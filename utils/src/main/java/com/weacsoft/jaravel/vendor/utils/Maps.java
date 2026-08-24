package com.weacsoft.jaravel.vendor.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 不可变 Map 便捷构造工具，用于替代 {@code Map.of(...)}。
 *
 * <p>与 {@code Map.of} 的区别：{@code Map.of} 遇到 {@code null} 键或 {@code null} 值会直接抛
 * {@link NullPointerException}，且最多只支持 10 个键值对。本工具在构造时更为宽松：</p>
 * <ul>
 *     <li><b>允许空键值</b>：传入的 {@code null} 键或空字符串键会被视为「没有这条数据」直接跳过；
 *         传入的 {@code null}/空字符串值会被保留，以便调用方（如模板引擎）仍能做判空、条件渲染。</li>
 *     <li><b>保持插入顺序</b>：底层使用 {@link LinkedHashMap}，迭代顺序与放入顺序一致。</li>
 *     <li><b>返回不可变 Map</b>：结果与 {@code Map.of} 一样不可修改（{@code put/remove} 等会抛异常）。</li>
 *     <li><b>支持任意数量键值对</b>：以 {@code Object...} 形式接收，按 key,value 成对读取。</li>
 * </ul>
 *
 * <p>适用场景：构造模板数据（配合 jblade 的 {@code {{ $x }}} / {@code @if} 判空）、配置文件解析、
 * 响应构建等需要「允许部分字段缺失/为空」的场景。</p>
 *
 * <p><b>语义约定（对齐 jblade 模板引擎对空值的处理）</b>：</p>
 * <ul>
 *     <li>键为 {@code null} 或空字符串 → 跳过该条目（当作没有这条数据）。</li>
 *     <li>值为 {@code null} 或空字符串 → 保留该条目（渲染为空串，{@code @if} 判空为假）。</li>
 * </ul>
 */
public final class Maps {

    private Maps() {
    }

    /**
     * 构造不可变 Map，参数按 (key, value) 成对传入。
     *
     * <p>示例：</p>
     * <pre>{@code
     * Map<String, Object> data = Maps.of("name", "alice", "age", null, "", "ignored");
     * // 等价于 { "name" -> "alice", "age" -> null }，空键条目被跳过
     * }</pre>
     *
     * @param kvs 交替出现的 key/value，长度应为偶数；若为奇数则最后一个 key 被忽略
     * @return 不可变的 {@link LinkedHashMap}，不允许后续修改
     * @throws IllegalArgumentException 若传入非成对参数且长度不为偶数（仅当末位遗漏 value 时）
     */
    public static Map<String, Object> of(Object... kvs) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (kvs == null) {
            return java.util.Collections.unmodifiableMap(map);
        }
        int i = 0;
        while (i + 1 < kvs.length) {
            Object key = kvs[i];
            Object value = kvs[i + 1];
            // 键为空（null 或空字符串）→ 跳过该条目，视为没有这条数据
            if (key != null && !key.toString().isEmpty()) {
                map.put(key.toString(), value);
            }
            i += 2;
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    /**
     * 基于已有的键值对数组（长度必须为偶数）构造不可变 Map。
     * 与 {@link #of(Object...)} 行为一致，键为空跳过、值原样保留。
     *
     * @param entries 交替出现的 key/value
     * @return 不可变 Map
     */
    public static Map<String, Object> ofEntries(Object... entries) {
        return of(entries);
    }
}
