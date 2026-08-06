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
 *  响应体  { pjax, reload, url, title, layout, template, regions:{name:html},
 *            unchanged:[], hashes:{}, components:[] }
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 区域脚本运行时（Region Script Runtime）
 * ─────────────────────────────────────────────────────────────────────────
 * 局部刷新与整页加载的最大差异在于「脚本的生命周期」。本运行时保证被替换区域内的
 * <script> 具备与整页加载一致的直觉语义：
 *
 *  1. 只执行一次。区域 HTML 用 <template> 解析（内容惰性、不执行脚本），
 *     再由 runScripts 统一受控执行。历史实现用 Range#createContextualFragment，
 *     该 API 会在插入时执行脚本，叠加 runScripts 后每次切换执行两遍。
 *  2. 「进入即触发」有效。区域脚本注册的 DOMContentLoaded / load / readystatechange
 *     回调会被拦截并在本次切换的 DOM 就绪后调用一次——否则这些事件早已触发完毕，
 *     回调永远不会执行。
 *  3. 不重复绑定。区域脚本在 document / window 上注册的监听器、创建的 setInterval，
 *     都以「注册它的 script 节点」为生命周期锚点；该节点随区域替换而脱离文档时自动回收。
 *  4. 不重复声明。内联脚本默认在函数作用域内执行（顶层 let/const/class 因此可以安全地
 *     重复执行），同时把列首的 function/var 声明回填到 window，兼容 onclick="fn()" 写法。
 *  5. 外部脚本不重复下载。同一 src 只加载一次；非 async 的动态脚本强制按序执行。
 *
 * 逐脚本可控的开关（写在 <script> 标签上）：
 *  data-pjax-no-exec="true"   该脚本永不由 PJAX 执行
 *  data-pjax-scope="global"   退回全局作用域执行（旧行为，需自行避免重复声明）
 *  data-pjax-run="once"       同一段脚本整个会话只执行一次
 *  data-pjax-reload="true"    外部脚本每次切换都重新加载
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

    /** install() 是否已完成 */
    var ready = false;

    // =========================================================================
    // 区域脚本运行时
    //
    // 必须在任何模板内联脚本之前完成安装，因此这段代码位于 IIFE 顶层同步执行，
    // 且服务端会把 pjax.js 注入到 </head> 之前。install() 仍延迟到 DOMContentLoaded。
    // =========================================================================

    var nativeDocumentAdd = document.addEventListener;
    var nativeDocumentRemove = document.removeEventListener;
    var nativeWindowAdd = window.addEventListener;
    var nativeWindowRemove = window.removeEventListener;
    var nativeSetInterval = window.setInterval;
    var nativeClearInterval = window.clearInterval;
    var nativeSetTimeout = window.setTimeout;

    /** 由脚本节点注册、需随该节点脱离文档一并回收的监听器 */
    var ownedListeners = [];

    /** 由脚本节点创建、需随该节点脱离文档一并清除的周期定时器 */
    var ownedTimers = [];

    /** 本次同步重放中被拦截下来的「文档就绪」回调 */
    var deferredReady = [];

    /** 已加载过的外部脚本（绝对 URL 集合） */
    var loadedSources = Object.create(null);

    /** data-pjax-run="once" 已执行过的脚本指纹 */
    var onceExecuted = Object.create(null);

    /** 是否处于 PJAX 脚本同步重放阶段 */
    var replaying = false;

    /** 需要拦截并改为「立即调用一次」的事件类型 */
    var READY_TYPES = { DOMContentLoaded: 1, load: 1, readystatechange: 1 };

    /** 可执行的 script type（空串表示未声明 type） */
    var EXECUTABLE_TYPES = {
        '': 1,
        'text/javascript': 1,
        'application/javascript': 1,
        'application/ecmascript': 1,
        'text/ecmascript': 1,
        'module': 1
    };

    /**
     * 取得当前正在同步执行的 script 节点。
     * 内联脚本与外部脚本执行期间该值均有效，是判定「谁注册了这个监听器」的可靠依据。
     */
    function ownerScript() {
        var cur = document.currentScript;
        return (cur && cur.tagName === 'SCRIPT') ? cur : null;
    }

    function isDetached(node) {
        if (!node) {
            return false;
        }
        if (typeof node.isConnected === 'boolean') {
            return !node.isConnected;
        }
        return !document.contains(node);
    }

    function safeCall(fn, arg) {
        try {
            fn(arg);
        } catch (e) {
            warn('回调执行出错', e);
        }
    }

    /**
     * 安排一个「文档就绪」回调：重放期间先收集、重放结束后统一触发；
     * 重放之外（例如外部脚本异步加载完成后才注册）则下一个宏任务立即触发。
     */
    function scheduleReady(listener) {
        if (replaying) {
            deferredReady.push(listener);
        } else {
            nativeSetTimeout.call(window, function () {
                safeCall(listener, null);
            }, 0);
        }
    }

    function flushDeferredReady() {
        if (!deferredReady.length) {
            return;
        }
        var queue = deferredReady;
        deferredReady = [];
        for (var i = 0; i < queue.length; i++) {
            safeCall(queue[i], null);
        }
    }

    /**
     * 构造 addEventListener 代理。行为与原生完全一致，只额外做两件事：
     *  - 区域脚本注册「文档就绪」类事件时，改为在本次切换后调用一次；
     *  - 记录注册来源脚本节点，供节点脱离文档时自动回收。
     */
    function makeAddProxy(target, nativeAdd) {
        return function (type, listener, options) {
            var node = ownerScript();
            if (READY_TYPES[type] && typeof listener === 'function') {
                var fromReplay = node ? !!node.__pjaxReplay : replaying;
                if (fromReplay && document.readyState !== 'loading') {
                    scheduleReady(listener);
                    return undefined;
                }
            }
            var result = nativeAdd.call(target, type, listener, options);
            if (node && typeof listener !== 'undefined' && listener !== null) {
                ownedListeners.push({
                    target: target,
                    type: type,
                    listener: listener,
                    options: options,
                    node: node
                });
            }
            return result;
        };
    }

    document.addEventListener = makeAddProxy(document, nativeDocumentAdd);
    window.addEventListener = makeAddProxy(window, nativeWindowAdd);

    window.setInterval = function (handler, timeout) {
        var id = nativeSetInterval.apply(window, arguments);
        var node = ownerScript();
        if (node) {
            ownedTimers.push({ id: id, node: node });
        }
        void handler;
        void timeout;
        return id;
    };

    /**
     * 回收「注册来源脚本节点已脱离文档」的监听器与定时器。
     * 以脚本节点为锚点而非区域名，可自然覆盖嵌套区域与首屏原生执行的脚本。
     */
    function sweepDetached() {
        var keptListeners = [];
        var removed = 0;
        for (var i = 0; i < ownedListeners.length; i++) {
            var rec = ownedListeners[i];
            if (isDetached(rec.node)) {
                try {
                    var nativeRemove = rec.target === window ? nativeWindowRemove : nativeDocumentRemove;
                    nativeRemove.call(rec.target, rec.type, rec.listener, rec.options);
                    removed++;
                } catch (e) { /* 移除失败不影响后续 */ }
            } else {
                keptListeners.push(rec);
            }
        }
        ownedListeners = keptListeners;

        var keptTimers = [];
        for (var j = 0; j < ownedTimers.length; j++) {
            var timer = ownedTimers[j];
            if (isDetached(timer.node)) {
                try {
                    nativeClearInterval.call(window, timer.id);
                    removed++;
                } catch (e) { /* 忽略 */ }
            } else {
                keptTimers.push(timer);
            }
        }
        ownedTimers = keptTimers;
        return removed;
    }

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

        emit('pjax:unload', { region: name });

        var node = region.start.nextSibling;
        while (node && node !== region.end) {
            var next = node.nextSibling;
            parent.removeChild(node);
            node = next;
        }

        var fragment = buildFragment(html);
        parent.insertBefore(fragment, region.end);
        return true;
    }

    /**
     * 把 HTML 字符串解析成可插入的 DOM 片段。
     *
     * 采用 <template>：其内容是惰性的——脚本不会执行、图片不会加载，
     * 同时解析器进入 "in template" 插入模式，<tr>/<td>/<option> 等
     * 受限标签也能正确保留（innerHTML 会直接丢弃它们）。
     *
     * 历史实现用 Range#createContextualFragment，该 API 在插入时就会执行脚本，
     * 与随后的 runScripts 叠加导致内联脚本每次切换执行两遍。
     */
    function buildFragment(html) {
        var tpl = document.createElement('template');
        tpl.innerHTML = html;
        if (tpl.content) {
            return document.importNode(tpl.content, true);
        }
        // 极老浏览器兜底（无 <template> 支持）
        var tmp = document.createElement('div');
        tmp.innerHTML = html;
        var frag = document.createDocumentFragment();
        while (tmp.firstChild) {
            frag.appendChild(tmp.firstChild);
        }
        return frag;
    }

    // ===== 脚本执行 =====

    function absoluteUrl(src) {
        try {
            return new URL(src, location.href).href;
        } catch (e) {
            return src;
        }
    }

    /** FNV-1a 32 位指纹，与服务端区域指纹算法一致 */
    function fingerprint(text) {
        var h = 0x811c9dc5;
        for (var i = 0; i < text.length; i++) {
            h ^= text.charCodeAt(i);
            h = (h * 0x01000193) >>> 0;
        }
        return h.toString(16);
    }

    var TOP_FUNCTION = /^function[ \t]+([A-Za-z_$][\w$]*)/gm;
    var TOP_VAR = /^var[ \t]+([A-Za-z_$][\w$]*)/gm;

    /**
     * 生成「顶层声明回填 window」的尾码。
     *
     * 内联脚本被包进函数作用域后，列首的 function / var 声明不再是全局的，
     * 而模板里 onclick="fn()" 这类内联事件属性只能看到全局标识符。这里按
     * 「行首（零缩进）」这一保守规则提取声明名并回填，既保住老写法可用，
     * 又不会把缩进的内部函数误提升为全局。typeof 保护确保任何误判都不会抛错。
     */
    function buildExports(code) {
        var names = {};
        var match;
        TOP_FUNCTION.lastIndex = 0;
        while ((match = TOP_FUNCTION.exec(code)) !== null) {
            names[match[1]] = 1;
        }
        TOP_VAR.lastIndex = 0;
        while ((match = TOP_VAR.exec(code)) !== null) {
            names[match[1]] = 1;
        }
        var parts = [];
        for (var name in names) {
            if (Object.prototype.hasOwnProperty.call(names, name)) {
                parts.push('try{if(typeof ' + name + '!=="undefined"){window.' + name + '=' + name + ';}}catch(e){}');
            }
        }
        return parts.join('');
    }

    /**
     * 把内联脚本包进函数作用域。
     * 这样顶层 let / const / class 在「回到同一页面」时不会因重复声明而整段报
     * SyntaxError——那是全局重放最典型的失效方式，且浏览器不会给出任何提示。
     */
    function wrapScoped(code) {
        if (!code) {
            return '';
        }
        return '(function(){\n' + code + '\n;' + buildExports(code) + '\n}).call(window);';
    }

    /** 收集区域内（含嵌套）的全部 script 节点，保持文档顺序 */
    function collectScripts(region) {
        var scripts = [];
        var node = region.start.nextSibling;
        while (node && node !== region.end) {
            if (node.nodeType === 1) {
                if (node.tagName === 'SCRIPT') {
                    scripts.push(node);
                } else if (node.querySelectorAll) {
                    var nested = node.querySelectorAll('script');
                    for (var i = 0; i < nested.length; i++) {
                        scripts.push(nested[i]);
                    }
                }
            }
            node = node.nextSibling;
        }
        return scripts;
    }

    /**
     * 中和一个「本次不执行」的脚本节点。
     * <p>从 {@code <template>} 解析出来的 script 插入文档时不会自动运行，所以这里
     * 不删除节点——保留它可以让 DOM 与服务端输出保持一致，排查时一眼能看出
     * 「这段脚本被跳过了、为什么跳过」。同时把 type 改成未知 MIME，
     * 确保即使被其它代码重新插入文档也永远不会执行。
     *
     * @param node   被跳过的 script 节点
     * @param reason 跳过原因（duplicate-source / once）
     */
    function neutralize(node, reason) {
        if (!node) {
            return;
        }
        try {
            node.setAttribute('type', 'application/pjax-skipped');
            node.setAttribute('data-pjax-skipped', reason);
        } catch (e) { /* 忽略 */ }
    }

    /**
     * 执行单个区域脚本：<template> 解析出来的 script 是惰性的，
     * 必须克隆成新元素插入文档才会运行。
     */
    function executeScript(old, regionName) {
        var dataset = old.dataset || {};
        if (dataset.pjaxNoExec === 'true') {
            return;
        }

        var type = (old.getAttribute('type') || '').toLowerCase();
        if (!EXECUTABLE_TYPES[type]) {
            // application/json 等数据块原样保留，绝不执行
            return;
        }

        var src = old.getAttribute('src');
        if (src) {
            var abs = absoluteUrl(src);
            if (loadedSources[abs] && dataset.pjaxReload !== 'true') {
                // 同一外部脚本重复加载会重置库的全局状态并浪费带宽
                neutralize(old, 'duplicate-source');
                return;
            }
            loadedSources[abs] = true;
        }

        var runKey = null;
        if (dataset.pjaxRun === 'once') {
            runKey = fingerprint((old.id || '') + '\u0000' + (src || old.textContent || ''));
            if (onceExecuted[runKey]) {
                neutralize(old, 'once');
                return;
            }
        }

        var fresh = document.createElement('script');
        for (var i = 0; i < old.attributes.length; i++) {
            var attr = old.attributes[i];
            fresh.setAttribute(attr.name, attr.value);
        }
        if (src && !old.hasAttribute('async')) {
            // 动态插入的脚本默认 async=true，会打乱依赖顺序
            fresh.async = false;
        }
        if (!src) {
            var code = old.textContent || '';
            // module 自带独立作用域，包装反而会破坏 import/export
            fresh.text = (type === 'module' || dataset.pjaxScope === 'global') ? code : wrapScoped(code);
        }
        fresh.__pjaxReplay = true;
        fresh.__pjaxRegion = regionName;

        var previous = replaying;
        replaying = true;
        try {
            old.parentNode.replaceChild(fresh, old);
        } finally {
            replaying = previous;
        }

        if (runKey) {
            onceExecuted[runKey] = true;
        }
    }

    function runScripts(name) {
        var region = regions[name];
        if (!region) {
            return;
        }
        var scripts = collectScripts(region);
        for (var i = 0; i < scripts.length; i++) {
            executeScript(scripts[i], name);
        }
    }

    /** 把首屏已存在的外部脚本登记为「已加载」，避免切换时重复拉取 */
    /**
     * 用首屏已有的脚本预热去重表。
     * <p>首屏脚本由浏览器原生执行，不经过 {@code executeScript}，若不在此登记，
     * 第一次区域替换会把它们当成「从未加载过」而重跑一遍——外部库被重复初始化、
     * {@code data-pjax-run="once"} 也会变成执行两次。两类都必须预热。
     */
    function seedLoadedSources() {
        var nodes = document.querySelectorAll('script[src]');
        for (var i = 0; i < nodes.length; i++) {
            var src = nodes[i].getAttribute('src');
            if (src) {
                loadedSources[absoluteUrl(src)] = true;
            }
        }
        var onceNodes = document.querySelectorAll('script[data-pjax-run="once"]');
        for (var j = 0; j < onceNodes.length; j++) {
            var node = onceNodes[j];
            var key = (node.id || '') + '\u0000' + (node.getAttribute('src') || node.textContent || '');
            onceExecuted[fingerprint(key)] = true;
        }
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
     *
     * 严格顺序：替换 DOM → 回收失效监听器/定时器 → 执行新脚本 →
     * 触发被拦截的「文档就绪」回调 → 挂载命名组件 → 更新标题与历史 → 广播 pjax:loaded。
     * 先回收后执行，保证新脚本注册的监听器不会被同一轮清扫误删。
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

        sweepDetached();
        changed.forEach(runScripts);
        flushDeferredReady();

        // 命名组件（toast / confirm 等）：PJAX 切换时不重载页面，outlet 容器与运行时仍在，
        // 只需把新组件挂载进去即可（后端已在信封顶层带上 components 字段）。
        if (payload.components && payload.components.length && window.WireComponent) {
            window.WireComponent.mountAll(payload.components);
        }

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

        var detail = { url: finalUrl, changed: changed, unchanged: payload.unchanged || [], initial: false };
        emit('pjax:loaded', detail);
        runLoadCallbacks(detail);
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

    // ===== 页面初始化回调 =====

    /**
     * 注册「每次进入页面都要跑一次」的初始化回调。
     *
     * 相比在模板里写 DOMContentLoaded，这里语义明确且天然幂等：
     * 同一个函数重复注册只保留一份，返回值可用于注销。
     */
    var loadCallbacks = [];

    function onLoad(fn) {
        if (typeof fn !== 'function') {
            return function () { /* noop */ };
        }
        if (loadCallbacks.indexOf(fn) < 0) {
            loadCallbacks.push(fn);
        }
        if (ready) {
            safeCall(fn, { url: state.url, changed: [], unchanged: [], initial: true });
        }
        return function off() {
            var idx = loadCallbacks.indexOf(fn);
            if (idx >= 0) {
                loadCallbacks.splice(idx, 1);
            }
        };
    }

    function runLoadCallbacks(detail) {
        for (var i = 0; i < loadCallbacks.length; i++) {
            safeCall(loadCallbacks[i], detail);
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
        seedLoadedSources();

        if ('scrollRestoration' in history) {
            history.scrollRestoration = 'manual';
        }
        try {
            history.replaceState({ pjax: true, url: state.url, scroll: window.scrollY }, '', location.href);
        } catch (e) { /* 忽略 */ }

        // 全部为事件委托，只在此处绑定一次；区域替换不会造成重复绑定
        nativeDocumentAdd.call(document, 'click', onClick, false);
        nativeDocumentAdd.call(document, 'submit', onSubmit, false);
        nativeWindowAdd.call(window, 'popstate', onPopState, false);

        ready = true;
        emit('pjax:ready', { url: state.url, regions: Object.keys(regions) });
        runLoadCallbacks({ url: state.url, changed: [], unchanged: [], initial: true });
    }

    // ===== 对外 API =====

    window.Pjax = {
        __installed: true,
        /** 主动切换到指定地址 */
        visit: visit,
        /** 重新加载当前地址（不写入历史） */
        reload: function () {
            return visit(state.url, { push: false });
        },
        /** 重新扫描区域锚点（在手工改动 DOM 结构后调用） */
        rescan: scanRegions,
        /** 注册每次进入页面都会执行的初始化回调，返回注销函数 */
        onLoad: onLoad,
        /** 立即回收已脱离文档的脚本所注册的监听器与定时器 */
        sweep: sweepDetached,
        /** 只读运行时状态 */
        state: function () {
            return {
                template: state.template,
                layout: state.layout,
                url: state.url,
                hashes: Object.assign({}, state.hashes),
                regions: Object.keys(regions)
            };
        },
        /** 脚本运行时统计，便于自检与自动化测试 */
        stats: function () {
            return {
                listeners: ownedListeners.length,
                timers: ownedTimers.length,
                sources: Object.keys(loadedSources).length,
                once: Object.keys(onceExecuted).length
            };
        }
    };

    if (document.readyState === 'loading') {
        nativeDocumentAdd.call(document, 'DOMContentLoaded', install);
    } else {
        install();
    }
})(window, document);
