/**
 * Wire.js — Laravel Livewire 风格的部分更新前端运行时
 * <p>
 * 功能：
 * - 自动扫描 wire: 属性并绑定事件（wire:click, wire:submit, wire:model, wire:change, wire:keydown）
 * - 支持自定义 update URL（wire:update 属性或 data-wire-update 配置）
 * - section 级局部更新（仅替换 [wire:section="name"] 的内容）
 * - wire:model 双向绑定（防抖 150ms，wire:model.lazy 延迟到 blur）
 * - wire:loading 加载状态显示/隐藏
 * - wire:target 指定要更新的 section
 * - 零外部依赖，自包含
 */
(function () {
    'use strict';

    var Wire = {
        components: [],
        debounceTimers: {}
    };

    // ===== 初始化 =====

    function init() {
        var configs = document.querySelectorAll('[wire\\:config]');
        for (var i = 0; i < configs.length; i++) {
            initComponent(configs[i]);
        }
    }

    function initComponent(configEl) {
        var updateUrl = configEl.getAttribute('data-wire-update') || '/wire/update';
        var snapshot = configEl.getAttribute('wire:snapshot') || '';

        var component = {
            element: document.body,
            configElement: configEl,
            updateUrl: updateUrl,
            snapshot: snapshot,
            id: 'wire-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9),
            // 已绑定事件的元素集合，防止重复绑定
            boundElements: new Set()
        };

        Wire.components.push(component);
        bindEvents(component);
    }

    // ===== 事件绑定 =====

    function bindEvents(component) {
        bindClick(component);
        bindSubmit(component);
        bindModel(component);
        bindChange(component);
        bindKeydown(component);
    }

    /**
     * 标记元素为已绑定，返回 true 表示这是第一次绑定（需要绑定），false 表示已绑定过（跳过）。
     */
    function markBound(component, el) {
        if (component.boundElements.has(el)) {
            return false;
        }
        component.boundElements.add(el);
        return true;
    }

    /**
     * 查找所有以 wire: 开头的属性，返回 {name, value, baseName} 列表。
     * baseName 是去掉修饰符的基础名（如 wire:model.live → wire:model）。
     */
    function findWireAttrs(el, prefix) {
        var results = [];
        for (var i = 0; i < el.attributes.length; i++) {
            var attr = el.attributes[i];
            if (attr.name.indexOf(prefix) === 0) {
                results.push({
                    name: attr.name,
                    value: attr.value,
                    baseName: attr.name
                });
            }
        }
        return results;
    }

    function bindClick(component) {
        var elements = component.element.querySelectorAll('[wire\\:click]');
        for (var i = 0; i < elements.length; i++) {
            (function (el) {
                if (!markBound(component, el)) return;
                el.addEventListener('click', function (e) {
                    e.preventDefault();
                    var action = el.getAttribute('wire:click');
                    var params = collectParams(el);
                    sendRequest(component, action, params, el);
                });
            })(elements[i]);
        }
    }

    function bindSubmit(component) {
        var forms = component.element.querySelectorAll('form[wire\\:submit]');
        for (var i = 0; i < forms.length; i++) {
            (function (form) {
                if (!markBound(component, form)) return;
                form.addEventListener('submit', function (e) {
                    e.preventDefault();
                    var action = form.getAttribute('wire:submit');
                    var params = collectFormData(form);
                    sendRequest(component, action, params, form);
                });
            })(forms[i]);
        }
    }

    function bindModel(component) {
        // 遍历所有元素，查找 wire:model 开头的属性
        var allElements = component.element.querySelectorAll('input, textarea, select');
        for (var i = 0; i < allElements.length; i++) {
            (function (input) {
                // 查找 wire:model 或 wire:model.xxx 属性
                var modelAttr = null;
                var modelValue = null;
                for (var j = 0; j < input.attributes.length; j++) {
                    var attr = input.attributes[j];
                    if (attr.name === 'wire:model' || attr.name.indexOf('wire:model.') === 0) {
                        modelAttr = attr.name;
                        modelValue = attr.value;
                        break;
                    }
                }
                if (!modelAttr) return;

                if (!markBound(component, input)) return;

                var field = modelValue;
                var isLazy = modelAttr.indexOf('.lazy') !== -1;
                var isLive = modelAttr.indexOf('.live') !== -1;

                input.setAttribute('data-wire-field', field);
                input.setAttribute('data-wire-model-attr', modelAttr);

                if (isLazy) {
                    input.addEventListener('change', function () {
                        var params = {};
                        params[field] = getInputValue(input);
                        sendRequest(component, '$sync', params, input);
                    });
                } else if (isLive) {
                    input.addEventListener('input', function () {
                        var params = {};
                        params[field] = getInputValue(input);
                        sendRequest(component, '$sync', params, input);
                    });
                } else {
                    input.addEventListener('input', function () {
                        var key = component.id + '-' + field;
                        clearTimeout(Wire.debounceTimers[key]);
                        Wire.debounceTimers[key] = setTimeout(function () {
                            var params = {};
                            params[field] = getInputValue(input);
                            sendRequest(component, '$sync', params, input);
                        }, 150);
                    });
                }
            })(allElements[i]);
        }
    }

    function bindChange(component) {
        var elements = component.element.querySelectorAll('[wire\\:change]');
        for (var i = 0; i < elements.length; i++) {
            (function (el) {
                if (!markBound(component, el)) return;
                el.addEventListener('change', function (e) {
                    var action = el.getAttribute('wire:change');
                    var params = collectParams(el);
                    sendRequest(component, action, params, el);
                });
            })(elements[i]);
        }
    }

    function bindKeydown(component) {
        var elements = component.element.querySelectorAll('[wire\\:keydown]');
        for (var i = 0; i < elements.length; i++) {
            (function (el) {
                if (!markBound(component, el)) return;
                var attr = el.getAttribute('wire:keydown');
                el.addEventListener('keydown', function (e) {
                    var parts = attr.split('.');
                    if (parts.length === 1) {
                        e.preventDefault();
                        var params = collectParams(el);
                        sendRequest(component, parts[0], params, el);
                    } else {
                        var key = parts[0];
                        var modifier = parts[1].toLowerCase();
                        var keyMap = {
                            'enter': 'Enter', 'escape': 'Escape', 'tab': 'Tab',
                            'space': ' ', 'arrowup': 'ArrowUp', 'arrowdown': 'ArrowDown'
                        };
                        if (e.key === (keyMap[modifier] || modifier)) {
                            e.preventDefault();
                            var params = collectParams(el);
                            sendRequest(component, key, params, el);
                        }
                    }
                });
            })(elements[i]);
        }
    }

    // ===== 请求发送 =====

    // 防止并发请求：记录正在进行的请求
    var pendingRequests = {};

    function sendRequest(component, action, params, triggerEl) {
        var isSync = action === '$sync';

        // 解析 update URL：优先使用元素上的 wire:update 覆盖
        var updateUrl = component.updateUrl;
        var el = triggerEl;
        while (el && el !== document) {
            if (el.hasAttribute && el.hasAttribute('wire:update')) {
                updateUrl = el.getAttribute('wire:update');
                break;
            }
            el = el.parentElement;
        }

        // 收集要更新的 section 列表
        var sections = getTargetSections(component, triggerEl);
        if (sections.length === 0) {
            sections = getAllSections(component);
        }

        // 对于 $sync 请求，在发送前保存输入框状态（DOM 替换后需要恢复）
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

        // 显示 loading
        showLoading(component, action);

        // 构建请求体
        var wireData = JSON.stringify({
            snapshot: component.snapshot,
            action: action,
            params: params || {},
            sections: sections
        });
        var body = 'wire_body=' + encodeURIComponent(wireData);

        // 发送请求
        fetch(updateUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Wire-Request': 'true'
            },
            body: body,
            redirect: 'manual' // 不自动跟随重定向，由我们手动处理（用于检测 302 登录跳转）
        }).then(function (response) {
            // 情况1: 401 未认证 — 中间件拦截，返回 JSON {message, redirect}
            if (response.status === 401) {
                return response.json().then(function (errData) {
                    var loginUrl = (errData && errData.redirect) || '/login';
                    redirectToLogin(loginUrl);
                    throw new Error('AUTH_EXPIRED');
                }).catch(function (e) {
                    if (e.message === 'AUTH_EXPIRED') throw e;
                    // JSON 解析失败，直接跳登录
                    redirectToLogin('/login');
                    throw new Error('AUTH_EXPIRED');
                });
            }
            // 情况2: 302 重定向 — 非 API 路径的中间件返回重定向（fetch manual 模式下 response.type === 'opaqueredirect'）
            if (response.status === 0 || response.type === 'opaqueredirect') {
                redirectToLogin('/login');
                throw new Error('AUTH_EXPIRED');
            }
            if (!response.ok) {
                throw new Error('Wire 请求失败: ' + response.status);
            }
            return response.json();
        }).then(function (data) {
            handleResponse(component, data);
            hideLoading(component, action);

            // 对于 $sync 请求，恢复输入框的焦点和光标位置
            if (inputState && inputState.field && inputState.modelAttr) {
                // 精确查找触发 $sync 的那个输入框（通过 modelAttr 区分 wire:model 和 wire:model.live）
                var newEl = findModelInput(component.element, inputState.field, inputState.modelAttr);
                if (newEl) {
                    // 服务端返回的 HTML 已包含 value="{{$message}}"，不需要手动设置值
                    // 只恢复焦点和光标位置
                    if (inputState.isFocused) {
                        newEl.focus();
                        var len = newEl.value ? newEl.value.length : 0;
                        var pos = inputState.selectionStart !== null ? inputState.selectionStart : len;
                        try { newEl.setSelectionRange(pos, pos); } catch(e) {}
                    }
                }
            }
        }).catch(function (error) {
            // AUTH_EXPIRED 是认证过期，已经在上面处理了重定向，不需要额外日志
            if (error.message !== 'AUTH_EXPIRED') {
                console.error('Wire 错误:', error);
            }
            hideLoading(component, action);
        });
    }

    function handleResponse(component, data) {
        // 更新 snapshot
        if (data.snapshot) {
            component.snapshot = data.snapshot;
            if (component.configElement) {
                component.configElement.setAttribute('wire:snapshot', data.snapshot);
            }
        }

        // 替换 section 内容
        if (data.sections) {
            for (var sectionName in data.sections) {
                if (data.sections.hasOwnProperty(sectionName)) {
                    replaceSection(component, sectionName, data.sections[sectionName]);
                }
            }
        }

        // 处理 effects
        if (data.effects) {
            if (data.effects.redirect) {
                var redirect = data.effects.redirect;
                // 兼容两种格式：
                //   字符串: "redirect": "/login"
                //   对象:   "redirect": {"url": "/login", "delay": 1500}
                var redirectUrl, redirectDelay;
                if (typeof redirect === 'string') {
                    redirectUrl = redirect;
                    redirectDelay = 0;
                } else {
                    redirectUrl = redirect.url;
                    redirectDelay = redirect.delay || 0;
                }
                if (redirectDelay > 0) {
                    setTimeout(function() {
                        window.location.href = redirectUrl;
                    }, redirectDelay);
                } else {
                    window.location.href = redirectUrl;
                }
            }
            if (data.effects.dispatch && data.effects.dispatch.length > 0) {
                for (var i = 0; i < data.effects.dispatch.length; i++) {
                    var event = data.effects.dispatch[i];
                    window.dispatchEvent(new CustomEvent(event.name, { detail: event.data }));
                }
            }
        }
    }

    // ===== 工具方法 =====

    function collectParams(el) {
        var params = {};
        for (var i = 0; i < el.attributes.length; i++) {
            var attr = el.attributes[i];
            if (attr.name.indexOf('wire:param-') === 0) {
                var key = attr.name.substring(12);
                params[key] = attr.value;
            }
        }
        return params;
    }

    function collectFormData(form) {
        var params = {};
        var formData = new FormData(form);
        formData.forEach(function (value, key) {
            params[key] = value;
        });
        return params;
    }

    function getInputValue(input) {
        if (input.type === 'checkbox') {
            return input.checked;
        }
        if (input.type === 'select-multiple') {
            var values = [];
            for (var i = 0; i < input.selectedOptions.length; i++) {
                values.push(input.selectedOptions[i].value);
            }
            return values;
        }
        return input.value;
    }

    function getTargetSections(component, triggerEl) {
        var targetAttr = triggerEl.getAttribute('wire:target');
        if (!targetAttr) {
            var el = triggerEl.parentElement;
            while (el && el !== component.element) {
                if (el.hasAttribute && el.hasAttribute('wire:target')) {
                    targetAttr = el.getAttribute('wire:target');
                    break;
                }
                el = el.parentElement;
            }
        }
        if (targetAttr) {
            return targetAttr.split(',').map(function (s) { return s.trim(); });
        }
        return [];
    }

    function getAllSections(component) {
        var sections = [];
        var sectionEls = component.element.querySelectorAll('[wire\\:section]');
        for (var i = 0; i < sectionEls.length; i++) {
            var name = sectionEls[i].getAttribute('wire:section');
            if (sections.indexOf(name) === -1) {
                sections.push(name);
            }
        }
        var walker = document.createTreeWalker(component.element, NodeFilter.SHOW_COMMENT, null, null);
        var comment;
        while (comment = walker.nextNode()) {
            var text = comment.nodeValue;
            var match = text.match(/^wire:section-start:(.+)$/);
            if (match && sections.indexOf(match[1]) === -1) {
                sections.push(match[1]);
            }
        }
        return sections;
    }

    /**
     * 替换 section 内容。
     * 支持两种标记方式：
     * 1. <div wire:section="name">...</div> — 替换 innerHTML
     * 2. <!--wire:section-start:name-->...<!--wire:section-end:name--> — 替换注释间的所有节点
     */
    function replaceSection(component, sectionName, html) {
        // 方式1: [wire:section] 元素属性
        var sectionEl = component.element.querySelector('[wire\\:section="' + sectionName + '"]');
        if (sectionEl) {
            var focusInfo = saveFocus(sectionEl);
            sectionEl.innerHTML = html;
            restoreFocus(focusInfo);
            rebindSection(component, sectionEl);
            return;
        }

        // 方式2: HTML 注释标记
        var startComment = findComment(component.element, 'wire:section-start:' + sectionName);
        var endComment = findComment(component.element, 'wire:section-end:' + sectionName);
        if (startComment && endComment) {
            // 收集 start 和 end 之间的所有节点
            var nodesToRemove = [];
            var node = startComment.nextSibling;
            while (node && node !== endComment) {
                nodesToRemove.push(node);
                node = node.nextSibling;
            }

            // 保存焦点信息
            var parent = startComment.parentNode;
            var focusInfo = saveFocus(parent);

            // 移除旧节点
            for (var i = 0; i < nodesToRemove.length; i++) {
                parent.removeChild(nodesToRemove[i]);
            }

            // 插入新内容
            var template = document.createElement('template');
            template.innerHTML = html;
            var frag = template.content;
            parent.insertBefore(frag, endComment);

            // 恢复焦点
            restoreFocus(focusInfo);

            // 重新绑定事件
            rebindSection(component, parent);
        }
    }

    function findComment(root, text) {
        var walker = document.createTreeWalker(root, NodeFilter.SHOW_COMMENT, null, null);
        var comment;
        while (comment = walker.nextNode()) {
            if (comment.nodeValue === text) {
                return comment;
            }
        }
        return null;
    }

    /**
     * 精确查找 wire:model 输入框：同时匹配 data-wire-field 和 data-wire-model-attr。
     * 这样可以区分 wire:model="message" 和 wire:model.live="message" 两个不同的输入框。
     */
    function findModelInput(container, field, modelAttr) {
        if (!container || !field || !modelAttr) return null;
        var els = container.querySelectorAll('[data-wire-field="' + field + '"]');
        for (var i = 0; i < els.length; i++) {
            if (els[i].getAttribute('data-wire-model-attr') === modelAttr) {
                return els[i];
            }
        }
        return null;
    }

    function showLoading(component, action) {
        var loadingEls = component.element.querySelectorAll('[wire\\:loading]');
        for (var i = 0; i < loadingEls.length; i++) {
            var el = loadingEls[i];
            var target = el.getAttribute('wire:target');
            if (!target || target === action) {
                el.style.display = '';
                el.setAttribute('wire:loading-active', 'true');
            }
        }
        var triggerEls = component.element.querySelectorAll('[wire\\:click="' + action + '"], [wire\\:submit="' + action + '"]');
        for (var j = 0; j < triggerEls.length; j++) {
            triggerEls[j].setAttribute('wire:loading', 'true');
        }
    }

    function hideLoading(component, action) {
        var loadingEls = component.element.querySelectorAll('[wire\\:loading]');
        for (var i = 0; i < loadingEls.length; i++) {
            var el = loadingEls[i];
            var target = el.getAttribute('wire:target');
            if (!target || target === action) {
                el.style.display = 'none';
                el.removeAttribute('wire:loading-active');
            }
        }
        var triggerEls = component.element.querySelectorAll('[wire\\:click="' + action + '"], [wire\\:submit="' + action + '"]');
        for (var j = 0; j < triggerEls.length; j++) {
            triggerEls[j].removeAttribute('wire:loading');
        }
    }

    function saveFocus(container) {
        var active = document.activeElement;
        if (!active || !container.contains(active)) {
            return null;
        }
        var path = '';
        var el = active;
        while (el && el !== container) {
            var selector = el.tagName.toLowerCase();
            if (el.id) {
                selector += '#' + el.id;
            } else if (el.getAttribute('data-wire-field')) {
                selector += '[data-wire-field="' + el.getAttribute('data-wire-field') + '"]';
            } else if (el.name) {
                selector += '[name="' + el.name + '"]';
            } else {
                var parent = el.parentElement;
                if (parent) {
                    var siblings = parent.children;
                    var index = Array.prototype.indexOf.call(siblings, el);
                    selector += ':nth-child(' + (index + 1) + ')';
                }
            }
            path = path ? selector + ' > ' + path : selector;
            el = el.parentElement;
        }
        var selectionStart = null;
        var selectionEnd = null;
        if (active.type !== 'checkbox' && active.type !== 'radio' && active.selectionStart !== undefined) {
            selectionStart = active.selectionStart;
            selectionEnd = active.selectionEnd;
        }
        return { path: path, selectionStart: selectionStart, selectionEnd: selectionEnd };
    }

    function restoreFocus(focusInfo) {
        if (!focusInfo || !focusInfo.path) return;
        try {
            var el = document.querySelector(focusInfo.path);
            if (el && el.focus) {
                el.focus();
                if (focusInfo.selectionStart !== null && el.setSelectionRange) {
                    el.setSelectionRange(focusInfo.selectionStart, focusInfo.selectionEnd);
                }
            }
        } catch (e) {
        }
    }

    function rebindSection(component, sectionEl) {
        bindClick(component);
        bindSubmit(component);
        bindModel(component);
        bindChange(component);
        bindKeydown(component);
    }

    // ===== 认证过期处理 =====

    /**
     * 重定向到登录页，携带当前页面 URL 作为回跳地址。
     * 用户登录成功后可以回到之前的页面，实现"无感"体验。
     */
    function redirectToLogin(loginUrl) {
        var currentUrl = window.location.href;
        // 避免重复重定向
        if (window.location.pathname === loginUrl) return;
        // 拼接 redirect 参数
        var separator = loginUrl.indexOf('?') !== -1 ? '&' : '?';
        window.location.href = loginUrl + separator + 'redirect=' + encodeURIComponent(currentUrl);
    }

    // ===== 启动 =====

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    window.Wire = Wire;
})();
