/**
 * Wire Navigate — 前端无感页面切换运行时。
 *
 * 仿 PJAX 的实现模式（链接拦截 → AJAX → section diff → DOM 替换 → 历史管理），
 * 但完全独立：使用 wire:section 标记定位区域，与 PJAX 无任何耦合。
 *
 * 功能：
 * - 拦截带有 wire-navigate 属性或 data-wire 属性的 <a> 链接
 * - 发送 GET 请求（X-Wire-Navigate + X-Wire-Hashes 头）
 * - 接收 JSON diff（只含变化的 section）
 * - 按 <!--wire:section-start:NAME--> 标记定位并替换对应 DOM
 * - pushState / popState 历史管理
 * - 派发 wire:navigate:* 生命周期事件
 *
 * 用法：
 *   <a href="/records" wire-navigate>记录列表</a>
 *   或
 *   <a href="/records" data-wire>记录列表</a>
 */
(function () {
    'use strict';

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
    window.WireNavigate = { on: on, off: off, visit: visit, currentUrl: function () { return currentUrl; } };

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
        walkSections(function (name, section) {
            var newHtml = sections[name];
            if (newHtml !== undefined) {
                replaceSectionContent(section, newHtml);
                changedCount++;
            }
            // 更新 hash
            if (hashes[name] !== undefined) {
                currentHashes[name] = hashes[name];
            }
        });

        // 更新 title
        if (payload.title) {
            document.title = payload.title;
        }

        // 更新 URL
        var finalUrl = payload.url || url;
        if (finalUrl && finalUrl !== currentUrl) {
            history.pushState({ wireUrl: finalUrl }, '', finalUrl);
            currentUrl = finalUrl;
        }

        emit('success', { url: finalUrl, payload: payload, changedCount: changedCount });
        emit('complete', { url: finalUrl });
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
