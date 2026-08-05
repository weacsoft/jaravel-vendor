/*!
 * pjax.js — jaravel PJAX 无感页面切换运行时
 *
 * 设计目标：模板与控制器零改动。
 *  - 区域锚点由 jblade 在编译期依据 @extends/@section/@yield 关系自动生成，
 *    形如 <!--pjax:start:content--> ... <!--pjax:end:content-->
 *  - 本脚本启动时扫描注释节点建立区域索引，切换时只替换服务端判定为「已变化」的区域，
 *    未变化区域的 DOM 完全不被触碰，因而天然保住其滚动位置、输入状态与已绑定事件。
 *
 * 与服务端的协议：
 *  请求头  X-Pjax: true
 *          X-Pjax-Layout: <当前布局模板名>
 *          X-Pjax-Regions: name:hash,name:hash
 *  响应体  { pjax, reload, url, title, layout, template, regions:{name:html}, unchanged:[], hashes:{} }
 */
(function (window, document) {
    'use strict';

    if (window.Pjax && window.Pjax.__installed) {
        return;
    }

    var START_PREFIX = 'pjax:start:';
    var END_PREFIX = 'pjax:end:';
    var NAME_PATTERN = /^[A-Za-z0-9_.\-]+$/;

    /** 运行时状态 */
    var state = {
        template: '',
        layout: '',
        url: location.pathname + location.search,
        hashes: {}
    };

    /** 区域索引：name -> { start: Comment, end: Comment } */
    var regions = {};

    /** 当前进行中的请求控制器 */
    var pending = null;

    // ===== 初始化 =====

    function readConfig() {
        var el = document.getElementById('pjax-config');
        if (!el) {
            return false;
        }
        try {
            var cfg = JSON.parse(el.textContent || el.innerText || '{}');
            state.template = cfg.template || '';
            state.layout = cfg.layout || '';
            state.url = cfg.url || state.url;
            state.hashes = cfg.hashes || {};
            return true;
        } catch (e) {
            warn('配置解析失败', e);
            return false;
        }
    }

    /**
     * 扫描 DOM 中的 PJAX 注释锚点，建立区域索引。
     * 只认「起止注释处于同一父节点」的区域；否则该区域不可安全替换，
     * 遇到需要更新时会退化为整页跳转，宁可慢一点也不产出错误 DOM。
     */
    function scanRegions() {
        regions = {};
        var starts = {};
        var walker = document.createTreeWalker(document.documentElement, NodeFilter.SHOW_COMMENT, null, false);
        var node;
        while ((node = walker.nextNode())) {
            var text = node.nodeValue || '';
            if (text.indexOf(START_PREFIX) === 0) {
                starts[text.slice(START_PREFIX.length)] = node;
            } else if (text.indexOf(END_PREFIX) === 0) {
                var name = text.slice(END_PREFIX.length);
                var start = starts[name];
                if (start && start.parentNode === node.parentNode) {
                    regions[name] = { start: start, end: node };
                }
                delete starts[name];
            }
        }
        return regions;
    }

    // ===== 区域替换 =====

    /**
     * 用新的 HTML 片段替换区域内容。锚点注释本身保留，只换中间的节点。
     */
    function replaceRegion(name, html) {
        var region = regions[name];
        if (!region) {
            return false;
        }
        var parent = region.end.parentNode;
        if (!parent || region.start.parentNode !== parent) {
            return false;
        }

        var node = region.start.nextSibling;
        while (node && node !== region.end) {
            var next = node.nextSibling;
            parent.removeChild(node);
            node = next;
        }

        var fragment = buildFragment(html, parent);
        parent.insertBefore(fragment, region.end);
        return true;
    }

    /**
     * 依据父元素上下文构造 DOM 片段。
     * 使用 Range#createContextualFragment 以正确处理 <tr>/<td>/<option> 等
     * 只能出现在特定父元素内的标签——innerHTML 在这些场景下会直接丢弃内容。
     */
    function buildFragment(html, parent) {
        var range = document.createRange();
        try {
            range.selectNodeContents(parent);
            return range.createContextualFragment(html);
        } catch (e) {
            var tmp = document.createElement('div');
            tmp.innerHTML = html;
            var frag = document.createDocumentFragment();
            while (tmp.firstChild) {
                frag.appendChild(tmp.firstChild);
            }
            return frag;
        }
    }

    /**
     * 重新执行区域内的 <script>。
     * createContextualFragment 产出的 script 行为在各浏览器间不完全一致，
     * 这里统一改为「克隆成新 script 元素再插入」，确保只执行一次且顺序稳定。
     */
    function runScripts(name) {
        var region = regions[name];
        if (!region) {
            return;
        }
        var parent = region.end.parentNode;
        var scripts = [];
        var node = region.start.nextSibling;
        while (node && node !== region.end) {
            if (node.nodeType === 1) {
                if (node.tagName === 'SCRIPT') {
                    scripts.push(node);
                } else {
                    var nested = node.querySelectorAll ? node.querySelectorAll('script') : [];
                    for (var i = 0; i < nested.length; i++) {
                        scripts.push(nested[i]);
                    }
                }
            }
            node = node.nextSibling;
        }
        scripts.forEach(function (old) {
            if (old.dataset && old.dataset.pjaxNoExec === 'true') {
                return;
            }
            var fresh = document.createElement('script');
            for (var i = 0; i < old.attributes.length; i++) {
                var attr = old.attributes[i];
                fresh.setAttribute(attr.name, attr.value);
            }
            fresh.text = old.textContent;
            old.parentNode.replaceChild(fresh, old);
        });
        void parent;
    }

    // ===== 状态保持 =====

    /**
     * 记录当前焦点元素及其光标位置，便于替换后回填。
     * 只处理带 id 或 name 的表单控件——无标识的元素无法在新 DOM 中可靠定位。
     */
    function captureFocus() {
        var el = document.activeElement;
        if (!el || el === document.body) {
            return null;
        }
        var tag = el.tagName;
        if (tag !== 'INPUT' && tag !== 'TEXTAREA' && tag !== 'SELECT') {
            return null;
        }
        var key = el.id ? '#' + cssEscape(el.id)
            : (el.name ? '[name="' + el.name.replace(/"/g, '\\"') + '"]' : null);
        if (!key) {
            return null;
        }
        var snapshot = { selector: key };
        try {
            if (typeof el.selectionStart === 'number') {
                snapshot.start = el.selectionStart;
                snapshot.end = el.selectionEnd;
            }
        } catch (e) { /* number/email 类型不支持 selection，忽略 */ }
        return snapshot;
    }

    function restoreFocus(snapshot) {
        if (!snapshot) {
            return;
        }
        var el = document.querySelector(snapshot.selector);
        if (!el || typeof el.focus !== 'function') {
            return;
        }
        el.focus();
        if (typeof snapshot.start === 'number' && typeof el.setSelectionRange === 'function') {
            try {
                el.setSelectionRange(snapshot.start, snapshot.end);
            } catch (e) { /* 忽略不支持的控件 */ }
        }
    }

    function cssEscape(value) {
        if (window.CSS && typeof window.CSS.escape === 'function') {
            return window.CSS.escape(value);
        }
        return String(value).replace(/([^\w-])/g, '\\$1');
    }

    // ===== 请求与应用 =====

    function buildRegionsHeader() {
        var parts = [];
        for (var name in state.hashes) {
            if (!Object.prototype.hasOwnProperty.call(state.hashes, name)) {
                continue;
            }
            if (!NAME_PATTERN.test(name)) {
                continue;
            }
            parts.push(name + ':' + state.hashes[name]);
        }
        return parts.join(',');
    }

    /**
     * 发起一次 PJAX 切换。
     *
     * @param {string} url      目标地址
     * @param {object} options  { push: 是否写入历史, scroll: 目标滚动位置 }
     */
    function visit(url, options) {
        options = options || {};
        var push = options.push !== false;

        if (pending) {
            pending.abort();
        }
        var controller = new AbortController();
        pending = controller;

        emit('pjax:before', { url: url });
        document.documentElement.classList.add('pjax-loading');

        return fetch(url, {
            method: 'GET',
            credentials: 'same-origin',
            redirect: 'follow',
            signal: controller.signal,
            headers: {
                'X-Pjax': 'true',
                'X-Pjax-Layout': state.layout || '',
                'X-Pjax-Regions': buildRegionsHeader(),
                'X-Requested-With': 'XMLHttpRequest',
                'Accept': 'application/json, text/html;q=0.9'
            }
        }).then(function (response) {
            // 认证过期等场景服务端会 302 到登录页，此时直接整页跳转
            if (response.redirected && response.url && !isPjaxResponse(response)) {
                hardNavigate(response.url);
                return null;
            }
            if (!response.ok) {
                throw new Error('PJAX 请求失败: ' + response.status);
            }
            if (!isPjaxResponse(response)) {
                hardNavigate(url);
                return null;
            }
            return response.json();
        }).then(function (payload) {
            if (!payload) {
                return;
            }
            if (payload.reload) {
                hardNavigate(payload.url || url);
                return;
            }
            apply(payload, url, push, options.scroll);
            emit('pjax:success', { url: url, payload: payload });
        }).catch(function (error) {
            if (error && error.name === 'AbortError') {
                return;
            }
            warn('切换失败，回退整页跳转', error);
            emit('pjax:error', { url: url, error: error });
            hardNavigate(url);
        }).finally(function () {
            if (pending === controller) {
                pending = null;
            }
            document.documentElement.classList.remove('pjax-loading');
            emit('pjax:complete', { url: url });
        });
    }

    function isPjaxResponse(response) {
        var type = response.headers.get('Content-Type') || '';
        return type.indexOf('application/json') >= 0;
    }

    /**
     * 应用服务端返回的局部更新。
     */
    function apply(payload, requestUrl, push, scrollTo) {
        var focus = captureFocus();
        var changed = [];

        var names = Object.keys(payload.regions || {});
        for (var i = 0; i < names.length; i++) {
            var name = names[i];
            if (!regions[name]) {
                // 目标区域在当前 DOM 中不存在（骨架不一致），退回整页跳转
                hardNavigate(payload.url || requestUrl);
                return;
            }
            if (replaceRegion(name, payload.regions[name])) {
                changed.push(name);
            }
        }

        changed.forEach(runScripts);

        if (typeof payload.title === 'string' && payload.title.length) {
            document.title = decodeEntities(payload.title);
        }
        state.hashes = payload.hashes || state.hashes;
        state.template = payload.template || state.template;
        state.layout = payload.layout || state.layout;

        var finalUrl = payload.url || requestUrl;
        state.url = finalUrl;

        if (push) {
            // 先把离开页面时的滚动位置写回当前历史项，返回时才能精确还原
            try {
                history.replaceState({ pjax: true, url: history.state && history.state.url, scroll: window.scrollY },
                    '', location.href);
            } catch (e) { /* 忽略 */ }
            history.pushState({ pjax: true, url: finalUrl, scroll: 0 }, '', finalUrl);
        }

        window.scrollTo(0, typeof scrollTo === 'number' ? scrollTo : 0);
        restoreFocus(focus);

        // 重新索引：被替换的区域内部可能含有嵌套锚点
        scanRegions();

        emit('pjax:loaded', { url: finalUrl, changed: changed, unchanged: payload.unchanged || [] });
        notifyWire();
    }

    /**
     * 通知 Wire 运行时重新扫描 DOM（若页面同时启用了 wire.js）。
     */
    function notifyWire() {
        try {
            if (window.Wire && typeof window.Wire.rescan === 'function') {
                window.Wire.rescan();
            }
        } catch (e) { /* 与 Wire 的集成失败不应影响切换 */ }
    }

    function decodeEntities(text) {
        var el = document.createElement('textarea');
        el.innerHTML = text;
        return el.value;
    }

    function hardNavigate(url) {
        window.location.assign(url);
    }

    function emit(name, detail) {
        try {
            document.dispatchEvent(new CustomEvent(name, { detail: detail, bubbles: true }));
        } catch (e) { /* 事件派发失败忽略 */ }
    }

    function warn() {
        if (window.console && console.warn) {
            console.warn.apply(console, ['[pjax]'].concat(Array.prototype.slice.call(arguments)));
        }
    }

    // ===== 事件绑定 =====

    /**
     * 判断链接是否应由 PJAX 接管。
     */
    function shouldHandle(anchor, event) {
        if (!anchor || !anchor.href) {
            return false;
        }
        if (event.defaultPrevented || event.button !== 0) {
            return false;
        }
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return false;
        }
        if (anchor.hasAttribute('download') || anchor.hasAttribute('target')) {
            return false;
        }
        if (anchor.getAttribute('data-pjax') === 'false') {
            return false;
        }
        // Wire 的交互链接交给 wire.js 处理
        var attrs = anchor.attributes;
        for (var i = 0; i < attrs.length; i++) {
            if (attrs[i].name.indexOf('wire:') === 0) {
                return false;
            }
        }
        var url;
        try {
            url = new URL(anchor.href, location.href);
        } catch (e) {
            return false;
        }
        if (url.origin !== location.origin) {
            return false;
        }
        if (url.protocol !== 'http:' && url.protocol !== 'https:') {
            return false;
        }
        // 纯锚点跳转交给浏览器原生行为
        if (url.pathname === location.pathname && url.search === location.search && url.hash) {
            return false;
        }
        return true;
    }

    function onClick(event) {
        var anchor = event.target.closest ? event.target.closest('a') : null;
        if (!shouldHandle(anchor, event)) {
            return;
        }
        event.preventDefault();
        var url = new URL(anchor.href, location.href);
        visit(url.pathname + url.search + url.hash, { push: true });
    }

    function onPopState(event) {
        var st = event.state;
        if (!st || !st.pjax) {
            return;
        }
        var url = location.pathname + location.search;
        visit(url, { push: false, scroll: typeof st.scroll === 'number' ? st.scroll : 0 });
    }

    /**
     * 拦截 GET 表单提交，使搜索/筛选也走无感切换。
     */
    function onSubmit(event) {
        var form = event.target;
        if (!form || form.tagName !== 'FORM') {
            return;
        }
        if ((form.method || 'get').toLowerCase() !== 'get') {
            return;
        }
        if (form.getAttribute('data-pjax') === 'false' || form.hasAttribute('target')) {
            return;
        }
        var action = form.getAttribute('action') || location.pathname;
        var url;
        try {
            url = new URL(action, location.href);
        } catch (e) {
            return;
        }
        if (url.origin !== location.origin) {
            return;
        }
        event.preventDefault();
        var params = new URLSearchParams(new FormData(form));
        var query = params.toString();
        visit(url.pathname + (query ? '?' + query : ''), { push: true });
    }

    function install() {
        if (!readConfig()) {
            return;
        }
        scanRegions();

        if ('scrollRestoration' in history) {
            history.scrollRestoration = 'manual';
        }
        try {
            history.replaceState({ pjax: true, url: state.url, scroll: window.scrollY }, '', location.href);
        } catch (e) { /* 忽略 */ }

        document.addEventListener('click', onClick, false);
        document.addEventListener('submit', onSubmit, false);
        window.addEventListener('popstate', onPopState, false);

        emit('pjax:ready', { url: state.url, regions: Object.keys(regions) });
    }

    // ===== 对外 API =====

    window.Pjax = {
        __installed: true,
        /** 主动切换到指定地址 */
        visit: visit,
        /** 重新扫描区域锚点（在手工改动 DOM 结构后调用） */
        rescan: scanRegions,
        /** 只读运行时状态 */
        state: function () {
            return {
                template: state.template,
                layout: state.layout,
                url: state.url,
                hashes: Object.assign({}, state.hashes),
                regions: Object.keys(regions)
            };
        }
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', install);
    } else {
        install();
    }
})(window, document);
