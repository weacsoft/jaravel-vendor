/**
 * Wire.js — 单一运行时：Laravel Livewire 风格部分更新 + 命名组件 + 透明导航。
 */
(function () {
    'use strict';
    if (window.Wire && window.Wire.__runtime === 'wire-merged') return;

    var Wire = {
        __runtime: 'wire-merged',
        components: [], debounceTimers: {}, _listeners: {}
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
            try { arr[i].apply(null, args); } catch (e) { console.error('[Wire] 事件监听器异常:', e); }
        }
    }

    function collectConfigElements() {
        var configs = [];
        try { var bySel = document.querySelectorAll('[wire\\:config]'); for (var s = 0; s < bySel.length; s++) configs.push(bySel[s]); } catch (e) {}
        if (configs.length === 0) {
            var scripts = document.querySelectorAll('script');
            for (var i = 0; i < scripts.length; i++) {
                var sc = scripts[i];
                if (sc.hasAttribute('wire:config') || sc.hasAttribute('wire:snapshot')) configs.push(sc);
            }
        }
        return configs;
    }

    Wire.scan = function () {
        var configs = collectConfigElements();
        for (var i = 0; i < configs.length; i++) {
            var cfg = configs[i];
            var known = false;
            for (var k = 0; k < Wire.components.length; k++) { if (Wire.components[k].configElement === cfg) { known = true; break; } }
            if (!known) initComponent(cfg);
        }
        for (var j = 0; j < Wire.components.length; j++) bindEvents(Wire.components[j]);
        return Wire;
    };

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

    function initComponent(configEl) {
        // 作用域:优先取 config 所在容器(如下发的对话框),否则回退整页。
        // 这样下发的对话框组件以自身为作用域,其 wire:model / wire:submit 不会与
        // 外层列表组件的事件被重复绑定(isOwnedByOther 会跳过被其它组件标记的元素)。
        var scope = configEl.parentElement || document.body;
        var component = {
            element: scope,
            configElement: configEl,
            updateUrl: configEl.getAttribute('data-wire-update') || '/wire/update',
            snapshot: configEl.getAttribute('wire:snapshot') || '',
            id: 'wire-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9),
            boundElements: new Set(),
            // wire:model 客户端缓存:双向绑定只在本地更新此缓存,不发起服务端请求,
            // 避免每个按键都触发整段 section 重渲染(会把 mdui 对话框的 DOM 状态冲掉)。
            // 缓存仅在「真正的 action」(wire:click / wire:submit / wire:change / wire:keydown)
            // 发起时作为 params 合并进请求,由服务端 update() 合并进快照后再 fill。
            modelCache: {}
        };
        // 标记该作用域为本组件私有,外层组件绑定事件时会跳过(见 isOwnedByOther)
        if (scope !== document.body && scope.setAttribute) {
            scope.setAttribute('data-wire-owned', component.id);
        }
        Wire.components.push(component);
        bindEvents(component);
    }

    /**
     * 从 DOM 中读取所有 wire:model 输入框的当前值,写回组件的 modelCache。
     * @param root 可选的作用域根元素(默认整页),用于只同步某个表单子树。
     */
    function syncModelFromDom(comp, root) {
        if (!comp) return;
        var ctx = root || comp.element;
        if (!ctx || !ctx.querySelectorAll) return;
        var inputs = ctx.querySelectorAll('input, textarea, select');
        for (var i = 0; i < inputs.length; i++) {
            var input = inputs[i];
            var field = null;
            for (var j = 0; j < input.attributes.length; j++) {
                var a = input.attributes[j];
                if (a.name === 'wire:model' || a.name.indexOf('wire:model.') === 0) { field = a.value; break; }
            }
            if (field) comp.modelCache[field] = getInputValue(input);
        }
    }

    /** 清空并依据当前 DOM 重新建立 modelCache,保证缓存永远等于页面实际值。 */
    function resyncModelCache(comp) {
        if (!comp) return;
        comp.modelCache = {};
        syncModelFromDom(comp, comp.element);
    }

    function bindEvents(comp) {
        bindClick(comp); bindSubmit(comp); bindModel(comp);
        bindChange(comp); bindKeydown(comp);
    }
    function markBound(comp, el) {
        if (comp.boundElements.has(el)) return false;
        comp.boundElements.add(el);
        return true;
    }

    // 判断元素是否属于「其它组件」私有作用域(被该组件的 data-wire-owned 标记包围),
    // 用于避免外层组件(如列表)重复绑定内层(如对话框)组件的事件(wire:submit 等)。
    function isOwnedByOther(el, comp) {
        if (!el || !el.closest) return false;
        var owned = el.closest('[data-wire-owned]');
        if (!owned) return false;
        return owned !== comp.element;
    }

    /**
     * 结构化解析 wire:action 表达式。
     * 不使用 eval,只做词法解析。参数全部视为字符串。
     */
    function parseWireAction(expr) {
        if (!expr) return { method: '', params: [] };
        var method = expr;
        var params = [];
        var lparen = expr.indexOf('(');
        var rparen = expr.lastIndexOf(')');
        if (lparen >= 0 && rparen > lparen) {
            method = expr.substring(0, lparen);
            var raw = expr.substring(lparen + 1, rparen);
            if (raw.trim()) {
                var cur = '', inStr = false, quote = '';
                for (var i = 0; i < raw.length; i++) {
                    var c = raw[i];
                    if (inStr) {
                        cur += c;
                        if (c === quote) inStr = false;
                    } else if (c === "'" || c === '"') {
                        inStr = true; quote = c; cur += c;
                    } else if (c === ',') {
                        var trimmed = cur.trim();
                        if (trimmed) params.push(trimmed.replace(/^['"]|['"]$/g, ''));
                        cur = '';
                    } else {
                        cur += c;
                    }
                }
                var trimmed = cur.trim();
                if (trimmed) params.push(trimmed.replace(/^['"]|['"]$/g, ''));
            }
        }
        return { method: method, params: params };
    }

    function bindClick(comp) {
        var els = comp.element.querySelectorAll('[wire\\:click]');
        for (var i = 0; i < els.length; i++) (function (el) {
            if (isOwnedByOther(el, comp)) return;
            if (!markBound(comp, el)) return;
            el.addEventListener('click', function (e) {
                e.preventDefault();
                var actionExpr = el.getAttribute('wire:click') || '';
                var parsed = parseWireAction(actionExpr);
                var params = Object.assign({}, collectParams(el));
                for (var i = 0; i < parsed.params.length; i++) params[String(i)] = parsed.params[i];
                sendRequest(comp, parsed.method, params, el);
            });
        })(els[i]);
    }

    function bindSubmit(comp) {
        // 支持 form[wire:submit]
        var forms = comp.element.querySelectorAll('form[wire\\:submit]');
        for (var i = 0; i < forms.length; i++) (function (form) {
            if (isOwnedByOther(form, comp)) return;
            if (!markBound(comp, form)) return;
            form.addEventListener('submit', function (e) {
                e.preventDefault();
                var actionExpr = form.getAttribute('wire:submit') || '';
                var parsed = parseWireAction(actionExpr);
                var p = {};
                var fd = new FormData(form); fd.forEach(function (v, k) { p[k] = v; });
                Object.assign(p, collectParams(form));
                for (var i = 0; i < parsed.params.length; i++) p[String(i)] = parsed.params[i];
                sendRequest(comp, parsed.method, p, form);
            });
        })(forms[i]);

        // button/input[type=submit][wire:submit]:拦截其所属 form 的 submit
        var buttons = comp.element.querySelectorAll('button[wire\\:submit], input[type=submit][wire\\:submit]');
        for (var j = 0; j < buttons.length; j++) (function (btn) {
            if (isOwnedByOther(btn, comp)) return;
            if (!markBound(comp, btn)) return;
            var form = btn.closest('form');
            if (!form) {
                btn.addEventListener('click', function (e) {
                    e.preventDefault();
                    var actionExpr = btn.getAttribute('wire:submit') || '';
                    var parsed = parseWireAction(actionExpr);
                    var params = Object.assign({}, collectParams(btn));
                    for (var i = 0; i < parsed.params.length; i++) params[String(i)] = parsed.params[i];
                    sendRequest(comp, parsed.method, params, btn);
                });
                return;
            }
            if (!form.__wireSubmitBound) {
                form.__wireSubmitBound = true;
                form.addEventListener('submit', function (e) {
                    var target = e.submitter || e.srcElement;
                    if (target && target.getAttribute && target.getAttribute('wire:submit')) {
                        e.preventDefault();
                        var actionExpr = target.getAttribute('wire:submit');
                        var parsed = parseWireAction(actionExpr);
                        var p = {};
                        var fd = new FormData(form); fd.forEach(function (v, k) { p[k] = v; });
                        Object.assign(p, collectParams(target));
                        for (var i = 0; i < parsed.params.length; i++) p[String(i)] = parsed.params[i];
                        sendRequest(comp, parsed.method, p, target);
                    }
                });
            }
        })(buttons[j]);
    }

    function bindModel(comp) {
        var all = comp.element.querySelectorAll('input, textarea, select');
        for (var i = 0; i < all.length; i++) (function (input) {
            var field = null;
            for (var j = 0; j < input.attributes.length; j++) {
                var attr = input.attributes[j];
                if (attr.name === 'wire:model' || attr.name.indexOf('wire:model.') === 0) {
                    field = attr.value; break;
                }
            }
            if (!field || isOwnedByOther(input, comp) || !markBound(comp, input)) return;
            // 双向绑定:只把最新值写进组件本地缓存,不发起任何服务端请求,
            // 因此不会触发整段 section 重渲染(避免冲掉对话框 DOM 状态)。
            // 真正的同步发生在下一个真实 action(wire:click / wire:submit 等)发起时,
            // 由 sendRequest 把 modelCache 合并进 params 一并提交。
            var sync = function () {
                comp.modelCache[field] = getInputValue(input);
            };
            input.addEventListener('change', sync);
            input.addEventListener('input', sync);
        })(all[i]);
    }

    function bindChange(comp) {
        var els = comp.element.querySelectorAll('[wire\\:change]');
        for (var i = 0; i < els.length; i++) (function (el) {
            if (isOwnedByOther(el, comp)) return;
            if (!markBound(comp, el)) return;
            el.addEventListener('change', function () { sendRequest(comp, el.getAttribute('wire:change'), collectParams(el), el); });
        })(els[i]);
    }

    function bindKeydown(comp) {
        var els = comp.element.querySelectorAll('[wire\\:keydown]');
        for (var i = 0; i < els.length; i++) (function (el) {
            if (isOwnedByOther(el, comp)) return;
            if (!markBound(comp, el)) return;
            el.addEventListener('keydown', function (e) {
                var attr = el.getAttribute('wire:keydown');
                sendRequest(comp, attr, collectParams(el), el);
            });
        })(els[i]);
    }

    function sendRequest(comp, action, params, triggerEl, targetSections) {
        var updateUrl = comp.updateUrl;
        var el = triggerEl;
        while (el && el !== document) { if (el.hasAttribute && el.hasAttribute('wire:update')) { updateUrl = el.getAttribute('wire:update'); break; } el = el.parentElement; }
        var sections = targetSections && targetSections.length ? targetSections : getAllSections(comp);
        // 真实 action(wire:click / wire:submit / wire:change / wire:keydown 指向的方法)发起时,
        // 把 wire:model 的客户端缓存合并进 params,由服务端 update() 合并进快照并 fill。
        // $sync 不会走到这里(见 bindModel);$refresh 为纯刷新,不携带未决编辑。
        var finalParams = params || {};
        if (comp && comp.modelCache && action && action !== '$sync' && action !== '$refresh') {
            finalParams = Object.assign({}, comp.modelCache, params || {});
        }
        wireEmit('beforeRequest', comp, action, finalParams);
        var body = 'wire_body=' + encodeURIComponent(JSON.stringify({
            snapshot: comp.snapshot, action: action, params: finalParams || {}, sections: sections
        }));
        var headers = { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Wire-Request': 'true' };
        var csrf = getCookie('XSRF-TOKEN');
        if (csrf) headers['X-XSRF-TOKEN'] = decodeURIComponent(csrf);
        fetch(updateUrl, {
            method: 'POST', headers: headers, body: body,
            credentials: 'same-origin', redirect: 'manual'
        }).then(function (resp) {
            if (!resp.ok) throw new Error('Wire 请求失败: ' + resp.status);
            return resp.json();
        }).then(function (data) {
            wireEmit('afterRequest', comp, data);
            handleResponse(comp, data);
        }).catch(function (e) {
            console.error('Wire 错误:', e);
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
        // 重渲染后 DOM 已反映服务端最新状态,据此重建 modelCache,
        // 使缓存始终等于页面实际值,避免把上一次未提交的编辑泄漏到后续 action。
        resyncModelCache(comp);
        if (data.effects) {
            var r = data.effects.redirect;
            if (r) { var url = typeof r === 'string' ? r : r.url; window.location.href = url; }
            if (data.effects.dispatch) {
                for (var i = 0; i < data.effects.dispatch.length; i++) {
                    window.dispatchEvent(new CustomEvent(data.effects.dispatch[i].name, { detail: data.effects.dispatch[i].data }));
                }
            }
            // 挂载临时组件
            if (data.effects.components) mountComponents(data.effects.components);
        }
        if (data.error) { console.error('Wire error:', data.error.message); }
        wireEmit('afterUpdate', comp, data, data.sections || {});
    }

    /**
     * 挂载临时组件:在 document.body 末尾追加组件 HTML。
     * 每个组件自动获得唯一 id,由组件自身的 JS 负责清理(如 toast 2.5s 后消失)。
     */
    function mountComponents(components) {
        if (!components || !components.length) return;
        var container = document.getElementById('wire-components-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'wire-components-container';
            container.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:10000;';
            container.style.pointerEvents = 'none';
            document.body.appendChild(container);
        }
        for (var i = 0; i < components.length; i++) {
            var comp = components[i];
            var wrap = document.createElement('div');
            wrap.innerHTML = comp.html || '';
            // 收集并激活所有 script（innerHTML 插入的 script 不会自动执行）
            var scripts = wrap.querySelectorAll('script');
            var scriptTexts = [];
            for (var s = 0; s < scripts.length; s++) {
                var old = scripts[s];
                var type = (old.getAttribute('type') || '').toLowerCase();
                if (type === 'application/json' || old.hasAttribute('wire:config')) continue;
                if (old.getAttribute('src')) {
                    var fresh = document.createElement('script');
                    for (var a = 0; a < old.attributes.length; a++) fresh.setAttribute(old.attributes[a].name, old.attributes[a].value);
                    fresh.type = 'application/javascript';
                    old.parentNode.replaceChild(fresh, old);
                } else if (old.textContent && old.textContent.trim()) {
                    scriptTexts.push(old.textContent);
                    old.parentNode.removeChild(old);
                }
            }
            var el = wrap.firstElementChild || wrap;
            el.style.pointerEvents = 'auto';
            container.appendChild(el);
            // 把下发的组件初始化为活动 Wire 组件:找到内嵌的 wire:config,使其成为带
            // wire:model / wire:submit 绑定的独立组件(作用域限定在对话框自身,见 initComponent)。
            var cfg = el.querySelector('[wire\\:config]') || el.querySelector('script[wire\\:config]');
            if (cfg) {
                var known = false;
                for (var c = 0; c < Wire.components.length; c++) {
                    if (Wire.components[c].configElement === cfg) { known = true; break; }
                }
                if (!known) initComponent(cfg);
            }
            // 执行内联脚本
            for (var t = 0; t < scriptTexts.length; t++) {
                try { (function (body) { eval(body); })(scriptTexts[t]); } catch (e) { console.error('[Wire] 组件脚本执行失败:', e); }
            }
        }
    }

    function replaceSection(comp, sectionName, html) {
        var cleanContent = html.replace(/<!--wire:section-start:[\s\S]+?-->/g, '').replace(/<!--wire:section-end:[\s\S]+?-->/g, '');
        var sectionEl = document.documentElement.querySelector('[wire\\:section="' + sectionName + '"]');
        if (sectionEl) { sectionEl.innerHTML = cleanContent; rebindSection(comp, sectionEl); return; }
        var start = findComment(document.documentElement, 'wire:section-start:' + sectionName);
        var end = findComment(document.documentElement, 'wire:section-end:' + sectionName);
        if (start && end) {
            var toRemove = [], node = start.nextSibling;
            while (node && node !== end) { toRemove.push(node); node = node.nextSibling; }
            var parent = start.parentNode;
            for (var i = 0; i < toRemove.length; i++) parent.removeChild(toRemove[i]);
            var tmpl = document.createElement('template'); tmpl.innerHTML = cleanContent;
            parent.insertBefore(tmpl.content, end);
            activateScriptsInContext(parent);
            rebindSection(comp, parent);
            return;
        }
    }

    function findComment(root, text) {
        var walker = root ? document.createTreeWalker(root, NodeFilter.SHOW_COMMENT, null, null) : null;
        if (!walker) return null;
        var comment;
        while ((comment = walker.nextNode())) { if (comment.nodeValue === text) return comment; }
        return null;
    }

    function activateScriptsInContext(root) {
        if (!root || !root.querySelectorAll) return;
        var scripts = root.querySelectorAll('script');
        for (var i = 0; i < scripts.length; i++) {
            var old = scripts[i];
            var type = (old.getAttribute('type') || '').toLowerCase();
            var isDataOnly = old.hasAttribute('wire:config') || old.hasAttribute('wire:snapshot')
                || type === 'application/json';
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
            try { (function (body) { eval(body); })(text); } catch (e) { console.error('[Wire] 脚本执行失败:', e); }
            if (old.parentNode) old.parentNode.removeChild(old);
        }
    }

    function rebindSection(comp, sectionEl) { bindEvents(comp); }

    function getInputValue(input) {
        var tag = (input.tagName || '').toLowerCase();
        if (tag === 'input') {
            var t = (input.getAttribute('type') || '').toLowerCase();
            if (t === 'checkbox') return input.checked;
            return input.value;
        }
        if (tag === 'select') return input.value;
        return input.value;
    }
    function collectParams(el) {
        var params = {};
        if (!el || !el.attributes) return params;
        for (var i = 0; i < el.attributes.length; i++) {
            var attr = el.attributes[i];
            if (attr.name.indexOf('wire:param-') === 0) {
                params[attr.name.substring(11)] = attr.value;
            }
        }
        return params;
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

    function wireInit() { cleanHeadWireMarkers(); Wire.scan(); }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { wireInit(); });
    } else {
        wireInit();
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