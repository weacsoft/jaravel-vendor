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

        // 收集所有 wire:config / wire:snapshot 标记的配置节点。
        // 注意：用属性选择器 [wire\:config] 查询 <script> 标签在部分浏览器/解析引擎下
        // 不可靠（<script> 尤其 type="application/json" 时），因此这里优先尝试选择器，
        // 失败（或为空）时退化为遍历所有 <script> 标签按属性判定，确保组件一定能初始化。
        var configs = [];
        try {
            var bySelector = document.querySelectorAll('[wire\\:config]');
            for (var s = 0; s < bySelector.length; s++) configs.push(bySelector[s]);
        } catch (e) { /* 选择器不被支持时忽略 */ }
        if (configs.length === 0) {
            var scripts = document.querySelectorAll('script');
            for (var i = 0; i < scripts.length; i++) {
                var sc = scripts[i];
                if (sc.hasAttribute('wire:config') || sc.hasAttribute('wire:snapshot')) {
                    configs.push(sc);
                }
            }
        }
        for (var j = 0; j < configs.length; j++) {
            initComponent(configs[j]);
        }

        // 第4点：声明式懒加载 wire:lazy —— 页面 load 后自动拉取标记了 lazy 的 section，
        // 无需在模板里手写 if/else 或额外的 <script>。
        initLazy();
    }

    /**
     * 声明式懒加载（Laravel Livewire lazy 风格）：
     * 模板用 <div wire:section="x" wire:lazy> 标记后，首次渲染由后端给出占位（如 spinner），
     * 前端在页面 load 后对该 section 发一次 $refresh（等价于 Wire.refresh(['x'])），
     * 后端从权威数据源（DB/慢接口）取真实数据返回。全程后端无需 if/else 分支。
     */
    function initLazy() {
        var lazyEls = document.querySelectorAll('[wire\\:lazy]');
        if (!lazyEls.length) return;
        function fire() {
            for (var i = 0; i < lazyEls.length; i++) {
                var name = lazyEls[i].getAttribute('wire:section');
                if (name) {
                    Wire.refresh([name]);
                }
            }
        }
        if (document.readyState === 'complete') {
            fire();
        } else {
            window.addEventListener('load', fire);
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

    /**
     * 向上查找最近的、带有指定 wire: 属性的祖先（含自身）。
     */
    function closestAttr(el, attrName) {
        var node = el;
        while (node && node !== document) {
            if (node.getAttribute && node.getAttribute(attrName) !== null) {
                return node;
            }
            node = node.parentNode;
        }
        return null;
    }

    /** 读取 cookie 值（用于 CSRF token 自动注入）。 */
    function getCookie(name) {
        var match = document.cookie.match(new RegExp('(?:^|; )' + name.replace(/([.$?*|{}()\[\]\\/+^])/g, '\\$1') + '=([^;]*)'));
        return match ? match[1] : null;
    }

    /** SPA 导航：高亮当前菜单项（移除同组其它项的 active 类，给当前项加 active）。 */
    function highlightNav(el) {
        try {
            var group = closestAttr(el, 'wire:nav-menu') || el.parentNode;
            if (!group) return;
            var items = group.querySelectorAll('[wire\\:nav]');
            for (var i = 0; i < items.length; i++) {
                items[i].classList.remove('wire-nav-active');
            }
            el.classList.add('wire-nav-active');
        } catch (e) { /* 忽略高亮异常，不影响功能 */ }
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
        bindPagination(component);
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
        var elements = component.element.querySelectorAll('[wire\\:click], [wire\\:nav]');
        for (var i = 0; i < elements.length; i++) {
            (function (el) {
                if (!markBound(component, el)) return;
                el.addEventListener('click', function (e) {
                    // SPA 导航拦截（第3点）：wire:nav="pageKey" 菜单项，只刷新右侧内容 section，不整页跳转。
                    var navKey = el.getAttribute('wire:nav');
                    if (navKey) {
                        e.preventDefault();
                        // 找到本组件内承载内容的 section（默认 content），只刷新它
                        var navTarget = closestAttr(el, 'wire:nav-content');
                        var targetName = navTarget
                            ? (navTarget.getAttribute('wire:section') || 'content')
                            : 'content';
                        // 约定动作 $nav，参数 page=目标页；复用统一请求通道
                        sendRequest(component, '$nav', { page: navKey }, el, [targetName]);
                        // 高亮当前菜单项
                        highlightNav(el);
                        return;
                    }

                    e.preventDefault();
                    var action = el.getAttribute('wire:click');
                    var params = collectParams(el);
                    sendRequest(component, action, params, el);
                });
            })(elements[i]);
        }
    }

    /**
     * 分页器拦截：为 [wire:pagination] 容器内的 a[href*="?page=N"] 绑定点击拦截，
     * 阻止浏览器整页跳转，改为发 $paginate 请求并只精准刷新目标 section。
     * 注意：分页链接是 <a> 标签、不带 wire:click/wire:nav，因此必须由本函数单独绑定，
     * 不能放进 bindClick（bindClick 只处理 wire:click/wire:nav 元素）。
     */
    function bindPagination(component) {
        var containers = component.element.querySelectorAll('[wire\\:pagination]');
        for (var c = 0; c < containers.length; c++) {
            (function (container) {
                var links = container.querySelectorAll('a[href]');
                for (var i = 0; i < links.length; i++) {
                    (function (el) {
                        if (!markBound(component, el)) return;
                        el.addEventListener('click', function (e) {
                            var href = el.getAttribute('href') || '';
                            var m = href.match(/[?&]page=(\d+)/);
                            if (!m) return; // 非分页链接，放行默认行为
                            e.preventDefault();
                            var target = container.getAttribute('wire:target') || '';
                            // 分页字段统一用 pageNum（与导航字段 page 解耦，避免冲突）
                            var pparams = { pageNum: parseInt(m[1], 10) };
                            var perMatch = href.match(/[?&]perPage=(\d+)/);
                            if (perMatch) pparams.perPage = parseInt(perMatch[1], 10);
                            // 复用统一请求通道：约定动作 $paginate（后端仅读取 pageNum/perPage 并重渲染）
                            sendRequest(component, '$paginate', pparams, el, target ? [target] : null);
                        });
                    })(links[i]);
                }
            })(containers[c]);
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

                // 行内输入框（位于 [data-wire-key] 行内）属于「每行独立数据」，
                // 不能把值同步到组件级同名字段（否则会污染新增表单的同名字段、
                // 并导致 restoreFocus 把焦点跳到页面上第一个同名输入框）。
                // 这类输入框只做「就地编辑」，值由 collectParams 在点击行内按钮时按行收集。
                if (closestAttr(input, 'data-wire-key') || closestAttr(input, 'wire:key')) {
                    input.setAttribute('data-wire-row-scoped', '1');
                    return;
                }

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

    function sendRequest(component, action, params, triggerEl, targetSections) {
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

        // 收集要更新的 section 列表（允许调用方显式覆盖，用于 Wire.refresh / 分页器精准刷新）
        var sections;
        if (targetSections && targetSections.length) {
            sections = targetSections;
        } else {
            sections = getTargetSections(component, triggerEl);
            if (sections.length === 0) {
                sections = getAllSections(component);
            }
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
        var headers = {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-Wire-Request': 'true'
        };
        // CSRF：从 XSRF-TOKEN cookie 读取并放入 X-XSRF-TOKEN header（Laravel/Axios 标准做法）
        var csrf = getCookie('XSRF-TOKEN');
        if (csrf) {
            headers['X-XSRF-TOKEN'] = decodeURIComponent(csrf);
        }
        fetch(updateUrl, {
            method: 'POST',
            headers: headers,
            body: body,
            credentials: 'same-origin', // 显式声明发送同源 Cookie（JSESSIONID/XSRF-TOKEN），避免某些浏览器默认不携带
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
            // 情况3: 419 CSRF token 过期 — 服务端已刷新 XSRF-TOKEN cookie，
            // 重新加载页面即可获取新 token，用户无感知（对齐 Laravel 419 Page Expired）
            if (response.status === 419) {
                window.location.reload();
                throw new Error('CSRF_EXPIRED');
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
            // CSRF_EXPIRED 是 token 过期，已经在上面处理了页面刷新，不需要额外日志
            if (error.message !== 'AUTH_EXPIRED' && error.message !== 'CSRF_EXPIRED') {
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
            // 命名组件（toast / confirm 等）：委托给 wire-component.js 无感挂载
            if (data.effects.components && data.effects.components.length > 0) {
                if (window.WireComponent && typeof window.WireComponent.mountAll === 'function') {
                    window.WireComponent.mountAll(data.effects.components);
                } else {
                    console.warn('[Wire] 收到 effects.components 但前端运行时 wire-component.js 未加载');
                }
            }
        }

        // 触发 afterUpdate 事件（DOM 更新完成后）
        // 适用于 mdui 等框架在 DOM 更新后需要重新初始化组件的场景
        emit('afterUpdate', component, data, data.sections || {});
    }

    // ===== 工具方法 =====

    /** 读取单个 wire:model / wire:model.* 输入元素的当前值。 */
    function getInputValue(input) {
        var tag = input.tagName ? input.tagName.toLowerCase() : '';
        if (tag === 'input') {
            var type = (input.getAttribute('type') || '').toLowerCase();
            if (type === 'checkbox') return input.checked;
            if (type === 'radio') return input.checked ? input.value : '';
            return input.value;
        }
        if (tag === 'select') {
            if (input.type === 'select-multiple') {
                var values = [];
                for (var i = 0; i < input.selectedOptions.length; i++) {
                    values.push(input.selectedOptions[i].value);
                }
                return values;
            }
            return input.value;
        }
        if (tag === 'textarea') return input.value;
        return input.value;
    }

    /**
     * 收集元素的 wire:param-* 字面量参数。
     * 此外：若触发元素位于某个 [data-wire-key] 行内（列表场景），则额外收集该行内
     * 所有 wire:model.* 输入框的【当前值】，作为同名参数传入。
     * 这样列表内的「改名/勾选」按钮点击时，能拿到用户实时输入，而不是服务端旧值。
     */
    function collectParams(el) {
        var params = {};
        if (!el || !el.attributes) return params;
        for (var i = 0; i < el.attributes.length; i++) {
            var attr = el.attributes[i];
            if (attr.name.indexOf('wire:param-') === 0) {
                var key = attr.name.substring(11);
                var raw = attr.value;
                if (raw === '') raw = '1';
                else if (raw === 'true') raw = true;
                else if (raw === 'false') raw = false;
                params[key] = raw;
            }
        }
        // 行级收集：找到触发元素所在的数据行，把行内 wire:model 的当前值收集进来
        var row = closestAttr(el, 'data-wire-key') || closestAttr(el, 'wire:key');
        if (row) {
            var models = row.querySelectorAll('[wire\\:model]');
            for (var j = 0; j < models.length; j++) {
                var mk = (models[j].getAttribute('wire:model') || '').split('.')[0];
                if (mk && !(mk in params)) {
                    params[mk] = getInputValue(models[j]);
                }
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
            // 列表交互稳定性（第2点）：若子节点带 data-wire-key，做基于 key 的最小化 diff，
            // 复用 DOM 以保留输入框/勾选框等交互状态，避免整段 innerHTML 替换丢失状态。
            if (hasKeyedChildren(sectionEl, html)) {
                var focusInfoK = saveFocus(sectionEl);
                replaceSectionKeyed(sectionEl, html);
                restoreFocus(focusInfoK);
                rebindSection(component, sectionEl);
                return;
            }
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

    /**
     * 判断 section 容器是否应使用 keyed diff：新内容里存在带 data-wire-key 的元素，
     * 且容器内也有带 data-wire-key 的直接子节点（说明是带交互状态的列表）。
     */
    function hasKeyedChildren(sectionEl, html) {
        // 只有当「新旧内容里 data-wire-key 元素的父容器路径一致」时才可做 keyed diff。
        // 注意：绝不能用 `[data-wire-key]`（任意层级）来判断，
        // 因为 keyed diff 只处理 direct children；若 key 元素其实深藏在
        // div > table > tbody > tr 里，section 的直接子节点全都没有 key，
        // 就会全部走 appendChild 分支 —— 表现为「原内容没被替换，又多出一份新的」。
        return findKeyedContainerPair(sectionEl, html) !== null;
    }

    /**
     * 找到「承载 data-wire-key 直接子节点」的容器在新旧 DOM 中的对应关系。
     * 返回 { oldContainer, newContainer, newRoot } 或 null（表示不适合 keyed diff）。
     */
    function findKeyedContainerPair(sectionEl, html) {
        var tmp = document.createElement('div');
        tmp.innerHTML = html;

        var newKeyed = tmp.querySelector('[data-wire-key]');
        if (!newKeyed || !newKeyed.parentNode) return null;

        var newContainer = newKeyed.parentNode;

        // 计算 newContainer 相对 tmp 根的索引路径
        var path = [];
        var cur = newContainer;
        while (cur && cur !== tmp) {
            var parent = cur.parentNode;
            if (!parent) return null;
            path.unshift(Array.prototype.indexOf.call(parent.children, cur));
            cur = parent;
        }
        if (cur !== tmp) return null;

        // 用同样的路径在旧 DOM（sectionEl）里定位容器
        var oldContainer = sectionEl;
        for (var i = 0; i < path.length; i++) {
            var idx = path[i];
            if (!oldContainer.children || idx >= oldContainer.children.length) return null;
            oldContainer = oldContainer.children[idx];
        }

        // 旧容器必须确实含有带 key 的直接子节点，否则没有可复用的状态，
        // 直接走 innerHTML 整体替换更安全。
        if (!oldContainer.querySelector) return null;
        var hasOldKeyedChild = false;
        for (var c = 0; c < oldContainer.children.length; c++) {
            if (oldContainer.children[c].getAttribute &&
                oldContainer.children[c].getAttribute('data-wire-key')) {
                hasOldKeyedChild = true;
                break;
            }
        }
        if (!hasOldKeyedChild) return null;

        // 标签名一致才认为是同一个容器
        if (oldContainer.tagName !== newContainer.tagName) return null;

        return { oldContainer: oldContainer, newContainer: newContainer, newRoot: tmp };
    }

    /**
     * 基于 data-wire-key 的精确 diff 替换：
     * - key 相同的节点：复用旧 DOM（保留交互状态），仅更新其属性与子文本
     * - 新内容里多出的 key：新增到正确位置
     * - 旧内容里消失的 key：删除节点
     */
    function replaceSectionKeyed(sectionEl, html) {
        var pair = findKeyedContainerPair(sectionEl, html);
        if (!pair) {
            // 兜底：结构对不上就整体替换，绝不能走「逐个 append」，否则会重复出现旧内容。
            sectionEl.innerHTML = html;
            return;
        }

        var oldContainer = pair.oldContainer;
        var newContainer = pair.newContainer;

        // 1) 先把 keyed 行从新内容中「摘出来」，把 section 里除行容器以外的部分整体更新。
        //    这样分页器、标题（共 N 项 / 第几页）等非 keyed 内容才会真正刷新，
        //    而不是保持旧值或被追加一份。
        var keptRows = {};
        var oldRows = Array.prototype.slice.call(oldContainer.children);
        for (var i = 0; i < oldRows.length; i++) {
            var k = oldRows[i].getAttribute && oldRows[i].getAttribute('data-wire-key');
            if (k) keptRows[k] = oldRows[i];
        }

        // 记录行容器在 section 中的位置路径，替换后重新定位
        var pathFromSection = [];
        var cur = oldContainer;
        while (cur && cur !== sectionEl) {
            var p = cur.parentNode;
            if (!p) break;
            pathFromSection.unshift(Array.prototype.indexOf.call(p.children, cur));
            cur = p;
        }

        // 用新 HTML 整体替换 section（非 keyed 部分随之刷新）
        sectionEl.innerHTML = html;

        // 重新定位到新的行容器
        var freshContainer = sectionEl;
        for (var s = 0; s < pathFromSection.length; s++) {
            if (!freshContainer.children || pathFromSection[s] >= freshContainer.children.length) {
                freshContainer = null;
                break;
            }
            freshContainer = freshContainer.children[pathFromSection[s]];
        }
        if (!freshContainer) return;

        // 2) 对行做 key 级别复用：把旧行的交互状态（输入值/勾选）迁移到新行上。
        //    这里复用「状态」而不是复用「DOM 节点」，可以天然保证顺序与数量都以服务端为准，
        //    不会出现重复行。
        var freshRows = Array.prototype.slice.call(freshContainer.children);
        for (var r = 0; r < freshRows.length; r++) {
            var nk = freshRows[r].getAttribute && freshRows[r].getAttribute('data-wire-key');
            if (!nk) continue;
            var oldRow = keptRows[nk];
            if (oldRow) {
                carryOverRowState(oldRow, freshRows[r]);
            }
        }
    }

    /**
     * 把旧行里「用户正在编辑的状态」迁移到新行对应控件上。
     * 仅在该控件是行内输入（row-scoped）且服务端值未变化时保留用户输入，
     * 避免用旧值覆盖服务端刚更新的权威值（如改名成功后应显示新名字）。
     */
    function carryOverRowState(oldRow, newRow) {
        var oldInputs = oldRow.querySelectorAll('input, textarea, select');
        var newInputs = newRow.querySelectorAll('input, textarea, select');
        if (oldInputs.length !== newInputs.length) return;

        for (var i = 0; i < oldInputs.length; i++) {
            var o = oldInputs[i];
            var n = newInputs[i];
            var type = (n.getAttribute('type') || '').toLowerCase();

            if (type === 'checkbox' || type === 'radio') {
                // 勾选状态以服务端为准（新 DOM 的 checked 属性），不做迁移
                continue;
            }
            if (type === 'file') continue;

            // 服务端值 = 新节点的 value 属性；旧节点 value 属性 = 上一次服务端值
            var oldServerVal = o.getAttribute('value');
            var newServerVal = n.getAttribute('value');
            var userVal = o.value;

            // 用户确实改动过（当前值 != 上次服务端值），且服务端这次没有给出新值时，
            // 才保留用户输入，避免覆盖服务端权威更新。
            if (userVal !== oldServerVal && oldServerVal === newServerVal) {
                n.value = userVal;
            }
        }
    }

    function syncAttributes(oldEl, newEl) {
        // 移除旧的有、新没有的属性（保留 data-wire-key）
        for (var i = 0; i < oldEl.attributes.length; i++) {
            var a = oldEl.attributes[i].name;
            if (a === 'data-wire-key') continue;
            if (newEl.getAttribute(a) === null) oldEl.removeAttribute(a);
        }
        // 设置/更新新的属性值
        for (var j = 0; j < newEl.attributes.length; j++) {
            var name = newEl.attributes[j].name;
            if (name === 'data-wire-key') continue;
            oldEl.setAttribute(name, newEl.attributes[j].value);
        }
        // 表单控件：同步 value/checked 这种 property（attribute 不能反映输入框实时值）。
        // 否则 keyed diff 复用旧节点时，改名/勾选后输入框会显示旧值（还原问题）。
        var tag = oldEl.tagName ? oldEl.tagName.toLowerCase() : '';
        if (tag === 'input') {
            var type = (oldEl.getAttribute('type') || '').toLowerCase();
            if (type === 'checkbox' || type === 'radio') {
                oldEl.checked = !!newEl.checked;
            } else if (type !== 'file') {
                oldEl.value = newEl.value;
            }
        } else if (tag === 'textarea' || tag === 'select') {
            oldEl.value = newEl.value;
        }
    }

    function syncTextNodes(oldEl, newEl) {
        // 仅当子节点结构都为纯文本时同步文本，避免破坏交互子元素
        if (oldEl.childNodes.length === newEl.childNodes.length) {
            for (var i = 0; i < oldEl.childNodes.length; i++) {
                if (oldEl.childNodes[i].nodeType === 3 && newEl.childNodes[i].nodeType === 3) {
                    if (oldEl.childNodes[i].textContent !== newEl.childNodes[i].textContent) {
                        oldEl.childNodes[i].textContent = newEl.childNodes[i].textContent;
                    }
                }
            }
        }
    }

    function findComment(root, text) {        var walker = document.createTreeWalker(root, NodeFilter.SHOW_COMMENT, null, null);
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
        // 先清除触发按钮的 wire:loading 属性（由 showLoading 设置），
        // 避免后续 [wire:loading] 查询误把触发按钮当作加载指示器而 display:none
        var triggerEls = component.element.querySelectorAll('[wire\\:click="' + action + '"], [wire\\:submit="' + action + '"]');
        for (var j = 0; j < triggerEls.length; j++) {
            triggerEls[j].removeAttribute('wire:loading');
        }
        // 再隐藏加载指示器（原始 HTML 中带 wire:loading 的元素）
        var loadingEls = component.element.querySelectorAll('[wire\\:loading]');
        for (var i = 0; i < loadingEls.length; i++) {
            var el = loadingEls[i];
            var target = el.getAttribute('wire:target');
            if (!target || target === action) {
                el.style.display = 'none';
                el.removeAttribute('wire:loading-active');
            }
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
            var rowKey = el.getAttribute('data-wire-key');
            if (el.id) {
                selector += '#' + el.id;
            } else if (rowKey) {
                // 行节点用 key 定位，保证同名字段能定位到「本行」而不是页面第一个同名输入框
                selector += '[data-wire-key="' + rowKey + '"]';
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
        return { path: path, container: container, selectionStart: selectionStart, selectionEnd: selectionEnd };
    }

    function restoreFocus(focusInfo) {
        if (!focusInfo || !focusInfo.path) return;
        try {
            // path 是相对 container 生成的，必须在 container 内查找。
            // 否则同名字段（如列表行和新增表单都用 wire:model="name"）会命中文档里第一个，导致焦点乱跳。
            var scope = (focusInfo.container && focusInfo.container.querySelector)
                ? focusInfo.container
                : document;
            var el = scope.querySelector(focusInfo.path);
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
        // 必须重绑分页：section 更新后分页器 <a> 是全新 DOM，未绑定则会走浏览器默认跳转（整页刷新）
        bindPagination(component);
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

    // ===== 公开 API：精准刷新（第1点 / 第7点懒加载扩展）=====
    /**
     * 手动刷新一个或多个 section。
     * @param {string[]|string} sections  要刷新的 section 名；为空/省略=全部；传 'list' 等=只刷该组件
     * @param {string} [action]           后端 action，默认 '$refresh'（不改动数据，仅重渲染）
     * @param {object} [params]           附加参数（如 {page:2}）
     *
     * 说明：后端不需要为"全页刷新"单独写代码。无论传哪些 section，后端都走同一个
     * update 通道：基于当前快照执行 action，再把指定 section 渲染返回。
     * 使用者已知某组件（如 list）在后端被别人更新时，调用 Wire.refresh(['list']) 即可精准拉取。
     */
    Wire.refresh = function (sections, action, params) {
        var comp = Wire.components[0];
        if (!comp) return;
        var targetSections = null;
        if (typeof sections === 'string') {
            targetSections = sections ? [sections] : null;
        } else if (Array.isArray(sections)) {
            targetSections = sections.length ? sections : null;
        }
        sendRequest(comp, action || '$refresh', params || {}, null, targetSections);
    };

    window.Wire = Wire;
})();
