/**
 * Wire.js — 单一运行时：Laravel Livewire 风格部分更新 + 命名组件 + 透明导航。
 * <p>
 * 本文件合并了三个历史文件：wire.js、wire-component.js、wire-navigate.js。
 * 三者同域同页共存，单一 IIFE 挂载：
 *   window.Wire            — Livewire 局部更新
 *   window.WireComponent   — 命名组件（toast / confirm 等）
 *   window.WireNavigate    — 透明导航（链接拦截 → 局部刷新 → pushState）
 * <p>
 * 幂等守卫：已加载过任一命名空间时立即返回，避免重复注册监听器。
 *
 * 主要功能：
 * - wire:click / wire:submit / wire:model / wire:change / wire:keydown 自动绑定
 * - 组件级 section 局部更新（wire:section / wire:target / wire:lazy / wire:loading）
 * - wire:config 声明式懒加载；wire:update 局部覆盖；wire:pagination 分页拦截
 * - Wire.on/off 事件：beforeRequest / afterRequest / beforeUpdate / afterUpdate
 * - 命名组件四生命周期（onCreate/onStart/onStop/onDestroy），wire.stop() 结束实例
 * - 透明导航（X-Wire-Navigate + X-Wire-Hashes），最小 diff，只换变化的 section
 * - 首屏脚本回放（DOMContentLoaded 类回调在 section 替换后重放）
 * - 零外部依赖，自包含
 */
(function () {
    'use strict';

    // ===== 幂等守卫 =====
    if (window.Wire && window.Wire.__runtime === 'wire-merged') return;
    if (window.WireComponent && window.WireComponent.__runtime === 'wire-merged') return;
    if (window.WireNavigate && window.WireNavigate.__runtime === 'wire-merged') return;

    // ======================== Wire（Livewire 核心） ========================

    var Wire = {
        __runtime: 'wire-merged',
        components: [],
        debounceTimers: {},
        _listeners: {}
    };
    var lazyFired = new WeakSet();

    Wire.on = function (event, callback) {
        if (typeof callback !== 'function') return Wire;
        (Wire._listeners[event] = Wire._listeners[event] || []).push(callback);
        return Wire;
    };
    Wire.off = function (event, callback) {
        if (!Wire._listeners[event]) return Wire;
        Wire._listeners[event] = callback
            ? Wire._listeners[event].filter(function (f) { return f !== callback; })
            : [];
        return Wire;
    };
    function wireEmit(event) {
        var arr = Wire._listeners[event];
        if (!arr) return;
        var args = Array.prototype.slice.call(arguments, 1);
        for (var i = 0; i < arr.length; i++) {
            try { arr[i].apply(null, args); } catch (e) { console.error('[Wire] 事件监听器异常 (' + event + '):', e); }
        }
    }

    function collectConfigElements() {
        var configs = [];
        try {
            var bySel = document.querySelectorAll('[wire\\:config]');
            for (var s = 0; s < bySel.length; s++) configs.push(bySel[s]);
        } catch (e) { /* */ }
        if (configs.length === 0) {
            var scripts = document.querySelectorAll('script');
            for (var i = 0; i < scripts.length; i++) {
                var sc = scripts[i];
                if (sc.hasAttribute('wire:config') || sc.hasAttribute('wire:snapshot')) configs.push(sc);
            }
        }
        return configs;
    }

    function pruneComponents() {
        var alive = [];
        for (var i = 0; i < Wire.components.length; i++) {
            var c = Wire.components[i];
            if (!c.configElement || document.contains(c.configElement)) alive.push(c);
        }
        Wire.components = alive;
    }

    Wire.scan = function () {
        pruneComponents();
        var configs = collectConfigElements();
        for (var i = 0; i < configs.length; i++) {
            var cfg = configs[i];
            var known = false;
            for (var k = 0; k < Wire.components.length; k++) {
                if (Wire.components[k].configElement === cfg) { known = true; break; }
            }
            if (!known) initComponent(cfg);
        }
        for (var j = 0; j < Wire.components.length; j++) bindEvents(Wire.components[j]);
        initLazy();
        return Wire;
    };

    function initLazy() {
        var all = document.querySelectorAll('[wire\\:lazy]');
        var lazyEls = [];
        for (var q = 0; q < all.length; q++) {
            if (!lazyFired.has(all[q])) { lazyFired.add(all[q]); lazyEls.push(all[q]); }
        }
        if (!lazyEls.length) return;
        var fire = function () {
            for (var i = 0; i < lazyEls.length; i++) {
                var name = lazyEls[i].getAttribute('wire:section');
                if (name) Wire.refresh([name]);
            }
        };
        if (document.readyState === 'complete') fire();
        else window.addEventListener('load', fire);
    }

    function cleanHeadWireMarkers() {
        var rawEls = document.querySelectorAll('title, style, script');
        for (var i = 0; i < rawEls.length; i++) {
            var el = rawEls[i];
            var content = el.textContent || '';
            var regex = /<!--wire:section-start:([\s\S]+?)-->([\s\S]*?)<!--wire:section-end:\1-->/g;
            var match, cleaned = content;
            while ((match = regex.exec(content))) { cleaned = cleaned.replace(match[0], match[2]); }
            if (cleaned !== content) {
                if (el.tagName === 'TITLE') document.title = cleaned;
                else el.textContent = cleaned;
            }
        }
        var headEls = document.head ? document.head.querySelectorAll('*') : [];
        for (var j = 0; j < headEls.length; j++) {
            var elem = headEls[j];
            for (var a = 0; a < elem.attributes.length; a++) {
                var attr = elem.attributes[a];
                var val = attr.value;
                var areg = /<!--wire:section-start:([\s\S]+?)-->([\s\S]*?)<!--wire:section-end:\1-->/g;
                var amatch, clean = val;
                while ((amatch = areg.exec(val))) { clean = clean.replace(amatch[0], amatch[2]); }
                if (clean !== val) elem.setAttribute(attr.name, clean);
            }
        }
    }

    function closestAttr(el, name) {
        var node = el;
        while (node && node !== document) {
            if (node.getAttribute && node.getAttribute(name) !== null) return node;
            node = node.parentNode;
        }
        return null;
    }
    function getCookie(name) {
        var re = new RegExp('(?:^|; )' + name.replace(/([.$?*|{}()\[\]\\/+^])/g, '\\$1') + '=([^;]*)');
        var m = document.cookie.match(re);
        return m ? m[1] : null;
    }
    function highlightNav(el) {
        try {
            var group = closestAttr(el, 'wire:nav-menu') || el.parentNode;
            if (!group) return;
            var items = group.querySelectorAll('[wire\\:nav]');
            for (var i = 0; i < items.length; i++) items[i].classList.remove('wire-nav-active');
            el.classList.add('wire-nav-active');
        } catch (e) { /* */ }
    }

    function initComponent(configEl) {
        var component = {
            element: document.body,
            configElement: configEl,
            updateUrl: configEl.getAttribute('data-wire-update') || '/wire/update',
            snapshot: configEl.getAttribute('wire:snapshot') || '',
            id: 'wire-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9),
            boundElements: new Set()
        };
        Wire.components.push(component);
        bindEvents(component);
    }

    function bindEvents(comp) {
        bindClick(comp); bindSubmit(comp); bindModel(comp);
        bindChange(comp); bindKeydown(comp); bindPagination(comp);
    }
    function markBound(comp, el) {
        if (comp.boundElements.has(el)) return false;
        comp.boundElements.add(el);
        return true;
    }

    function bindClick(comp) {
        var els = comp.element.querySelectorAll('[wire\\:click], [wire\\:nav]');
        for (var i = 0; i < els.length; i++) (function (el) {
            if (!markBound(comp, el)) return;
            el.addEventListener('click', function (e) {
                var navKey = el.getAttribute('wire:nav');
                if (navKey) {
                    e.preventDefault();
                    var target = closestAttr(el, 'wire:nav-content');
                    var targetName = target ? (target.getAttribute('wire:section') || 'content') : 'content';
                    sendRequest(comp, '$nav', { page: navKey }, el, [targetName]);
                    highlightNav(el);
                    return;
                }
                e.preventDefault();
                sendRequest(comp, el.getAttribute('wire:click'), collectParams(el), el);
            });
        })(els[i]);
    }
    function bindPagination(comp) {
        var containers = comp.element.querySelectorAll('[wire\\:pagination]');
        for (var c = 0; c < containers.length; c++) (function (container) {
            var links = container.querySelectorAll('a[href]');
            for (var i = 0; i < links.length; i++) (function (el) {
                if (!markBound(comp, el)) return;
                el.addEventListener('click', function (e) {
                    var href = el.getAttribute('href') || '';
                    var m = href.match(/[?&]page=(\d+)/);
                    if (!m) return;
                    e.preventDefault();
                    var target = container.getAttribute('wire:target') || '';
                    var pparams = { pageNum: parseInt(m[1], 10) };
                    var per = href.match(/[?&]perPage=(\d+)/);
                    if (per) pparams.perPage = parseInt(per[1], 10);
                    sendRequest(comp, '$paginate', pparams, el, target ? [target] : null);
                });
            })(links[i]);
        })(containers[c]);
    }
    function bindSubmit(comp) {
        var forms = comp.element.querySelectorAll('form[wire\\:submit]');
        for (var i = 0; i < forms.length; i++) (function (form) {
            if (!markBound(comp, form)) return;
            form.addEventListener('submit', function (e) {
                e.preventDefault();
                sendRequest(comp, form.getAttribute('wire:submit'), collectFormData(form), form);
            });
        })(forms[i]);
    }
    function bindModel(comp) {
        var all = comp.element.querySelectorAll('input, textarea, select');
        for (var i = 0; i < all.length; i++) (function (input) {
            var modelAttr = null, modelValue = null;
            for (var j = 0; j < input.attributes.length; j++) {
                var attr = input.attributes[j];
                if (attr.name === 'wire:model' || attr.name.indexOf('wire:model.') === 0) {
                    modelAttr = attr.name; modelValue = attr.value; break;
                }
            }
            if (!modelAttr || !markBound(comp, input)) return;
            var field = modelValue;
            var isLazy = modelAttr.indexOf('.lazy') !== -1;
            var isLive = modelAttr.indexOf('.live') !== -1;
            input.setAttribute('data-wire-field', field);
            input.setAttribute('data-wire-model-attr', modelAttr);
            if (closestAttr(input, 'data-wire-key') || closestAttr(input, 'wire:key')) {
                input.setAttribute('data-wire-row-scoped', '1'); return;
            }
            var sync = function () {
                var params = {}; params[field] = getInputValue(input);
                sendRequest(comp, '$sync', params, input);
            };
            if (isLazy) input.addEventListener('change', sync);
            else if (isLive) input.addEventListener('input', sync);
            else input.addEventListener('input', function () {
                var key = comp.id + '-' + field;
                clearTimeout(Wire.debounceTimers[key]);
                Wire.debounceTimers[key] = setTimeout(sync, 150);
            });
        })(all[i]);
    }
    function bindChange(comp) {
        var els = comp.element.querySelectorAll('[wire\\:change]');
        for (var i = 0; i < els.length; i++) (function (el) {
            if (!markBound(comp, el)) return;
            el.addEventListener('change', function () {
                sendRequest(comp, el.getAttribute('wire:change'), collectParams(el), el);
            });
        })(els[i]);
    }
    function bindKeydown(comp) {
        var els = comp.element.querySelectorAll('[wire\\:keydown]');
        var keyMap = { 'enter':'Enter','escape':'Escape','tab':'Tab','space':' ','arrowup':'ArrowUp','arrowdown':'ArrowDown' };
        for (var i = 0; i < els.length; i++) (function (el) {
            var attr = el.getAttribute('wire:keydown');
            el.addEventListener('keydown', function (e) {
                var parts = attr.split('.');
                if (parts.length === 1) { e.preventDefault(); sendRequest(comp, parts[0], collectParams(el), el); }
                else if (e.key === (keyMap[parts[1].toLowerCase()] || parts[1].toLowerCase())) {
                    e.preventDefault(); sendRequest(comp, parts[0], collectParams(el), el);
                }
            });
        })(els[i]);
    }

    var pendingRequests = {};
    function sendRequest(comp, action, params, triggerEl, targetSections) {
        var isSync = action === '$sync';
        var updateUrl = comp.updateUrl;
        var el = triggerEl;
        while (el && el !== document) { if (el.hasAttribute && el.hasAttribute('wire:update')) { updateUrl = el.getAttribute('wire:update'); break; } el = el.parentElement; }
        var sections;
        if (targetSections && targetSections.length) sections = targetSections;
        else { sections = getTargetSections(comp, triggerEl); if (!sections.length) sections = getAllSections(comp); }
        var inputState = null;
        if (isSync && triggerEl && triggerEl.tagName) {
            inputState = {
                field: triggerEl.getAttribute('data-wire-field'),
                modelAttr: triggerEl.getAttribute('data-wire-model-attr'),
                value: triggerEl.value,
                selectionStart: triggerEl.selectionStart,
                selectionEnd: triggerEl.selectionEnd,
                isFocused: document.activeElement === triggerEl
            };
        }
        showLoading(comp, action);
        wireEmit('beforeRequest', comp, action, params);
        wireEmit('beforeUpdate', comp, action, params);
        var body = 'wire_body=' + encodeURIComponent(JSON.stringify({
            snapshot: comp.snapshot, action: action, params: params || {}, sections: sections
        }));
        var headers = { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Wire-Request': 'true' };
        var csrf = getCookie('XSRF-TOKEN');
        if (csrf) headers['X-XSRF-TOKEN'] = decodeURIComponent(csrf);
        fetch(updateUrl, {
            method: 'POST', headers: headers, body: body,
            credentials: 'same-origin', redirect: 'manual'
        }).then(function (resp) {
            if (resp.status === 401) return resp.json().then(function (d) { redirectToLogin((d && d.redirect) || '/login'); throw new Error('AUTH_EXPIRED'); })
                .catch(function (e) { if (e.message !== 'AUTH_EXPIRED') { redirectToLogin('/login'); } throw new Error('AUTH_EXPIRED'); });
            if (resp.status === 0 || resp.type === 'opaqueredirect') { redirectToLogin('/login'); throw new Error('AUTH_EXPIRED'); }
            if (resp.status === 419) { window.location.reload(); throw new Error('CSRF_EXPIRED'); }
            if (!resp.ok) throw new Error('Wire 请求失败: ' + resp.status);
            return resp.json();
        }).then(function (data) {
            wireEmit('afterRequest', comp, data);
            handleResponse(comp, data);
            hideLoading(comp, action);
            if (inputState && inputState.field && inputState.modelAttr) {
                var newEl = findModelInput(comp.element, inputState.field, inputState.modelAttr);
                if (newEl && inputState.isFocused) {
                    newEl.focus();
                    try { newEl.setSelectionRange(inputState.selectionStart || newEl.value.length, inputState.selectionStart || newEl.value.length); } catch (e) { /* */ }
                }
            }
        }).catch(function (e) {
            if (e.message !== 'AUTH_EXPIRED' && e.message !== 'CSRF_EXPIRED') console.error('Wire 错误:', e);
            hideLoading(comp, action);
        });
    }

    function handleResponse(comp, data) {
        if (data.snapshot) {
            comp.snapshot = data.snapshot;
            if (comp.configElement) comp.configElement.setAttribute('wire:snapshot', data.snapshot);
        }
        if (data.sections) {
            for (var name in data.sections) {
                if (data.sections.hasOwnProperty(name)) replaceSection(comp, name, data.sections[name]);
            }
        }
        if (data.effects) {
            var r = data.effects.redirect;
            if (r) {
                var url, delay = 0;
                if (typeof r === 'string') { url = r; } else { url = r.url; delay = r.delay || 0; }
                if (delay > 0) setTimeout(function () { window.location.href = url; }, delay);
                else window.location.href = url;
            }
            if (data.effects.dispatch) {
                for (var i = 0; i < data.effects.dispatch.length; i++) {
                    window.dispatchEvent(new CustomEvent(data.effects.dispatch[i].name, { detail: data.effects.dispatch[i].data }));
                }
            }
            if (data.effects.components && window.WireComponent) window.WireComponent.mountAll(data.effects.components);
        }
        wireEmit('afterUpdate', comp, data, data.sections || {});
    }

    function getInputValue(input) {
        var tag = (input.tagName || '').toLowerCase();
        if (tag === 'input') {
            var t = (input.getAttribute('type') || '').toLowerCase();
            if (t === 'checkbox') return input.checked;
            if (t === 'radio') return input.checked ? input.value : '';
            return input.value;
        }
        if (tag === 'select') {
            if (input.type === 'select-multiple') { var v = []; for (var i = 0; i < input.selectedOptions.length; i++) v.push(input.selectedOptions[i].value); return v; }
            return input.value;
        }
        return input.value;
    }
    function collectParams(el) {
        var params = {};
        if (!el || !el.attributes) return params;
        for (var i = 0; i < el.attributes.length; i++) {
            var attr = el.attributes[i];
            if (attr.name.indexOf('wire:param-') === 0) {
                var key = attr.name.substring(11), raw = attr.value;
                if (raw === '') raw = '1';
                else if (raw === 'true') raw = true;
                else if (raw === 'false') raw = false;
                params[key] = raw;
            }
        }
        var row = closestAttr(el, 'data-wire-key') || closestAttr(el, 'wire:key');
        if (row) {
            var models = row.querySelectorAll('[wire\\:model]');
            for (var j = 0; j < models.length; j++) {
                var mk = (models[j].getAttribute('wire:model') || '').split('.')[0];
                if (mk && !(mk in params)) params[mk] = getInputValue(models[j]);
            }
        }
        return params;
    }
    function collectFormData(form) { var p = {}; var fd = new FormData(form); fd.forEach(function (v, k) { p[k] = v; }); return p; }
    function getTargetSections(comp, triggerEl) {
        var targetAttr = triggerEl.getAttribute('wire:target');
        if (!targetAttr) {
            var el = triggerEl.parentElement;
            while (el && el !== comp.element) { if (el.hasAttribute && el.hasAttribute('wire:target')) { targetAttr = el.getAttribute('wire:target'); break; } el = el.parentElement; }
        }
        return targetAttr ? targetAttr.split(',').map(function (s) { return s.trim(); }) : [];
    }
    function getAllSections(comp) {
        var secs = [];
        var els = document.documentElement.querySelectorAll('[wire\\:section]');
        for (var i = 0; i < els.length; i++) { var n = els[i].getAttribute('wire:section'); if (secs.indexOf(n) === -1) secs.push(n); }
        var walker = document.createTreeWalker(document.documentElement, NodeFilter.SHOW_COMMENT, null, null);
        var comment;
        while ((comment = walker.nextNode())) { var m = comment.nodeValue.match(/^wire:section-start:(.+)$/); if (m && secs.indexOf(m[1]) === -1) secs.push(m[1]); }
        return secs;
    }

    function replaceSection(comp, sectionName, html) {
        var cleanContent = html.replace(/<!--wire:section-start:[\s\S]+?-->/g, '').replace(/<!--wire:section-end:[\s\S]+?-->/g, '');
        var sectionEl = document.documentElement.querySelector('[wire\\:section="' + sectionName + '"]');
        if (sectionEl) {
            if (hasKeyedChildren(sectionEl, html)) { var f1 = saveFocus(sectionEl); replaceSectionKeyed(sectionEl, html); restoreFocus(f1); rebindSection(comp, sectionEl); return; }
            var f = saveFocus(sectionEl); sectionEl.innerHTML = html; restoreFocus(f); rebindSection(comp, sectionEl); return;
        }
        var start = findComment(document.documentElement, 'wire:section-start:' + sectionName);
        var end = findComment(document.documentElement, 'wire:section-end:' + sectionName);
        if (start && end) {
            var toRemove = [], node = start.nextSibling;
            while (node && node !== end) { toRemove.push(node); node = node.nextSibling; }
            var parent = start.parentNode;
            var f2 = saveFocus(parent);
            for (var i = 0; i < toRemove.length; i++) parent.removeChild(toRemove[i]);
            var tmpl = document.createElement('template'); tmpl.innerHTML = html;
            parent.insertBefore(tmpl.content, end);
            restoreFocus(f2);
            activateScriptsInContext(parent);
            replayReadyScripts(parent);
            rebindSection(comp, parent);
            return;
        }
        var rawEls = document.querySelectorAll('title, style, script');
        for (var r = 0; r < rawEls.length; r++) {
            var el = rawEls[r]; var content = el.textContent || '';
            var startMk = '<!--wire:section-start:' + sectionName + '-->';
            var endMk = '<!--wire:section-end:' + sectionName + '-->';
            var si = content.indexOf(startMk);
            if (si >= 0) {
                var ei = content.indexOf(endMk, si);
                if (ei >= 0) {
                    if (el.tagName === 'TITLE') document.title = cleanContent;
                    else el.textContent = cleanContent;
                    return;
                }
            }
        }
        var allEls = document.documentElement.querySelectorAll('*');
        for (var a = 0; a < allEls.length; a++) {
            var elem = allEls[a];
            for (var ai = 0; ai < elem.attributes.length; ai++) {
                var attr = elem.attributes[ai];
                if (attr.value.indexOf('<!--wire:section-start:' + sectionName + '-->') >= 0) {
                    elem.setAttribute(attr.name, attr.value.replace(new RegExp('<!--wire:section-start:' + sectionName + '-->([\\s\\S]*?)<!--wire:section-end:' + sectionName + '-->'), cleanContent));
                    return;
                }
            }
        }
    }

    function hasKeyedChildren(sectionEl, html) { return findKeyedContainerPair(sectionEl, html) !== null; }
    function findKeyedContainerPair(sectionEl, html) {
        var tmp = document.createElement('div'); tmp.innerHTML = html;
        var newKeyed = tmp.querySelector('[data-wire-key]');
        if (!newKeyed || !newKeyed.parentNode) return null;
        var newContainer = newKeyed.parentNode;
        var path = [];
        var cur = newContainer;
        while (cur && cur !== tmp) { var p = cur.parentNode; if (!p) return null; path.unshift(Array.prototype.indexOf.call(p.children, cur)); cur = p; }
        if (cur !== tmp) return null;
        var oldContainer = sectionEl;
        for (var i = 0; i < path.length; i++) { if (!oldContainer.children || path[i] >= oldContainer.children.length) return null; oldContainer = oldContainer.children[path[i]]; }
        if (!oldContainer.querySelector) return null;
        var hasKey = false;
        for (var c = 0; c < oldContainer.children.length; c++) { if (oldContainer.children[c].getAttribute && oldContainer.children[c].getAttribute('data-wire-key')) { hasKey = true; break; } }
        if (!hasKey) return null;
        if (oldContainer.tagName !== newContainer.tagName) return null;
        return { oldContainer: oldContainer, newContainer: newContainer, newRoot: tmp };
    }
    function replaceSectionKeyed(sectionEl, html) {
        var pair = findKeyedContainerPair(sectionEl, html);
        if (!pair) { sectionEl.innerHTML = html; return; }
        var oldContainer = pair.oldContainer;
        var keptRows = {}, oldRows = Array.prototype.slice.call(oldContainer.children);
        for (var i = 0; i < oldRows.length; i++) { var k = oldRows[i].getAttribute && oldRows[i].getAttribute('data-wire-key'); if (k) keptRows[k] = oldRows[i]; }
        var path = []; var cur = oldContainer;
        while (cur && cur !== sectionEl) { var p = cur.parentNode; if (!p) break; path.unshift(Array.prototype.indexOf.call(p.children, cur)); cur = p; }
        sectionEl.innerHTML = html;
        var fresh = sectionEl;
        for (var s = 0; s < path.length; s++) { if (!fresh.children || path[s] >= fresh.children.length) { fresh = null; break; } fresh = fresh.children[path[s]]; }
        if (!fresh) return;
        var rows = Array.prototype.slice.call(fresh.children);
        for (var r = 0; r < rows.length; r++) { var nk = rows[r].getAttribute && rows[r].getAttribute('data-wire-key'); if (nk && keptRows[nk]) carryOverRowState(keptRows[nk], rows[r]); }
    }
    function carryOverRowState(oldRow, newRow) {
        var oldIn = oldRow.querySelectorAll('input, textarea, select');
        var newIn = newRow.querySelectorAll('input, textarea, select');
        if (oldIn.length !== newIn.length) return;
        for (var i = 0; i < oldIn.length; i++) {
            var o = oldIn[i], n = newIn[i]; var type = (n.getAttribute('type') || '').toLowerCase();
            if (type === 'checkbox' || type === 'radio' || type === 'file') continue;
            if (o.value !== o.getAttribute('value') && o.getAttribute('value') === n.getAttribute('value')) n.value = o.value;
        }
    }

    function findComment(root, text) {
        var walker = document.createTreeWalker(root, NodeFilter.SHOW_COMMENT, null, null);
        var comment;
        while ((comment = walker.nextNode())) { if (comment.nodeValue === text) return comment; }
        return null;
    }
    function findModelInput(container, field, modelAttr) {
        if (!container || !field || !modelAttr) return null;
        var els = container.querySelectorAll('[data-wire-field="' + field + '"]');
        for (var i = 0; i < els.length; i++) if (els[i].getAttribute('data-wire-model-attr') === modelAttr) return els[i];
        return null;
    }

    function showLoading(comp, action) {
        var els = comp.element.querySelectorAll('[wire\\:loading]');
        for (var i = 0; i < els.length; i++) { var el = els[i]; var t = el.getAttribute('wire:target'); if (!t || t === action) { el.style.display = ''; el.setAttribute('wire:loading-active', 'true'); } }
        var triggers = comp.element.querySelectorAll('[wire\\:click="' + action + '"], [wire\\:submit="' + action + '"]');
        for (var j = 0; j < triggers.length; j++) triggers[j].setAttribute('wire:loading', 'true');
    }
    function hideLoading(comp, action) {
        var triggers = comp.element.querySelectorAll('[wire\\:click="' + action + '"], [wire\\:submit="' + action + '"]');
        for (var j = 0; j < triggers.length; j++) { triggers[j].removeAttribute('wire:loading'); triggers[j].style.display = ''; }
        var els = comp.element.querySelectorAll('[wire\\:loading]');
        for (var i = 0; i < els.length; i++) { var el = els[i]; var t = el.getAttribute('wire:target'); if (!t || t === action) { el.style.display = 'none'; el.removeAttribute('wire:loading-active'); } }
    }
    function saveFocus(container) {
        var active = document.activeElement;
        if (!active || !container.contains(active)) return null;
        var path = '', el = active;
        while (el && el !== container) {
            var sel = el.tagName.toLowerCase();
            var rk = el.getAttribute('data-wire-key');
            if (el.id) sel += '#' + el.id;
            else if (rk) sel += '[data-wire-key="' + rk + '"]';
            else if (el.getAttribute('data-wire-field')) sel += '[data-wire-field="' + el.getAttribute('data-wire-field') + '"]';
            else if (el.name) sel += '[name="' + el.name + '"]';
            else { var parent = el.parentElement; if (parent) sel += ':nth-child(' + (Array.prototype.indexOf.call(parent.children, el) + 1) + ')'; }
            path = path ? sel + ' > ' + path : sel;
            el = el.parentElement;
        }
        var ss = null, se = null;
        if (active.type !== 'checkbox' && active.type !== 'radio' && active.selectionStart !== undefined) { ss = active.selectionStart; se = active.selectionEnd; }
        return { path: path, container: container, selectionStart: ss, selectionEnd: se };
    }
    function restoreFocus(info) {
        if (!info || !info.path) return;
        try {
            var scope = (info.container && info.container.querySelector) ? info.container : document;
            var el = scope.querySelector(info.path);
            if (el && el.focus) { el.focus(); if (info.selectionStart !== null && el.setSelectionRange) el.setSelectionRange(info.selectionStart, info.selectionEnd); }
        } catch (e) { /* */ }
    }
    function rebindSection(comp, sectionEl) { bindEvents(comp); }

    function redirectToLogin(loginUrl) {
        var url = window.location.href;
        if (window.location.pathname === loginUrl) return;
        window.location.href = loginUrl + ((loginUrl.indexOf('?') >= 0) ? '&' : '?') + 'redirect=' + encodeURIComponent(url);
    }

    // ======================== WireComponent（命名组件） ========================

    var compInstances = {};
    var compInited = false;

    function parseLifecycle(src) {
        if (!src) return {};
        try {
            var factory = new Function(src + ';return {onCreate:typeof onCreate==="function"?onCreate:null,onStart:typeof onStart==="function"?onStart:null,onStop:typeof onStop==="function"?onStop:null,onDestroy:typeof onDestroy==="function"?onDestroy:null};');
            return factory() || {};
        } catch (e) { console.error('[WireComponent] 生命周期脚本解析失败:', e); return {}; }
    }
    function getOutlet(outletId) {
        if (outletId) { var by = document.getElementById(outletId); if (by) return by; }
        return document.querySelector('[wire\\:outlet]') || document.getElementById('wire-outlet');
    }
    function callLife(inst, name, el, wire) {
        var fn = inst.api[name]; if (typeof fn !== 'function') return undefined;
        try { return fn(el, wire); } catch (e) { console.error('[WireComponent] ' + inst.name + '.' + name + ' 执行异常', e); }
        return undefined;
    }
    function compMount(payload, outletId) {
        if (!payload || !payload.id) return null;
        if (compInstances[payload.id]) return compInstances[payload.id];
        var outlet = getOutlet(outletId || payload.outlet);
        if (!outlet) { console.error('[WireComponent] 找不到 outlet 容器，无法挂载组件 [' + payload.name + ']'); return null; }
        var wrap = document.createElement('div'); wrap.innerHTML = payload.html || '';
        var el = wrap.firstElementChild || wrap;
        var api = parseLifecycle(payload.script);
        var inst = { id: payload.id, name: payload.name, el: el, params: payload.params || {}, api: api, removing: false, wire: null };
        var wire = { id: payload.id, name: payload.name, params: inst.params, el: el, stop: function () { compStop(inst); } };
        inst.wire = wire; compInstances[payload.id] = inst;
        callLife(inst, 'onCreate', el, wire);
        outlet.appendChild(el);
        callLife(inst, 'onStart', el, wire);
        return inst;
    }
    function compStop(inst) {
        if (!inst || inst.removing) return;
        inst.removing = true;
        var ret = callLife(inst, 'onStop', inst.el, inst.wire);
        var finish = function () { if (inst.el && inst.el.parentNode) inst.el.parentNode.removeChild(inst.el); callLife(inst, 'onDestroy', inst.el, inst.wire); delete compInstances[inst.id]; };
        if (typeof ret === 'number') setTimeout(finish, ret);
        else if (ret && typeof ret.then === 'function') ret.then(finish, finish);
        else finish();
    }
    function compMountAll(payloads, outletId) { for (var i = 0; i < payloads.length; i++) compMount(payloads[i], outletId); }
    function compMountBootstrapTags() {
        var tags = document.querySelectorAll('script[type="application/json"][wire\\:components]');
        for (var i = 0; i < tags.length; i++) {
            var tag = tags[i]; var outletId = tag.getAttribute('data-wire-outlet') || 'wire-outlet';
            var list = []; try { list = JSON.parse(tag.textContent || '[]'); } catch (e) { console.error('[WireComponent] 首屏引导数据解析失败', e); }
            compMountAll(list, outletId);
            if (tag.parentNode) tag.parentNode.removeChild(tag);
        }
    }
    function compInit() { if (compInited) return; compInited = true; compMountBootstrapTags(); }

    window.WireComponent = {
        __runtime: 'wire-merged',
        mount: compMount, mountAll: compMountAll,
        stop: function (id) { compStop(compInstances[id]); },
        init: compInit,
        scan: compMountBootstrapTags,
        version: '1.2'
    };

    // ======================== WireNavigate（透明导航） ========================

    var navUrl = location.href;
    var navHashes = {};
    var navListeners = {};

    function navOn(evt, fn) { (navListeners[evt] = navListeners[evt] || []).push(fn); }
    function navOff(evt, fn) { navListeners[evt] = (navListeners[evt] || []).filter(function (f) { return f !== fn; }); }
    function navEmit(evt, detail) {
        document.dispatchEvent(new CustomEvent('wire:navigate:' + evt, { detail: detail }));
        if (navListeners[evt]) navListeners[evt].forEach(function (fn) { try { fn(detail); } catch (e) { console.error(e); } });
    }

    window.WireNavigate = {
        __runtime: 'wire-merged',
        on: navOn, off: navOff, visit: visit,
        rescan: function () { refreshRuntimes(); },
        currentUrl: function () { return navUrl; }
    };

    // --- 合并后统一的 FNV-1a 32-bit hash（与 Java 端 WireRenderer.hash 完全一致） ---
    function wireHash(str) {
        if (!str) return '00000000';
        var h = 0x811c9dc5;
        for (var i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = (h * 0x01000193) | 0; }
        return (h >>> 0).toString(16).padStart(8, '0');
    }

    // 收集当前文档中所有已知 section 名（用于计算哈希时剥离嵌套）
    var knownSectionNames = {};

    function collectKnownSectionNames() {
        knownSectionNames = {};
        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_COMMENT, null, false);
        var comment;
        while ((comment = walker.nextNode())) {
            var m = comment.nodeValue.match(/^wire:section-start:([a-zA-Z0-9_-]+)$/);
            if (m) knownSectionNames[m[1]] = true;
        }
    }

    // 对 section 内容计算 hash：剥离嵌套子 section，与 Java 端同口径。
    function hashSectionContent(content, ownName) {
        if (!content) return '00000000';
        var needsNormalize = false;
        for (var s in knownSectionNames) {
            if (s !== ownName && content.indexOf('<!--wire:section-start:' + s + '-->') >= 0) { needsNormalize = true; break; }
        }
        if (!needsNormalize) return wireHash(content);
        var normalized = content.replace(/<!--wire:section-start:([a-zA-Z0-9_-]+)-->.*?<!--wire:section-end:\1-->/gs,
            function (full, childName) {
                if (childName === ownName) return full;
                if (knownSectionNames[childName]) return 'WIRE_SECTION_PLACEHOLDER:' + childName;
                return full;
            }
        );
        return wireHash(normalized);
    }

    function navComputeHashes() {
        if (window.__wireHashes) { navHashes = Object.assign({}, window.__wireHashes); return; }
        collectKnownSectionNames();
        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_COMMENT, null, false);
        var open = {}; var comment; navHashes = {};
        while ((comment = walker.nextNode())) {
            var text = comment.nodeValue || '';
            var startMatch = /^wire:section-start:([a-zA-Z0-9_-]+)$/.exec(text);
            if (startMatch) { open[startMatch[1]] = comment; continue; }
            var endMatch = /^wire:section-end:([a-zA-Z0-9_-]+)$/.exec(text);
            if (endMatch && open[endMatch[1]]) {
                var name = endMatch[1];
                var sc = getSectionContentFromNodes(open[name], comment);
                navHashes[name] = hashSectionContent(sc, name);
                delete open[name];
            }
        }
    }

    function navInit() {
        navComputeHashes();
        navEmit('ready', { url: navUrl, hashes: navHashes });
    }

    function walkSections(fn) {
        collectKnownSectionNames();
        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_COMMENT, null, false);
        var open = {}; var node;
        while ((node = walker.nextNode())) {
            var text = node.textContent || '';
            var sm = /^wire:section-start:([a-zA-Z0-9_-]+)$/.exec(text);
            if (sm) { open[sm[1]] = node; continue; }
            var em = /^wire:section-end:([a-zA-Z0-9_-]+)$/.exec(text);
            if (em && open[em[1]]) { fn(em[1], { startNode: open[em[1]], endNode: node, name: em[1] }); delete open[em[1]]; }
        }
    }
    function getSectionContentFromNodes(startNode, endNode) {
        var parts = []; var node = startNode.nextSibling;
        while (node && node !== endNode) {
            if (node.nodeType === Node.ELEMENT_NODE) parts.push(node.outerHTML);
            else if (node.nodeType === Node.TEXT_NODE) parts.push(node.textContent);
            node = node.nextSibling;
        }
        return parts.join('');
    }
    function replaceSectionContent(section, newHtml) {
        var parentNode = section.startNode.parentNode;
        if (!parentNode) return;
        var node = section.startNode.nextSibling;
        while (node && node !== section.endNode) { var next = node.nextSibling; node.parentNode.removeChild(node); node = next; }
        var temp = document.createElement('div'); temp.innerHTML = newHtml;
        while (temp.firstChild) { parentNode.insertBefore(temp.firstChild, section.endNode); }
        section.parentNode = parentNode;
        return parentNode;
    }

    /**
     * 激活新插入的 script 标签（innerHTML 插入的 <script> 不执行，需替换为真实 script 节点）。
     */
    function activateScriptsInContext(root) {
        if (!root || !root.querySelectorAll) return;
        var scripts = root.querySelectorAll('script');
        for (var i = 0; i < scripts.length; i++) {
            var old = scripts[i];
            var type = (old.getAttribute('type') || '').toLowerCase();
            var isDataOnly = old.hasAttribute('wire:config') || old.hasAttribute('wire:snapshot')
                || old.hasAttribute('wire:components') || type === 'application/json';
            if (isDataOnly) continue;
            var src = old.getAttribute('src');
            if (src) {
                var fresh = document.createElement('script');
                for (var a = 0; a < old.attributes.length; a++) {
                    var attr = old.attributes[a];
                    if (attr.name !== 'type') fresh.setAttribute(attr.name, attr.value);
                }
                fresh.type = 'application/javascript';
                old.parentNode.replaceChild(fresh, old);
                continue;
            }
            var text = old.textContent || '';
            if (!text.trim()) continue;
            try {
                (function (body) { eval(body); })(text);
            } catch (e) { console.error('[Wire] 内联脚本执行失败:', e); }
            if (old.parentNode) old.parentNode.removeChild(old);
        }
    }

    var readyCallbacks = [];
    var readyCallbackIds = {};

    function _registerReady(fn) { readyCallbacks.push(fn); if (typeof fn === 'function') readyCallbackIds[fn.toString()] = true; }
    function _unregisterReady(fn) {
        var i = readyCallbacks.indexOf(fn);
        if (i >= 0) readyCallbacks.splice(i, 1);
        if (typeof fn === 'function') delete readyCallbackIds[fn.toString()];
    }
    window.__wireReady = { on: _registerReady, off: _unregisterReady };

    var _jQueryReady = null, _MDuiReady = null;
    function _hookReadyFunctions() {
        try {
            if (window.jQuery) {
                var _jq = window.jQuery.fn;
                if (_jq.ready && !_jq.ready.__wireHooked) {
                    _jq.ready.__wireHooked = true;
                    var _origReady = _jq.ready;
                    _jq.ready = function (fn) { _registerReady(fn); return _origReady.apply(this, arguments); };
                }
                if (window.jQuery.readyList) {
                    for (var i = 0; i < window.jQuery.readyList.length; i++) {
                        var cb = window.jQuery.readyList[i];
                        if (typeof cb === 'function' && readyCallbacks.indexOf(cb) === -1) {
                            _registerReady(cb);
                        }
                    }
                }
            }
            if (window.mdui && mdui.$) {
                var _md = mdui.$.fn || mdui.$;
                if (_md.ready && !_md.ready.__wireHooked) {
                    _md.ready.__wireHooked = true;
                    var _origMdReady = _md.ready;
                    _md.ready = function (fn) { _registerReady(fn); return _origMdReady.apply(this, arguments); };
                }
            }
        } catch (e) { /* */ }
    }
    _hookReadyFunctions();

    function _rewriteDollarReady() {
        try {
            if (window.$ && window.$.fn && window.$.fn.ready) {
                var _orig = window.$.fn.ready;
                window.$.fn.ready = function (fn) {
                    _registerReady(fn);
                    if (document.readyState === 'complete' && typeof fn === 'function') {
                        try { fn(document); } catch (e) { /* */ }
                    }
                    return _orig.apply(this, arguments);
                };
            }
        } catch (e) { /* */ }
    }
    _rewriteDollarReady();

    function replayReadyScripts(root) {
        setTimeout(function () {
            for (var i = 0; i < readyCallbacks.length; i++) {
                try { readyCallbacks[i].call && readyCallbacks[i].call(document); }
                catch (e) { console.error('[Wire] ready 回调重放失败:', e); }
            }
        }, 0);
    }

    function applyAnchors(anchors) {
        if (!anchors) return;
        for (var key in anchors) {
            var value = anchors[key];
            if (key.indexOf('text:') === 0) {
                var section = key.slice(5);
                var el = document.querySelector('[wire\\:section-text~="' + section + '"]');
                if (el) { if (el.tagName === 'TITLE') document.title = value; else el.textContent = value; }
            } else if (key.indexOf('attr:') === 0) {
                var token = key.slice(5); var colon = token.indexOf(':');
                if (colon > 0) {
                    var attrName = token.slice(0, colon);
                    var target = document.querySelector('[wire\\:section-attr~="' + token + '"]');
                    if (target) target.setAttribute(attrName, value);
                }
            }
        }
    }

    function visit(url) {
        if (!url) return;
        navEmit('before', { url: url });
        var xhr = new XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.setRequestHeader('X-Wire-Navigate', 'true');
        var parts = [];
        for (var k in navHashes) { if (navHashes.hasOwnProperty(k)) parts.push(k + '=' + navHashes[k]); }
        xhr.setRequestHeader('X-Wire-Hashes', parts.join(','));
        xhr.setRequestHeader('Accept', 'application/json, text/html');
        xhr.onload = function () {
            if (xhr.status >= 200 && xhr.status < 300) {
                var ct = xhr.getResponseHeader('Content-Type') || '';
                if (ct.indexOf('application/json') >= 0) {
                    try { var payload = JSON.parse(xhr.responseText); applyDiff(payload, url); }
                    catch (e) { console.error('[wire-navigate] JSON parse error:', e); hardNavigate(url); }
                } else hardNavigate(url);
            } else if (xhr.status === 302 || xhr.status === 301) { var loc = xhr.getResponseHeader('Location'); if (loc) visit(loc); }
            else hardNavigate(url);
        };
        xhr.onerror = function () { hardNavigate(url); };
        xhr.send();
    }

    function applyDiff(payload, url) {
        var sections = payload.sections || {};
        var hashes = payload.hashes || {};
        var changedCount = 0;
        var activatedParents = [];
        var changedKeys = [];
        walkSections(function (name, section) {
            var newHtml = sections[name];
            if (newHtml !== undefined) {
                changedKeys.push(name);
                var parent = replaceSectionContent(section, newHtml);
                if (parent) activatedParents.push(parent);
                changedCount++;
            }
            if (hashes[name] !== undefined) navHashes[name] = hashes[name];
        });
        console.warn('[wire] 导航到 ' + url + '，替换 section: ' + changedKeys.join(',') + '，共 ' + changedCount + ' 个');
        // 只执行替换区域内新增的 script（避免重复执行其他 section 的脚本）
        for (var r = 0; r < activatedParents.length; r++) {
            activateScriptsInSection(activatedParents[r]);
        }
        applyAnchors(payload.anchors);
        if (payload.title) document.title = payload.title;
        refreshRuntimes();
        replayReadyScripts(document.body);
        var finalUrl = payload.url || url;
        if (finalUrl && finalUrl !== navUrl) { history.pushState({ wireUrl: finalUrl }, '', finalUrl); navUrl = finalUrl; }
        navEmit('success', { url: finalUrl, payload: payload, changedCount: changedCount });
        navEmit('complete', { url: finalUrl });
    }

    function activateScriptsInSection(parentNode) {
        if (!parentNode) return;
        // 遍历所有 section，只激活已替换区域内的 script
        var sectionsToActivate = [];
        walkSections(function (name, section) {
            sectionsToActivate.push(section);
        });
        for (var i = 0; i < sectionsToActivate.length; i++) {
            var sec = sectionsToActivate[i];
            var scripts = [];
            var n = sec.startNode.nextSibling;
            while (n && n !== sec.endNode) {
                if (n.nodeType === Node.ELEMENT_NODE && n.tagName === 'SCRIPT') scripts.push(n);
                n = n.nextSibling;
            }
            for (var s = 0; s < scripts.length; s++) {
                var old = scripts[s];
                var type = (old.getAttribute('type') || '').toLowerCase();
                var isDataOnly = old.hasAttribute('wire:config') || old.hasAttribute('wire:snapshot')
                    || old.hasAttribute('wire:components') || type === 'application/json';
                if (isDataOnly) continue;
                var src = old.getAttribute('src');
                if (src) {
                    var fresh = document.createElement('script');
                    for (var a = 0; a < old.attributes.length; a++) {
                        var attr = old.attributes[a];
                        if (attr.name !== 'type') fresh.setAttribute(attr.name, attr.value);
                    }
                    fresh.type = 'application/javascript';
                    old.parentNode.replaceChild(fresh, old);
                    continue;
                }
                var text = old.textContent || '';
                if (!text.trim()) continue;
                try {
                    (function (body) { eval(body); })(text);
                } catch (e) { console.error('[Wire] 内联脚本执行失败:', e); }
                if (old.parentNode) old.parentNode.removeChild(old);
            }
        }
    }

    function refreshRuntimes() {
        try { if (window.Wire && typeof window.Wire.scan === 'function') window.Wire.scan(); }
        catch (e) { console.error('[wire-navigate] Wire.scan() 失败', e); }
        try { if (window.WireComponent && typeof window.WireComponent.scan === 'function') window.WireComponent.scan(); }
        catch (e) { console.error('[wire-navigate] WireComponent.scan() 失败', e); }
        navEmit('rescan', { url: navUrl });
    }
    function hardNavigate(url) { window.location.assign(url); }

    document.addEventListener('click', function (e) {
        var el = e.target.closest('a');
        if (!el || !el.href) return;
        if (!el.hasAttribute('wire-navigate') && !el.hasAttribute('data-wire')) return;
        if (el.host !== location.host) return;
        if (!/^https?:/.test(el.protocol)) return;
        if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
        if (el.hasAttribute('target') || el.hasAttribute('download') || el.getAttribute('href') === '#') return;
        e.preventDefault();
        visit(el.href);
    });
    window.addEventListener('popstate', function (e) {
        var url = (e.state && e.state.wireUrl) || location.href;
        if (url !== navUrl) visit(url);
    });

    function wireInit() { cleanHeadWireMarkers(); Wire.scan(); }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { wireInit(); compInit(); navInit(); });
    } else {
        wireInit(); compInit(); navInit();
    }

    Wire.refresh = function (sections, action, params) {
        var comp = Wire.components[0]; if (!comp) return;
        var target = null;
        if (typeof sections === 'string') target = sections ? [sections] : null;
        else if (Array.isArray(sections)) target = sections.length ? sections : null;
        sendRequest(comp, action || '$refresh', params || {}, null, target);
    };

    window.Wire = Wire;
})();