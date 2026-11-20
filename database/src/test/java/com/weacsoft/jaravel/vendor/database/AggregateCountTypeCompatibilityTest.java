package com.weacsoft.jaravel.vendor.database;

import gaarason.database.appointment.AggregatesType;
import gaarason.database.contract.builder.Aggregates;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code COUNT()} 聚合结果类型兼容性的回归测试（上游 issue gaarason/database-all#64）。
 * <p>
 * <b>背景</b>：SQLite 等 JDBC 驱动把 {@code COUNT(*)} 以 {@code Integer} 返回，而 gaarason 7.0.15 的
 * {@code Aggregates.count(String)} 直接 {@code checkcast java/lang/Long}，于是分页内部
 * {@code clone().count()} 抛：
 * <pre>
 * ClassCastException: class java.lang.Integer cannot be cast to class java.lang.Long
 *   at gaarason.database.contract.builder.Aggregates.count(Aggregates.java:36)
 *   at gaarason.database.query.ExecuteLevel3Builder.paginate(...)
 * </pre>
 * 当时 jaravel 用一个 {@code JaravelQueryBuilder} 包装层（重写 {@code newQuery()} + 反射拷贝字段 +
 * 自己发 COUNT 查询）绕过该缺陷。
 * <p>
 * <b>现状</b>：gaarason 7.1.3 已改为
 * {@code result instanceof Number ? ((Number) result).longValue() : Long.valueOf(result.toString())}，
 * 因此兼容层已整体移除（{@code BaseModel} 不再重写 {@code newQuery()}）。
 * <p>
 * <b>本测试的价值</b>：它不依赖任何具体数据库驱动，而是直接对 {@link Aggregates#count(String)}
 * 喂各种 {@code Number} 子类，锁定「聚合结果按数值语义归一为 Long」这一契约。
 * 一旦有人把 gaarason 降回 7.0.x，{@code Integer} 这一行就会立刻变红 ——
 * 这正是当初兼容层存在的唯一理由，比端到端跑一遍 SQLite 更精准、更快。
 */
class AggregateCountTypeCompatibilityTest {

    /**
     * 构造一个把 {@code aggregate(...)} 固定返回指定值的 {@link Aggregates} 桩。
     * <p>
     * {@code count(String)} 是 interface 上的 default 方法，最终都会调用
     * {@link Aggregates#aggregate(AggregatesType, String)}，因此只实现这一个抽象方法即可。
     *
     * @param aggregateResult {@code aggregate} 的返回值
     * @return 桩实例
     */
    private static Aggregates stubReturning(Object aggregateResult) {
        return new Aggregates() {
            @Override
            @SuppressWarnings("unchecked")
            public <R> R aggregate(AggregatesType type, String column) {
                return (R) aggregateResult;
            }
        };
    }

    /**
     * 核心回归：{@code Integer} 形态的 COUNT 结果<b>不得</b>再抛 {@code ClassCastException}。
     * <p>
     * 这是 #64 在 SQLite 下的真实返回类型，也是 7.0.15 的崩溃点。
     */
    @Test
    void integerCountResultIsAccepted() {
        Aggregates aggregates = stubReturning(Integer.valueOf(42));
        Long count = assertDoesNotThrow(() -> aggregates.count("*"),
                "Integer 形态的 COUNT 结果必须被接受；若此处抛 ClassCastException，"
                        + "说明 gaarason 被降回了 7.0.x，需要恢复 JaravelQueryBuilder 兼容层");
        assertEquals(42L, count);
    }

    /** {@code Long} 形态（MySQL 等驱动）照常工作。 */
    @Test
    void longCountResultIsAccepted() {
        assertEquals(42L, stubReturning(42L).count("*"));
    }

    /** {@code BigInteger} 形态（部分驱动/大表）不得崩。 */
    @Test
    void bigIntegerCountResultIsAccepted() {
        assertEquals(42L, stubReturning(BigInteger.valueOf(42L)).count("*"));
    }

    /** 非数值但可解析的字符串形态（如 H2 某些配置返回字符串）按字符串解析。 */
    @Test
    void numericStringCountResultIsAccepted() {
        assertEquals(42L, stubReturning("42").count("*"));
    }

    /** {@code null} 聚合结果（空结果集/自定义 SQL）返回 null 而不是 NPE。 */
    @Test
    void nullCountResultReturnsNull() {
        assertEquals(null, stubReturning(null).count("*"));
    }

    /**
     * 无参 {@code count()} 走 {@code count("*")}，行为一致。
     */
    @Test
    void noArgCountDelegatesToStarColumn() {
        assertEquals(7L, stubReturning(Integer.valueOf(7)).count());
    }
}
