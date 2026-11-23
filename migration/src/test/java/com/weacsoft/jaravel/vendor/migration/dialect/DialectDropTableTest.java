package com.weacsoft.jaravel.vendor.migration.dialect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 方言的删表能力测试：验证各方言是否支持 {@code DROP TABLE IF EXISTS} 语法。
 * <p>
 * Oracle 不支持该语法，执行会报 ORA-00900（无效的 SQL 语句），
 * 需要先查询系统目录确认表是否存在再执行删除。
 */
class DialectDropTableTest {

    @Test
    void oracleDoesNotSupportDropTableIfExists() {
        Dialect d = DialectFactory.create("oracle");
        assertFalse(d.supportsDropTableIfExists(), "Oracle 不支持 DROP TABLE IF EXISTS");
    }

    @Test
    void otherDialectsSupportDropTableIfExists() {
        for (String product : new String[]{"mysql", "sqlite", "h2", "microsoft sql server", "postgresql"}) {
            Dialect d = DialectFactory.create(product);
            assertTrue(d.supportsDropTableIfExists(), product + " 应支持 DROP TABLE IF EXISTS");
        }
    }

    @Test
    void unknownProductFallsBackToMysqlWhichSupportsDropTableIfExists() {
        Dialect d = DialectFactory.create("some-unknown-db");
        assertTrue(d.supportsDropTableIfExists(), "未知数据库回退到 MySQL 方言，应支持 DROP TABLE IF EXISTS");
    }
}
