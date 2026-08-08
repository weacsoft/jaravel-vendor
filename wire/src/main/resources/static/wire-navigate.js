/**
 * Wire Navigate — 前端无感页面切换运行时。
 *
 * 实现模式：链接拦截 → AJAX → section diff → DOM 替换 → 历史管理，
 * 使用 wire:section 标记定位区域。
 *
 * 功能：
 * - 拦截带有 wire-navigate 属性或 data-wire 属性的 <a> 链接
 * - 发送 GET 请求（X-Wire-Navigate + X-Wire-Hashes 头）
 * - 接收 JSON diff（只含变化的 section）
 * - 按 <!--wire:section-start:NAME--> 标记定位并替换对应 DOM
 * - 注释非法位置（<title> 文本、class 等属性值）改由 wire:section-text /
 *   wire:section-attr 标记属性定位，服务端下发完整新值后直接赋值
 * - 替换完成后重扫运行时（Wire.scan / WireComponent.scan），保证新 DOM 的事件与组件生效
 * - pushState / popState 历史管理
 * - 派发 wire:navigate:* 生命周期事件（before/success/complete/rescan）
 *
 * 用法：
 *   <a href="/records" wire-navigate>记录列表</a>
 *   或
 *   <a href="/records" data-wire>记录列表</a>
 */
(function () {
    'use strict';

    // ===== 幂等守卫 =====
    // 重复加载会注册出第二套 document click 拦截器与 popstate 监听，
    // 一次点击将并发两次导航请求。已加载过就直接返回。
    if (window.WireNavigate && window.WireNavigate.__runtime === 'wire-navigate.js') {
        return;
    }

    // ===== 状态 =====
    var currentUrl = location.href;
    var currentHashes = {};  // section名 → hash值
    var firstLoad = true;

    // ===== 事件系统 =====
    var listeners = {};

    function on(event, fn) {
        if (!listeners[event]) listeners[event] = [];
        listeners[event].push(fn);
    }

    function off(event, fn) {
        if (!listeners[event]) return;
        listeners[event] = listeners[event].filter(function (f) { return f !== fn; });
    }

    function emit(event, detail) {
        document.dispatchEvent(new CustomEvent('wire:navigate:' + event, { detail: detail }));
        if (listeners[event]) {
            listeners[event].forEach(function (fn) { try { fn(detail); } catch (e) { console.error(e); } });
        }
    }

    // ===== 公开 API =====
    window.WireNavigate = {
        /** 运行时指纹，供幂等守卫识别 */
        __runtime: 'wire-navigate.js',
        on: on,
        off: off,
        visit: visit,
        rescan: function () { refreshRuntimes(); },
        currentUrl: function () { return currentUrl; }
    };

    // ===== 初始化 =====
    function init() {
        // 计算初始 section hash
        computeHashes();
        firstLoad = false;
        emit('ready', { url: currentUrl, hashes: currentHashes });
    }

    /** 扫描 DOM 重新计算所有 section hash（首屏优先使用服务端注入的 __wireHashes，保证与 diff 同口径） */
    function computeHashes() {
        if (window.__wireHashes) {
            currentHashes = Object.assign({}, window.__wireHashes);
            return;
        }
        currentHashes = {};
        walkSections(function (name, el) {
            currentHashes[name] = hash(getSectionContent(el) || '');
        });
    }

    // ===== FNV-1a 32-bit hash =====
    function hash(str) {
        var h = 0x811c9dc5;
        for (var i = 0; i < str.length; i++) {
            h ^= str.charCodeAt(i);
            h = (h * 0x01000193) | 0;
        }
        return (h >>> 0).toString(16).padStart(8, '0');
    }

    // ===== Section 遍历（使用 TreeWalker 扫描注释标记） =====
    function walkSections(fn) {
        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_COMMENT, null, false);
        var openMarkers = {};
        var node;
        while ((node = walker.nextNode())) {
            var text = node.textContent || '';
            var startMatch = /^wire:section-start:([a-zA-Z0-9_-]+)$/.exec(text);
            if (startMatch) {
                var name = startMatch[1];
                openMarkers[name] = node;
                continue;
            }
            var endMatch = /^wire:section-end:([a-zA-Z0-9_-]+)$/.exec(text);
            if (endMatch) {
                var endName = endMatch[1];
                var startNode = openMarkers[endName];
                if (startNode) {
                    // 收集起止注释之间的 DOM 作为 section 内容
                    fn(endName, { startNode: startNode, endNode: node, name: endName });
                    delete openMarkers[endName];
                }
            }
        }
    }

    /** 获取 section 起止注释之间的 HTML */
    function getSectionContent(section) {
        var parts = [];
        var node = section.startNode.nextSibling;
        while (node && node !== section.endNode) {
            if (node.nodeType === Node.ELEMENT_NODE) {
                parts.push(node.outerHTML);
            } else if (node.nodeType === Node.TEXT_NODE) {
                parts.push(node.textContent);
            }
            node = node.nextSibling;
        }
        return parts.join('');
    }

    /** 替换 section 起止注释之间的 DOM */
    function replaceSectionContent(section, newHtml) {
        // 移除旧 DOM
        var node = section.startNode.nextSibling;
        while (node && node !== section.endNode) {
            var next = node.nextSibling;
            node.parentNode.removeChild(node);
            node = next;
        }

        // 插入新 DOM
        var temp = document.createElement('div');
        temp.innerHTML = newHtml;
        var frag = document.createDocumentFragment();
        while (temp.firstChild) {
            frag.appendChild(temp.firstChild);
        }
        section.endNode.parentNode.insertBefore(frag, section.endNode);
    }

    /**
     * 让 section 中新插入的 <script> 生效。
     * <p>
     * innerHTML 插入的 <script> 按 HTML 规范不会执行，导致新页面的初始化脚本失效。
     * 这里克隆为新的 script 节点重新插入以触发执行。
     * 纯标记节点（wire:config / wire:components 等无可执行内容的配置载体）跳过，
     * 它们只需要留在 DOM 中被运行时读取属性。
     */
    function activateScripts(root) {
        if (!root) return;
        var scripts = root.querySelectorAll ? root.querySelectorAll('script') : [];
        for (var i = 0; i < scripts.length; i++) {
            var old = scripts[i];
            var type = (old.getAttribute('type') || '').toLowerCase();
            var isDataOnly = old.hasAttribute('wire:config') || old.hasAttribute('wire:snapshot')
                || old.hasAttribute('wire:components') || type === 'application/json';
            if (isDataOnly) continue;
            var fresh = document.createElement('script');
            for (var a = 0; a < old.attributes.length; a++) {
                fresh.setAttribute(old.attributes[a].name, old.attributes[a].value);
            }
            fresh.text = old.textContent || '';
            old.parentNode.replaceChild(fresh, old);
        }
    }

    /**
     * 应用锚点值：更新「HTML 注释非法位置」的内容。
     * <p>
     * {@code <title>} 内部、{@code class} 等属性值里放不了注释，服务端 WireAnchorRewriter
     * 已把这些位置改写为标记属性（wire:section-text / wire:section-attr），
     * 并在 diff 中下发渲染后的完整新值，这里按标记定位后直接赋值即可。
     *
     * anchors 形如：{"text:title":"组件演示", "attr:class:bodyClass":"page-components"}
     */
    function applyAnchors(anchors) {
        if (!anchors) return;
        Object.keys(anchors).forEach(function (key) {
            var value = anchors[key];
            try {
                if (key.indexOf('text:') === 0) {
                    var section = key.slice(5);
                    var el = document.querySelector('[wire\\:section-text~="' + section + '"]');
                    if (!el) return;
                    if (el.tagName === 'TITLE') {
                        document.title = value;
                    } else {
                        el.textContent = value;
                    }
                } else if (key.indexOf('attr:') === 0) {
                    var token = key.slice(5);              // 形如 class:bodyClass
                    var colon = token.indexOf(':');
                    if (colon <= 0) return;
                    var attrName = token.slice(0, colon);
                    var target = document.querySelector('[wire\\:section-attr~="' + token + '"]');
                    if (target) target.setAttribute(attrName, value);
                }
            } catch (e) {
                console.error('[wire-navigate] 锚点应用失败: ' + key, e);
            }
        });
    }

    // ===== 导航 =====
    function visit(url) {
        if (!url) return;
        emit('before', { url: url });

        var xhr = new XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.setRequestHeader('X-Wire-Navigate', 'true');

        // 构建 hash 请求头
        var hashParts = [];
        for (var k in currentHashes) {
            if (currentHashes.hasOwnProperty(k)) {
                hashParts.push(k + '=' + currentHashes[k]);
            }
        }
        xhr.setRequestHeader('X-Wire-Hashes', hashParts.join(','));
        xhr.setRequestHeader('Accept', 'application/json, text/html');

        xhr.onload = function () {
            if (xhr.status >= 200 && xhr.status < 300) {
                var contentType = xhr.getResponseHeader('Content-Type') || '';
                if (contentType.indexOf('application/json') >= 0) {
                    // Wire diff response
                    try {
                        var payload = JSON.parse(xhr.responseText);
                        applyDiff(payload, url);
                    } catch (e) {
                        console.error('[wire-navigate] JSON parse error:', e);
                        hardNavigate(url);
                    }
                } else {
                    // Full HTML (shouldn't happen for Wire nav, but fallback)
                    hardNavigate(url);
                }
            } else if (xhr.status === 302 || xhr.status === 301) {
                var loc = xhr.getResponseHeader('Location');
                if (loc) visit(loc);
            } else {
                hardNavigate(url);
            }
        };

        xhr.onerror = function () {
            hardNavigate(url);
        };

        xhr.send();
    }

    /** 应用 section diff 到 DOM */
    function applyDiff(payload, url) {
        var sections = payload.sections || {};
        var hashes = payload.hashes || {};
        var changedCount = 0;

        // 替换每个变化的 section
        var replaced = [];
        walkSections(function (name, section) {
            var newHtml = sections[name];
            if (newHtml !== undefined) {
                replaceSectionContent(section, newHtml);
                replaced.push(section);
                changedCount++;
            }
            // 更新 hash
            if (hashes[name] !== undefined) {
                currentHashes[name] = hashes[name];
            }
        });

        // 让新插入的脚本生效（innerHTML 插入的 <script> 默认不执行）
        for (var r = 0; r < replaced.length; r++) {
            activateScripts(replaced[r].endNode.parentNode);
        }

        // 更新「注释非法位置」的锚点（<title> 文本、class 等属性）
        applyAnchors(payload.anchors);

        // 更新 title（兼容未启用锚点的旧版服务端）
        if (payload.title) {
            document.title = payload.title;
        }

        // 关键：DOM 已被整体替换，必须让运行时重扫。
        // 否则新页面的 wire:config 不会被识别、新按钮没有事件绑定 —— 表现为
        // 「先访问其它页面再切过来，点按钮毫无反应」。
        refreshRuntimes();

        // 更新 URL
        var finalUrl = payload.url || url;
        if (finalUrl && finalUrl !== currentUrl) {
            history.pushState({ wireUrl: finalUrl }, '', finalUrl);
            currentUrl = finalUrl;
        }

        emit('success', { url: finalUrl, payload: payload, changedCount: changedCount });
        emit('complete', { url: finalUrl });
    }

    /**
     * 通知同页运行时「DOM 换过了，请重扫」。
     * <p>
     * 三者互相解耦：wire-navigate 不关心 wire.js / wire-component.js 是否被引入，
     * 存在就调用，不存在就跳过；同时派发 DOM 事件，宿主应用可自行监听做二次初始化。
     */
    function refreshRuntimes() {
        try {
            if (window.Wire && typeof window.Wire.scan === 'function') {
                window.Wire.scan();
            }
        } catch (e) {
            console.error('[wire-navigate] Wire.scan() 失败', e);
        }
        try {
            if (window.WireComponent && typeof window.WireComponent.scan === 'function') {
                window.WireComponent.scan();
            }
        } catch (e) {
            console.error('[wire-navigate] WireComponent.scan() 失败', e);
        }
        emit('rescan', { url: currentUrl });
    }

    function hardNavigate(url) {
        window.location.assign(url);
    }

    // ===== 链接拦截 =====
    document.addEventListener('click', function (e) {
        // 查找最近的 wire-navigate 链接
        var el = e.target.closest('a');
        if (!el || !el.href) return;

        // 检查是否为 Wire 导航链接
        var isWireLink = el.hasAttribute('wire-navigate') || el.hasAttribute('data-wire');
        if (!isWireLink) return;

        // 排除条件
        if (el.host !== location.host) return;                    // 外部链接
        if (!/^https?:/.test(el.protocol)) return;                // 非 http/https
        if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return; // 修饰键
        if (el.hasAttribute('target')) return;                    // target 属性
        if (el.hasAttribute('download')) return;                  // 下载链接
        if (el.getAttribute('href') === '#') return;              // 纯锚点

        e.preventDefault();
        visit(el.href);
    });

    // ===== 浏览器后退/前进 =====
    window.addEventListener('popstate', function (e) {
        var url = (e.state && e.state.wireUrl) || location.href;
        if (url !== currentUrl) {
            visit(url);
        }
    });

    // ===== 启动 =====
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
