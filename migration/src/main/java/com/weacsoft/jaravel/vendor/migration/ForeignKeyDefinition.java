package com.weacsoft.jaravel.vendor.migration;

/**
 * 外键定义，对齐 Laravel Blueprint 的 {@code foreign()} 链。
 * <pre>
 * table.foreign("user_id").references("id").on("users").onDelete("cascade");
 * </pre>
 */
public class ForeignKeyDefinition {

    private final String column;
    private String references;
    private String onTable;
    private String onDelete;
    private String onUpdate;
    private String name;
    /** 关联的 Blueprint，用于按方言引用标识符；可能为 null（兼容旧用法） */
    private Blueprint blueprint;

    public ForeignKeyDefinition(String column) {
        this.column = column;
    }

    /** 由 Blueprint 注入，用于按方言生成标识符引号 */
    void setBlueprint(Blueprint blueprint) {
        this.blueprint = blueprint;
    }

    /** 引用列 */
    public ForeignKeyDefinition references(String column) {
        this.references = column;
        return this;
    }

    /** 引用表 */
    public ForeignKeyDefinition on(String table) {
        this.onTable = table;
        return this;
    }

    /** 删除时动作，如 "cascade"、"set null"、"restrict" */
    public ForeignKeyDefinition onDelete(String action) {
        this.onDelete = action;
        return this;
    }

    /** 更新时动作 */
    public ForeignKeyDefinition onUpdate(String action) {
        this.onUpdate = action;
        return this;
    }

    /** 约束名 */
    public ForeignKeyDefinition name(String name) {
        this.name = name;
        return this;
    }

    /** 按方言引用标识符 */
    private String quote(String identifier) {
        if (blueprint != null) {
            return blueprint.quote(identifier);
        }
        return "`" + identifier + "`";
    }

    /** 生成 ADD CONSTRAINT 子句 */
    public String toSql(String table) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(quote(table)).append(" ADD ");
        sb.append("CONSTRAINT ").append(quote(name != null ? name : table + "_" + column + "_foreign")).append(" ");
        sb.append("FOREIGN KEY (").append(quote(column)).append(")");
        if (onTable != null && references != null) {
            sb.append(" REFERENCES ").append(quote(onTable)).append("(").append(quote(references)).append(")");
        }
        if (onDelete != null) {
            sb.append(" ON DELETE ").append(onDelete.toUpperCase());
        }
        if (onUpdate != null) {
            sb.append(" ON UPDATE ").append(onUpdate.toUpperCase());
        }
        return sb.toString();
    }
}
