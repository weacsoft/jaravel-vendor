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

    /** 转义 SQL SQL 字符串中的单引号 */
    protected static String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    // ==================== upsert SQL 变体（供各方言实现复用） ====================
    // 语义统一为「按冲突列（主键/唯一键）写入或覆盖」：
    // INSERT 全部列；冲突时更新除冲突列之外的所有列。占位符顺序固定为列序。

    /**
     * 拼接 {@code INSERT INTO t (cols) VALUES (?, ...)} 公共部分，再附加方言冲突子句。
     */
    protected static String joinUpsert(String quotedTable, String[] quotedColumns, String conflictClause) {
        StringBuilder cols = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < quotedColumns.length; i++) {
            if (i > 0) {
                cols.append(", ");
                values.append(", ");
            }
            cols.append(quotedColumns[i]);
            values.append('?');
        }
        String base = "INSERT INTO " + quotedTable + " (" + cols + ") VALUES (" + values + ")";
        return (conflictClause == null || conflictClause.isEmpty())
                ? base
                : base + " " + conflictClause;
    }

    /** MySQL 变体：{@code ON DUPLICATE KEY UPDATE col = VALUES(col)} */
    public static String upsertMysql(String quotedTable, String[] quotedColumns, String quotedKeyColumn) {
        StringBuilder set = new StringBuilder();
        for (String c : quotedColumns) {
            if (c.equalsIgnoreCase(quotedKeyColumn)) {
                continue;
            }
            if (set.length() > 0) {
                set.append(", ");
            }
            set.append(c).append(" = VALUES(").append(c).append(')');
        }
        return joinUpsert(quotedTable, quotedColumns,
                set.length() > 0 ? "ON DUPLICATE KEY UPDATE " + set : null);
    }

    /** PostgreSQL / SQLite 变体：{@code ON CONFLICT (key) DO UPDATE SET col = EXCLUDED.col} */
    public static String upsertOnConflict(String quotedTable, String[] quotedColumns, String quotedKeyColumn) {
        StringBuilder set = new StringBuilder();
        for (String c : quotedColumns) {
            if (c.equalsIgnoreCase(quotedKeyColumn)) {
                continue;
            }
            if (set.length() > 0) {
                set.append(", ");
            }
            set.append(c).append(" = EXCLUDED.").append(c);
        }
        return joinUpsert(quotedTable, quotedColumns,
                set.length() > 0 ? "ON CONFLICT (" + quotedKeyColumn + ") DO UPDATE SET " + set : null);
    }

    /** H2 变体：{@code MERGE INTO t (cols) KEY (key) VALUES (?, ...)} */
    public static String upsertH2(String quotedTable, String[] quotedColumns, String quotedKeyColumn) {
        StringBuilder cols = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < quotedColumns.length; i++) {
            if (i > 0) {
                cols.append(", ");
                values.append(", ");
            }
            cols.append(quotedColumns[i]);
            values.append('?');
        }
        return "MERGE INTO " + quotedTable + " (" + cols + ") KEY (" + quotedKeyColumn
                + ") VALUES (" + values + ")";
    }

    /**
     * SQL Server / Oracle 变体：{@code MERGE ... USING (SELECT ...) s ON (t.key = s.key)
     * WHEN MATCHED THEN UPDATE SET t.c = s.c WHEN NOT MATCHED THEN INSERT ...}
     *
     * @param sourceAliasStyle "as"（SQL Server：{@code SELECT ? AS c ... }，无 FROM）
     *                         或 "dual"（Oracle：{@code SELECT ? c ... FROM DUAL}）
     */
    public static String upsertMergeUsing(String quotedTable, String[] quotedColumns,
                                          String quotedKeyColumn, String sourceAliasStyle) {
        boolean oracle = "dual".equals(sourceAliasStyle);
        StringBuilder select = new StringBuilder();
        StringBuilder insertCols = new StringBuilder();
        StringBuilder insertVals = new StringBuilder();
        StringBuilder updateSet = new StringBuilder();
        for (int i = 0; i < quotedColumns.length; i++) {
            String c = quotedColumns[i];
            if (i > 0) {
                select.append(", ");
                insertCols.append(", ");
                insertVals.append(", ");
            }
            select.append('?').append(oracle ? " " : " AS ").append(c);
            insertCols.append(c);
            insertVals.append("s.").append(c);
            if (!c.equalsIgnoreCase(quotedKeyColumn)) {
                if (updateSet.length() > 0) {
                    updateSet.append(", ");
                }
                updateSet.append("t.").append(c).append(" = s.").append(c);
            }
        }
        String usingFrom = oracle ? " FROM DUAL" : "";
        StringBuilder sb = new StringBuilder();
        sb.append("MERGE ").append(quotedTable).append(" AS t ")   // H2 兼容；SQL Server / Oracle 均接受
                .append("USING (SELECT ").append(select).append(usingFrom).append(") AS s ")
                .append("ON (t.").append(quotedKeyColumn).append(" = s.").append(quotedKeyColumn).append(") ");
        if (updateSet.length() > 0) {
            sb.append("WHEN MATCHED THEN UPDATE SET ").append(updateSet).append(" ");
        }
        sb.append("WHEN NOT MATCHED THEN INSERT (").append(insertCols).append(") VALUES (").append(insertVals).append(")");
        return sb.toString();
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
