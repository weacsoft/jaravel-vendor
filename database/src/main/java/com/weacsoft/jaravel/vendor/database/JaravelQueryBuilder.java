package com.weacsoft.jaravel.vendor.database;

import gaarason.database.query.QueryBuilder;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * jaravel 对 gaarason {@link QueryBuilder} 的安全包装。
 *
 * <p>问题背景：在使用 SQLite 等驱动时，{@code COUNT()} 聚合结果会被 JDBC 驱动以 {@code Integer}
 * 形式返回，而 gaarason 的 {@code Aggregates.count()} 内部通过 {@code ObjectUtils.typeCast} 直接
 * 把结果强转为 {@code Long}，触发 {@code Integer cannot be cast to Long} 的 {@link ClassCastException}
 * （详见 {@code AbstractBuilder.aggregate}）。该异常在 {@code paginate()} 内部调用 {@code clone().count()}
 * 时抛出，导致分页查询直接失败。</p>
 *
 * <p>本类在不修改 gaarason 源码的前提下，重写 {@code count()}，对聚合结果做 {@code Number}-safe 的
 * 兼容转换（统一以 {@code longValue()} 兜底），从而消除该强转异常。其他聚合（sum/avg 等）返回
 * BigDecimal/Double，本就不受该强转影响，故保持框架默认行为。</p>
 *
 * <p>透明性：{@link BaseModel#newQuery()} 返回本类的实例；由于 gaarason 的 {@code paginate()} 通过
 * {@code clone()} 复制当前 builder（保留运行时类型），克隆体同样为本类实例，因此分页内部使用的
 * {@code count()} 即为本类重写的版本，业务代码无需任何改动。</p>
 *
 * @param <T> 实体类型
 * @param <K> 主键类型
 */
public class JaravelQueryBuilder<T, K> extends QueryBuilder<T, K> {

    /**
     * 将 gaarason 生成的原始 builder 的字段复制到本类实例，得到一个功能等价但具备安全 count 的 builder。
     *
     * <p>gaarason 通过 {@code initBuilder(GaarasonDataSource, Model, Grammar)} 注入字段，
     * 而 {@code newQuery()} 已返回完整初始化好的 builder；这里直接反射拷贝其字段（本类与
     * {@link QueryBuilder} 共享同一套字段布局），避免重复依赖内部构造细节。</p>
     */
    @SuppressWarnings("unchecked")
    public static <T, K> JaravelQueryBuilder<T, K> wrap(QueryBuilder<T, K> source) {
        if (source instanceof JaravelQueryBuilder) {
            return (JaravelQueryBuilder<T, K>) source;
        }
        JaravelQueryBuilder<T, K> target = new JaravelQueryBuilder<>();
        copyFields(source, target);
        return target;
    }

    private static void copyFields(Object source, Object target) {
        Class<?> clazz = source.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    field.set(target, field.get(source));
                } catch (IllegalAccessException ignored) {
                    // 单个字段拷贝失败不影响整体（如 transient/static 等）
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    // ==================== 安全 count ====================

    @Override
    public Long count() {
        try {
            return super.count();
        } catch (ClassCastException e) {
            return safeCount("*");
        }
    }

    @Override
    public Long count(String column) {
        try {
            return super.count(column);
        } catch (ClassCastException e) {
            return safeCount(column);
        }
    }

    /**
     * 安全版本的 count：绕过 gaarason 的强转，自己执行 {@code COUNT} 并对结果做数值兼容。
     */
    @SuppressWarnings("unchecked")
    private Long safeCount(String column) {
        String alias = "jaravel_cnt";
        Map<String, Object> resMap = this.selectFunction("COUNT", column, alias)
                .firstOrFail().toMap();
        return toLong(resMap.get(alias));
    }

    /**
     * 把聚合结果统一转换为 {@code Long}，对 {@code Integer}/{@code Long}/{@code BigInteger} 等
     * 数值类型做兼容；非数值则按字符串解析，空值返回 0。
     */
    private static Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(value.toString());
    }
}
