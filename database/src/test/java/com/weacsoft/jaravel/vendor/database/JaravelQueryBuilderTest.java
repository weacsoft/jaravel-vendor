package com.weacsoft.jaravel.vendor.database;

import gaarason.database.query.QueryBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link JaravelQueryBuilder} 回归测试。
 * <p>
 * 验证 {@link JaravelQueryBuilder#wrap(QueryBuilder)} 的契约：
 * <ul>
 *     <li>对普通 {@link QueryBuilder} 包装后得到 {@link JaravelQueryBuilder} 实例，
 *         从而保证 {@link BaseModel#newQuery()} 返回的就是该安全版本（分页内部的
 *         {@code clone().count()} 走 Integer→Long 兼容逻辑）；</li>
 *     <li>对已包装对象再次 wrap 幂等，避免重复字段拷贝。</li>
 * </ul>
 * <p>
 * 说明：完整触发 SQLite 下 {@code COUNT} 返回 {@code Integer} 的端到端验证需要真实数据库连接，
 * 属于集成测试范畴；此处单元验证包装契约即可锁定 {@code newQuery()} 已接入安全路径。
 */
class JaravelQueryBuilderTest {

    @Test
    void wrapProducesSafeJaravelQueryBuilder() {
        QueryBuilder<Object, Long> base = new QueryBuilder<>();
        JaravelQueryBuilder<Object, Long> wrapped = JaravelQueryBuilder.wrap(base);
        assertInstanceOf(JaravelQueryBuilder.class, wrapped,
                "wrap() 应返回 JaravelQueryBuilder，以启用安全的 count()");
    }

    @Test
    void wrapIsIdempotentForAlreadyWrappedBuilder() {
        QueryBuilder<Object, Long> base = new QueryBuilder<>();
        JaravelQueryBuilder<Object, Long> wrapped = JaravelQueryBuilder.wrap(base);
        assertSame(wrapped, JaravelQueryBuilder.wrap(wrapped),
                "对已包装的 builder 再次 wrap 应原样返回，避免重复拷贝");
    }
}
