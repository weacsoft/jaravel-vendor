package com.weacsoft.jaravel.vendor.migration.dialect;

/**
 * 方言抽象基类，提供通用默认实现。
 * <p>
 * 各具体方言继承此类，按需覆写方法。公共逻辑（如数值判断、字符串转义）在此提供。
 */
public abstract class AbstractDialect implements Dialect {

    /** 方言名称（小写） */
    protected final String name;

    protected AbstractDialect(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String tableOptions() {
        return "";
    }

    @Override
    public boolean supportsModifyColumn() {
        return true;
    }

    @Override
    public boolean needsTableRecreationForModify() {
        return false;
    }

    @Override
    public boolean usesPragmaForColumnInfo() {
        return false;
    }

    @Override
    public String pragmaTableInfoSql(String quotedTable) {
        return null;
    }

    @Override
    public boolean needsCheckTableExistsBeforeCreateRepository() {
        return false;
    }

    @Override
    public boolean supportsColumnComment() {
        return false;
    }

    @Override
    public boolean supportsAfterColumn() {
        return false;
    }

    // ==================== 通用工具方法 ====================

    /** 判断字符串是否为数值 */
    protected static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            new java.math.BigDecimal(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 转义 SQL 字符串中的单引号 */
    protected static String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    /** 构造 DEFAULT 子句（不含 DEFAULT 关键字前导空格的情况由调用方处理） */
    protected String buildDefaultClause(String defaultValue, boolean hasDefault) {
        if (!hasDefault) {
            return "";
        }
        if (defaultValue == null) {
            return " DEFAULT NULL";
        }
        if (isNumeric(defaultValue)) {
            return " DEFAULT " + defaultValue;
        }
        if ("CURRENT_TIMESTAMP".equalsIgnoreCase(defaultValue)) {
            return " DEFAULT CURRENT_TIMESTAMP";
        }
        return " DEFAULT '" + escape(defaultValue) + "'";
    }
}
