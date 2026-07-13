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
 * - Wire.on/off 事件系统（beforeUpdate/afterUpdate），支持 mdui 等框架在 DOM 更新后刷新组件
 * - 零外部依赖，自包含
 */
(function () {
    'use strict';

    var Wire = {
        components: [],
        debounceTimers: {},
        // 全局事件监听器
        _listeners: {}
    };

    /**
     * 注册事件监听器。
     * <p>
     * 支持的事件：
     * <ul>
     *   <li>{@code beforeUpdate} — 发送更新请求前触发，参数：{@code (component, action, params)}</li>
     *   <li>{@code afterUpdate} — DOM 更新完成后触发，参数：{@code (component, data, sections)}</li>
     * </ul>
     * <p>
     * 典型用法（mdui 等框架在 DOM 更新后需要重新初始化组件）：
     * <pre>
     * Wire.on('afterUpdate', function(component, data, sections) {
     *     mdui.mutation();  // 重新扫描并初始化 mdui 组件
     * });
     * </pre>
     *
     * @param {string} event 事件名
     * @param {Function} callback 回调函数
     * @returns {Wire} Wire 对象（链式）
     */
    Wire.on = function(event, callback) {
        if (typeof callback !== 'function') return Wire;
        if (!Wire._listeners[event]) {
            Wire._listeners[event] = [];
        }
        Wire._listeners[event].push(callback);
        return Wire;
    };

    /**
     * 移除事件监听器。
     *
     * @param {string} event 事件名
     * @param {Function} callback 要移除的回调（不传则移除该事件的所有监听器）
     * @returns {Wire} Wire 对象（链式）
     */
    Wire.off = function(event, callback) {
        if (!Wire._listeners[event]) return Wire;
        if (!callback) {
            Wire._listeners[event] = [];
        } else {
            Wire._listeners[event] = Wire._listeners[event].filter(function(fn) {
                return fn !== callback;
            });
        }
        return Wire;
    };

    /**
     * 触发事件，调用所有注册的监听器。
     */
    function emit(event) {
        var listeners = Wire._listeners[event];
        if (!listeners) return;
        var args = Array.prototype.slice.call(arguments, 1);
        for (var i = 0; i < listeners.length; i++) {
            try {
                listeners[i].apply(null, args);
            } catch (e) {
                console.error('[Wire] 事件监听器异常 (' + event + '):', e);
            }
        }
    }

    // ===== 初始化 =====

    function init() {
        // 清理 <head> 中原始文本元素（title/style/script）的 wire 标记
        cleanHeadWireMarkers();

        var configs = document.querySelectorAll('[wire\\:config]');
        for (var i = 0; i < configs.length; i++) {
            initComponent(configs[i]);
        }
    }

    /**
     * 清理 <head> 中原始文本元素的 wire section 标记。
     * <p>
     * HTML 原始文本元素（title, style, script）不解析 HTML 注释，
     * <!--wire:section-start:name--> 会被当作纯文本显示。
     * 此方法在初始化时提取真实内容并设置到对应元素上。
     * 同时处理属性值中的 wire 标记（如 <meta content="@yield('desc')">）。
     */
    function cleanHeadWireMarkers() {
        // 1. 处理原始文本元素：title, style, script
        var rawTextEls = document.querySelectorAll('title, style, script');
        for (var i = 0; i < rawTextEls.length; i++) {
            var el = rawTextEls[i];
            var content = el.textContent || '';
            // 提取所有 wire section 标记中的内容
            var regex = /<!--wire:section-start:([\s\S]+?)-->([\s\S]*?)<!--wire:section-end:\1-->/g;
            var match;
            var cleaned = content;
            while ((match = regex.exec(content)) !== null) {
                cleaned = cleaned.replace(match[0], match[2]);
            }
            if (cleaned !== content) {
                if (el.tagName === 'TITLE') {
                    document.title = cleaned;
                } else {
                    el.textContent = cleaned;
                }
            }
        }

        // 2. 处理属性值中的 wire 标记（如 <meta content="<!--wire:section-start:desc-->val<!--wire:section-end:desc-->">）
        var allEls = document.head ? document.head.querySelectorAll('*') : [];
        for (var j = 0; j < allEls.length; j++) {
            var elem = allEls[j];
            for (var attrIdx = 0; attrIdx < elem.attributes.length; attrIdx++) {
                var attr = elem.attributes[attrIdx];
                var attrVal = attr.value;
                var attrRegex = /<!--wire:section-start:([\s\S]+?)-->([\s\S]*?)<!--wire:section-end:\1-->/g;
                var attrMatch;
                var attrCleaned = attrVal;
                while ((attrMatch = attrRegex.exec(attrVal)) !== null) {
                    attrCleaned = attrCleaned.replace(attrMatch[0], attrMatch[2]);
                }
                if (attrCleaned !== attrVal) {
                    elem.setAttribute(attr.name, attrCleaned);
                }
            }
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

        // 触发 beforeUpdate 事件（发送请求前）
        emit('beforeUpdate', component, action, params);

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

        // 触发 afterUpdate 事件（DOM 更新完成后）
        // 适用于 mdui 等框架在 DOM 更新后需要重新初始化组件的场景
        emit('afterUpdate', component, data, data.sections || {});
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
        // 1. 搜索整个文档中的 [wire:section] 元素（body + head）
        var sectionEls = document.documentElement.querySelectorAll('[wire\\:section]');
        for (var i = 0; i < sectionEls.length; i++) {
            var name = sectionEls[i].getAttribute('wire:section');
            if (sections.indexOf(name) === -1) {
                sections.push(name);
            }
        }
        // 2. 搜索整个文档中的注释标记（body 中的正常注释）
        var walker = document.createTreeWalker(document.documentElement, NodeFilter.SHOW_COMMENT, null, null);
        var comment;
        while (comment = walker.nextNode()) {
            var text = comment.nodeValue;
            var match = text.match(/^wire:section-start:(.+)$/);
            if (match && sections.indexOf(match[1]) === -1) {
                sections.push(match[1]);
            }
        }
        // 3. 搜索原始文本元素（title, style, script）中的文本 wire 标记
        var rawTextEls = document.querySelectorAll('title, style, script');
        for (var r = 0; r < rawTextEls.length; r++) {
            var rawContent = rawTextEls[r].textContent || '';
            var rawRegex = /<!--wire:section-start:([\s\S]+?)-->/g;
            var rawMatch;
            while ((rawMatch = rawRegex.exec(rawContent)) !== null) {
                if (sections.indexOf(rawMatch[1]) === -1) {
                    sections.push(rawMatch[1]);
                }
            }
        }
        // 4. 搜索 head 中元素属性值里的 wire 标记（如 <meta content="@yield('desc')">）
        var headEls = document.head ? document.head.querySelectorAll('*') : [];
        for (var h = 0; h < headEls.length; h++) {
            for (var ha = 0; ha < headEls[h].attributes.length; ha++) {
                var attrVal = headEls[h].attributes[ha].value;
                var attrMatch = attrVal.match(/<!--wire:section-start:([\s\S]+?)-->/);
                if (attrMatch && sections.indexOf(attrMatch[1]) === -1) {
                    sections.push(attrMatch[1]);
                }
            }
        }
        return sections;
    }

    /**
     * 替换 section 内容。
     * 支持四种标记方式：
     * 1. <div wire:section="name">...</div> — 替换 innerHTML（body 或 head）
     * 2. <!--wire:section-start:name-->...<!--wire:section-end:name--> — 替换注释间的所有节点
     * 3. 原始文本元素（title, style, script）中的文本标记 — 替换 textContent
     * 4. 元素属性值中的标记（如 <meta content="...">） — 替换属性值
     */
    function replaceSection(component, sectionName, html) {
        // 提取纯内容（去除 wire 标记）
        var cleanContent = html
            .replace(/<!--wire:section-start:[\s\S]+?-->/g, '')
            .replace(/<!--wire:section-end:[\s\S]+?-->/g, '');

        // 方式1: [wire:section] 元素属性（搜索整个文档）
        var sectionEl = document.documentElement.querySelector('[wire\\:section="' + sectionName + '"]');
        if (sectionEl) {
            var focusInfo = saveFocus(sectionEl);
            sectionEl.innerHTML = html;
            restoreFocus(focusInfo);
            rebindSection(component, sectionEl);
            return;
        }

        // 方式2: HTML 注释标记（搜索整个文档）
        var startComment = findComment(document.documentElement, 'wire:section-start:' + sectionName);
        var endComment = findComment(document.documentElement, 'wire:section-end:' + sectionName);
        if (startComment && endComment) {
            var nodesToRemove = [];
            var node = startComment.nextSibling;
            while (node && node !== endComment) {
                nodesToRemove.push(node);
                node = node.nextSibling;
            }
            var parent = startComment.parentNode;
            var focusInfo2 = saveFocus(parent);
            for (var i = 0; i < nodesToRemove.length; i++) {
                parent.removeChild(nodesToRemove[i]);
            }
            var template = document.createElement('template');
            template.innerHTML = html;
            parent.insertBefore(template.content, endComment);
            restoreFocus(focusInfo2);
            rebindSection(component, parent);
            return;
        }

        // 方式3: 原始文本元素（title, style, script）中的文本标记
        var rawTextEls = document.querySelectorAll('title, style, script');
        for (var r = 0; r < rawTextEls.length; r++) {
            var el = rawTextEls[r];
            var content = el.textContent || '';
            var startMarker = '<!--wire:section-start:' + sectionName + '-->';
            var endMarker = '<!--wire:section-end:' + sectionName + '-->';
            var startIdx = content.indexOf(startMarker);
            if (startIdx >= 0) {
                var endIdx = content.indexOf(endMarker, startIdx);
                if (endIdx >= 0) {
                    if (el.tagName === 'TITLE') {
                        document.title = cleanContent;
                    } else {
                        el.textContent = cleanContent;
                    }
                    return;
                }
            }
        }

        // 方式4: 元素属性值中的标记（如 <meta content="@yield('desc')">）
        var allEls = document.documentElement.querySelectorAll('*');
        for (var a = 0; a < allEls.length; a++) {
            var elem = allEls[a];
            for (var attrIdx = 0; attrIdx < elem.attributes.length; attrIdx++) {
                var attr = elem.attributes[attrIdx];
                var attrVal = attr.value;
                var attrStartMarker = '<!--wire:section-start:' + sectionName + '-->';
                if (attrVal.indexOf(attrStartMarker) >= 0) {
                    var attrEndMarker = '<!--wire:section-end:' + sectionName + '-->';
                    var newAttrVal = attrVal.replace(
                        new RegExp('<!--wire:section-start:' + sectionName + '-->([\\s\\S]*?)<!--wire:section-end:' + sectionName + '-->'),
                        cleanContent
                    );
                    elem.setAttribute(attr.name, newAttrVal);
                    return;
                }
            }
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
