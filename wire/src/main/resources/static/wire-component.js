/**
 * Wire Component Runtime — 命名组件（toast / confirm / 引导浮层等）前端运行时
 *
 * 设计要点：
 *  - 首屏：后端在 HTML 注入 <script type="application/json" wire:components data-wire-outlet="..."> 引导数据，
 *          本运行时在 DOMContentLoaded 时扫描并挂载。
 *  - 更新：Wire 响应 effects.components 由 wire.js 委派 mountAll，开发者无感。
 *  - 隔离：每个实例用 new Function 独立求值生命周期脚本，得到独立闭包（同名组件多开互不覆盖）。
 *  - 生命周期：onCreate(el, wire) → 插入 DOM → onStart(el, wire) → [wire.stop()] → onStop(el, wire) → 移除 DOM → onDestroy(el, wire)
 *  - wire.stop()：模板内主动调用表示“展示完成，移除我”。onStop 可返回 number(ms) 或 Promise 以延后移除（退出动画）。
 *  - 零外部依赖，自包含。
 */
(function () {
    'use strict';

    /** id -> instance，用于按 id 停止 / 防重复挂载 */
    var instances = {};
    var inited = false;

    /**
     * 解析生命周期脚本源码为 {onCreate,onStart,onStop,onDestroy} 对象。
     * 用 new Function 逐实例求值，得到独立闭包 —— 这是组件间隔离的关键。
     */
    function parseLifecycle(scriptSrc) {
        if (!scriptSrc) return {};
        try {
            var factory = new Function(
                scriptSrc + '\n;' +
                'return {' +
                '  onCreate: typeof onCreate === "function" ? onCreate : null,' +
                '  onStart: typeof onStart === "function" ? onStart : null,' +
                '  onStop: typeof onStop === "function" ? onStop : null,' +
                '  onDestroy: typeof onDestroy === "function" ? onDestroy : null' +
                '};'
            );
            return factory() || {};
        } catch (e) {
            console.error('[WireComponent] 生命周期脚本解析失败:', e);
            return {};
        }
    }

    /** 找到 outlet 容器（按显式 id / wire:outlet 属性 / 默认 id 兜底） */
    function getOutlet(outletId) {
        if (outletId) {
            var byId = document.getElementById(outletId);
            if (byId) return byId;
        }
        return document.querySelector('[wire\\:outlet]') || document.getElementById('wire-outlet');
    }

    /** 触发一次生命周期回调，异常不中断其它流程 */
    function callLife(inst, name, el, wire) {
        var fn = inst.api[name];
        if (typeof fn === 'function') {
            try {
                return fn(el, wire);
            } catch (e) {
                console.error('[WireComponent] ' + inst.name + '.' + name + ' 执行异常', e);
            }
        }
        return undefined;
    }

    /** 挂载单个组件实例 */
    function mount(payload, outletId) {
        if (!payload || !payload.id) {
            console.warn('[WireComponent] 跳过无效载荷', payload);
            return null;
        }
        if (instances[payload.id]) {
            return instances[payload.id]; // 防重复挂载
        }
        var outlet = getOutlet(outletId || payload.outlet);
        if (!outlet) {
            console.error('[WireComponent] 找不到 outlet 容器，无法挂载组件 [' + payload.name + ']');
            return null;
        }

        // 1) 由 html 构建 DOM（innerHTML 的 <script> 不会执行，因此生命周期已拆到 script 字段）
        var wrap = document.createElement('div');
        wrap.innerHTML = payload.html || '';
        var el = wrap.firstElementChild || wrap;

        // 2) 实例隔离：每实例独立闭包
        var api = parseLifecycle(payload.script);
        var inst = {
            id: payload.id,
            name: payload.name,
            el: el,
            params: payload.params || {},
            api: api,
            removing: false,
            wire: null
        };
        var wire = {
            id: payload.id,
            name: payload.name,
            params: inst.params,
            el: el,
            stop: function () { stop(inst); }
        };
        inst.wire = wire;
        instances[payload.id] = inst;

        // 3) onCreate：内容已从服务端取回、尚未插入 DOM
        callLife(inst, 'onCreate', el, wire);

        // 4) 插入 DOM
        outlet.appendChild(el);

        // 5) onStart：已插入 DOM 且其余初始化完成
        callLife(inst, 'onStart', el, wire);

        return inst;
    }

    /** 移除一个实例：onStop → 移除 DOM → onDestroy（onStop 可返回 ms / Promise 延后移除） */
    function stop(inst) {
        if (!inst || inst.removing) return;
        inst.removing = true;

        var ret = callLife(inst, 'onStop', inst.el, inst.wire);
        var finish = function () {
            if (inst.el && inst.el.parentNode) {
                inst.el.parentNode.removeChild(inst.el);
            }
            callLife(inst, 'onDestroy', inst.el, inst.wire);
            delete instances[inst.id];
        };

        if (typeof ret === 'number') {
            setTimeout(finish, ret);
        } else if (ret && typeof ret.then === 'function') {
            ret.then(finish, finish);
        } else {
            finish();
        }
    }

    /** 批量挂载（首屏 bootstrap / Wire 更新 effects 通用入口） */
    function mountAll(payloads, outletId) {
        if (!payloads || !payloads.length) return;
        for (var i = 0; i < payloads.length; i++) {
            mount(payloads[i], outletId);
        }
    }

    /** 扫描首屏 <script type="application/json" wire:components> 引导数据并挂载 */
    function mountBootstrapTags() {
        var tags = document.querySelectorAll('script[type="application/json"][wire\\:components]');
        for (var i = 0; i < tags.length; i++) {
            var tag = tags[i];
            var outletId = tag.getAttribute('data-wire-outlet') || 'wire-outlet';
            var list = [];
            try {
                list = JSON.parse(tag.textContent || '[]');
            } catch (e) {
                console.error('[WireComponent] 首屏引导数据解析失败', e);
            }
            mountAll(list, outletId);
            // 移除标签，避免重复挂载
            if (tag.parentNode) tag.parentNode.removeChild(tag);
        }
    }

    function init() {
        if (inited) return;
        inited = true;
        mountBootstrapTags();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // ===== 公开 API =====
    window.WireComponent = {
        mount: mount,
        mountAll: mountAll,
        stop: function (id) { stop(instances[id]); },
        init: init,
        version: '1.0'
    };
})();
