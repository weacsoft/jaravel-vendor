package com.weacsoft.jaravel.vendor.migration.dialect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * H2 方言 mapType 回归测试。
 * <p>
 * 历史上 H2Dialect 直接输出 MySQL 专属类型（{@code TINYINT(1)}、{@code * UNSIGNED}、
 * {@code MEDIUMINT}、{@code YEAR}、{@code LONGTEXT}、{@code LONGBLOB}），
 * 导致在 H2 默认模式下建表失败，测试只能靠 {@code MODE=MySQL} 掩盖。
 * 修复后输出 H2 原生类型，在默认模式与 {@code MODE=MySQL} 下均可建表。
 */
class H2DialectMapTypeTest {

    private final Dialect d = DialectFactory.create("h2");

    @Test
    void integerFamilyIsNativeWithoutUnsigned() {
        assertEquals("INT", d.mapType("integer", null, null, null, false));
        assertEquals("INT", d.mapType("integer", null, null, null, true), "H2 无 UNSIGNED");
        assertEquals("BIGINT", d.mapType("bigInteger", null, null, null, false));
        assertEquals("BIGINT", d.mapType("bigInteger", null, null, null, true), "H2 无 UNSIGNED");
        assertEquals("TINYINT", d.mapType("tinyInteger", null, null, null, false));
        assertEquals("SMALLINT", d.mapType("smallInteger", null, null, null, false));
        assertEquals("INT", d.mapType("mediumInteger", null, null, null, false), "MEDIUMINT 在 H2 不存在");
    }

    @Test
    void booleanAndTextAndBinaryAreNative() {
        assertEquals("BOOLEAN", d.mapType("boolean", null, null, null, false), "TINYINT(1) 不是 H2 原生布尔");
        assertEquals("TEXT", d.mapType("text", null, null, null, false));
        assertEquals("CLOB", d.mapType("mediumText", null, null, null, false), "MEDIUMTEXT 在 H2 不存在");
        assertEquals("CLOB", d.mapType("longText", null, null, null, false), "LONGTEXT 在 H2 不存在");
        assertEquals("BLOB", d.mapType("binary", null, null, null, false), "LONGBLOB 在 H2 不存在");
        assertEquals("INT", d.mapType("year", null, null, null, false), "YEAR 在 H2 不存在");
    }

    @Test
    void standardTypesUnchanged() {
        assertEquals("VARCHAR(255)", d.mapType("string", null, null, null, false));
        assertEquals("VARCHAR(100)", d.mapType("string", 100, null, null, false));
        assertEquals("CHAR(50)", d.mapType("char", 50, null, null, false));
        // 注意参数顺序：mapType(logicalType, length, precision, scale, unsigned)，
        // decimal 使用 (precision, scale)，length 位传 null
        assertEquals("DECIMAL(8,2)", d.mapType("decimal", null, null, null, false));
        assertEquals("DECIMAL(10,2)", d.mapType("decimal", null, 10, 2, false));
        assertEquals("FLOAT", d.mapType("float", null, null, null, false));
        assertEquals("DOUBLE", d.mapType("double", null, null, null, false));
        assertEquals("DATE", d.mapType("date", null, null, null, false));
        assertEquals("DATETIME", d.mapType("dateTime", null, null, null, false));
        assertEquals("TIME", d.mapType("time", null, null, null, false));
        assertEquals("TIMESTAMP", d.mapType("timestamp", null, null, null, false));
        assertEquals("JSON", d.mapType("json", null, null, null, false));
        assertEquals("mytype", d.mapType("mytype", null, null, null, false), "未知类型透传");
    }

    @Test
    void noMysqlSpecificTokensRemain() {
        for (String t : new String[]{"integer", "bigInteger", "tinyInteger", "smallInteger",
                "mediumInteger", "boolean", "text", "mediumText", "longText", "year", "binary"}) {
            String mapped = d.mapType(t, null, null, null, true);
            assertFalse(mapped.toUpperCase().contains("UNSIGNED"), t + " 不应含 UNSIGNED: " + mapped);
            assertFalse(mapped.toUpperCase().contains("MEDIUMINT"), t + " 不应含 MEDIUMINT: " + mapped);
            assertFalse(mapped.toUpperCase().contains("LONGBLOB"), t + " 不应含 LONGBLOB: " + mapped);
            assertFalse(mapped.toUpperCase().contains("LONGTEXT"), t + " 不应含 LONGTEXT: " + mapped);
            assertFalse(mapped.equals("TINYINT(1)"), t + " 不应是 TINYINT(1): " + mapped);
            assertFalse(mapped.equals("YEAR"), t + " 不应是 YEAR: " + mapped);
        }
    }
}
