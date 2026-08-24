package com.weacsoft.jaravel.vendor.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从迁移文件解析出的表结构定义。
 * <p>
 * 由 {@link MigrationFileParser} 在执行迁移 {@code up()} 方法后，
 * 从 {@link CapturingSchema} 捕获的 {@link Blueprint} 转换而来。
 * 包含表名和有序的列定义列表，用于 {@link ReverseModelGenerator} 生成 Model 类。
 * <p>
 * 多个迁移对同一张表的定义会合并：{@code create()} 建立初始定义，
 * 后续 {@code table()} 追加的列会合并进来（同名列覆盖）。
 *
 * @see ParsedColumn
 * @see MigrationFileParser
 * @see ReverseModelGenerator#generateFromParsedTable
 */
public class ParsedTable {

    private final String tableName;
    private final Map<String, ParsedColumn> columnMap = new LinkedHashMap<>();

    /**
     * 创建解析表定义。
     *
     * @param tableName 表名
     */
    public ParsedTable(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 获取表名。
     *
     * @return 表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 添加或覆盖列定义。
     * <p>
     * 若已存在同名列，则用新定义替换（模拟 ALTER TABLE MODIFY 的效果）。
     *
     * @param column 列定义
     */
    public void addColumn(ParsedColumn column) {
        columnMap.put(column.getName(), column);
    }

    /**
     * 获取所有列定义（保持插入顺序）。
     *
     * @return 不可修改的列定义列表
     */
    public List<ParsedColumn> getColumns() {
        return Collections.unmodifiableList(new ArrayList<>(columnMap.values()));
    }

    /**
     * 获取主键列名（取第一个标记为 primary 的列）。
     *
     * @return 主键列名，无主键返回 null
     */
    public String getPrimaryKeyColumn() {
        for (ParsedColumn col : columnMap.values()) {
            if (col.isPrimary()) {
                return col.getName();
            }
        }
        return null;
    }

    /**
     * 检查表中是否包含 {@code deleted_at} 列（软删除标记）。
     *
     * @return true 表示存在 deleted_at 列
     */
    public boolean hasSoftDeletes() {
        return columnMap.containsKey("deleted_at");
    }

    /**
     * 获取列数量。
     *
     * @return 列数
     */
    public int columnCount() {
        return columnMap.size();
    }
}
