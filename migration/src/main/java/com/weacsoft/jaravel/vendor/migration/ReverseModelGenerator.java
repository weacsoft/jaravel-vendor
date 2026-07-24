package com.weacsoft.jaravel.vendor.migration;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 反向工程 Model 生成器，从数据库表结构生成 Model Java 源文件。
 * <p>
 * 通过 JDBC {@link DatabaseMetaData} 读取表的列信息和主键信息，
 * 根据列类型映射为 Java 类型，生成继承 {@code BaseModel} 的 Model 类，
 * 对齐 Laravel 的 {@code php artisan make:model} 反向生成能力。
 * <p>
 * 功能特性：
 * <ul>
 *   <li>自动映射 SQL 类型到 Java 类型</li>
 *   <li>snake_case 列名转 camelCase 字段名</li>
 *   <li>表名转 PascalCase 类名（自动单数化）</li>
 *   <li>检测 {@code deleted_at} 列自动添加软删除支持</li>
 *   <li>仅在使用 {@code DECIMAL/NUMERIC} 类型时导入 {@code java.math.BigDecimal}</li>
 * </ul>
 */
public class ReverseModelGenerator {

    private final DataSource dataSource;

    public ReverseModelGenerator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 从数据库表生成 Model 类源文件。
     *
     * @param tableName   数据库表名
     * @param basePackage 基包名（如 com.weacsoft.jaravel）
     * @param outputDir   输出根目录（如 src/main/java）
     * @param force       是否覆盖已存在文件
     * @return 生成的文件绝对路径
     * @throws SQLException 数据库读取异常
     * @throws IOException   文件写入异常
     */
    public String generate(String tableName, String basePackage, String outputDir, boolean force) throws SQLException, IOException {
        // 1. 读取表结构
        List<ColumnInfo> columns = readTableColumns(tableName);
        if (columns.isEmpty()) {
            throw new IllegalStateException("表不存在或没有列: " + tableName);
        }
        String pkColumn = readPrimaryKey(tableName);

        // 2. 生成类名和包名
        String className = toPascalCase(singularize(tableName));
        String packageName = basePackage + ".models";

        // 3. 构建源代码
        String source = buildModelSource(packageName, className, tableName, columns, pkColumn);

        // 4. 写入文件
        return writeJavaFile(outputDir, packageName, className, source, force);
    }

    // ==================== 数据库读取 ====================

    /**
     * 读取表的所有列信息。
     */
    List<ColumnInfo> readTableColumns(String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, tableName, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    int dataType = rs.getInt("DATA_TYPE");
                    String typeName = rs.getString("TYPE_NAME");
                    int nullable = rs.getInt("NULLABLE");
                    int columnSize = rs.getInt("COLUMN_SIZE");
                    String remarks = rs.getString("REMARKS");
                    columns.add(new ColumnInfo(name, dataType, typeName, nullable, columnSize, remarks));
                }
            }
        }
        return columns;
    }

    /**
     * 读取表的主键列名（取第一个主键列）。
     *
     * @return 主键列名，无主键返回 null
     */
    String readPrimaryKey(String tableName) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getPrimaryKeys(null, null, tableName)) {
                // 按 KEY_SEQ 排序，取序号最小的
                Map<Integer, String> pkMap = new LinkedHashMap<>();
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    int keySeq = rs.getInt("KEY_SEQ");
                    pkMap.put(keySeq, colName);
                }
                if (pkMap.isEmpty()) {
                    return null;
                }
                // 取 KEY_SEQ 最小的（通常为 1）
                return pkMap.entrySet().stream()
                        .min(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue)
                        .orElse(null);
            }
        }
    }

    // ==================== 类型映射 ====================

    /**
     * 将 SQL 类型映射为 Java 类型字符串。
     *
     * @param col      列信息
     * @param isPrimary 是否为主键
     * @return Java 类型名（如 String、Long、BigDecimal）
     */
    String mapSqlTypeToJava(ColumnInfo col, boolean isPrimary) {
        String typeName = col.getTypeName() != null ? col.getTypeName().toUpperCase() : "";
        int dataType = col.getDataType();

        // 检查数据库特定的文本类型（TEXT, MEDIUMTEXT, LONGTEXT, JSON, DATETIME）
        if (typeName.contains("TEXT") || typeName.equals("JSON") || typeName.equals("DATETIME")) {
            return "String";
        }

        // TINYINT(1) -> Boolean（MySQL 约定），其他 TINYINT -> Integer
        if (dataType == Types.TINYINT) {
            return col.getColumnSize() == 1 ? "Boolean" : "Integer";
        }

        switch (dataType) {
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.NVARCHAR:
            case Types.NCHAR:
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.CLOB:
            case Types.NCLOB:
                return "String";
            case Types.INTEGER:
            case Types.SMALLINT:
                return isPrimary ? "Long" : "Integer";
            case Types.BIGINT:
                return "Long";
            case Types.BOOLEAN:
            case Types.BIT:
                return "Boolean";
            case Types.DECIMAL:
            case Types.NUMERIC:
                return "BigDecimal";
            case Types.FLOAT:
            case Types.REAL:
                return "Float";
            case Types.DOUBLE:
                return "Double";
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
            case Types.TIME_WITH_TIMEZONE:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return "String";
            case Types.BLOB:
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return "byte[]";
            default:
                // 回退：检查 typeName 中是否包含 INT
                if (typeName.contains("INT") && isPrimary) {
                    return "Long";
                }
                return "String";
        }
    }

    // ==================== 名称转换 ====================

    /**
     * 将 snake_case 转换为 camelCase。
     * e.g. {@code user_name} -> {@code userName}, {@code id} -> {@code id}
     */
    String toCamelCase(String snake) {
        if (snake == null || snake.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String[] parts = snake.toLowerCase().split("_");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() == 0) {
                sb.append(part);
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.toString();
    }

    /**
     * 将任意字符串转换为 PascalCase。
     * e.g. {@code user_name} -> {@code UserName}, {@code user} -> {@code User}
     */
    String toPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return "Generated";
        }
        StringBuilder sb = new StringBuilder();
        String[] parts = input.toLowerCase().split("[^a-zA-Z0-9]+");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.length() > 0 ? sb.toString() : "Generated";
    }

    /**
     * 简单单数化：去除末尾 's'，但保留以 'ss'、'us'、'is' 结尾的词。
     * e.g. {@code users} -> {@code user}, {@code settings} -> {@code setting},
     * {@code class} -> {@code class}, {@code status} -> {@code status}
     */
    String singularize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String lower = name.toLowerCase();
        if (lower.endsWith("ss") || lower.endsWith("us") || lower.endsWith("is")) {
            return name;
        }
        if (lower.endsWith("s") && lower.length() > 1) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    // ==================== 源代码生成 ====================

    /**
     * 构建完整的 Model Java 源代码。
     */
    String buildModelSource(String packageName, String className, String tableName,
                            List<ColumnInfo> columns, String pkColumn) {
        // 预扫描：判断是否需要 BigDecimal 导入、是否有 deleted_at 列
        boolean needsBigDecimal = false;
        boolean hasDeletedAt = false;
        for (ColumnInfo col : columns) {
            boolean isPrimary = pkColumn != null && col.getName().equalsIgnoreCase(pkColumn);
            String javaType = mapSqlTypeToJava(col, isPrimary);
            if ("BigDecimal".equals(javaType)) {
                needsBigDecimal = true;
            }
            if ("deleted_at".equalsIgnoreCase(col.getName())) {
                hasDeletedAt = true;
            }
        }

        StringBuilder sb = new StringBuilder();

        // 包声明
        sb.append("package ").append(packageName).append(";\n\n");

        // 导入
        sb.append("import com.weacsoft.jaravel.vendor.database.BaseModel;\n");
        sb.append("import gaarason.database.annotation.Column;\n");
        sb.append("import gaarason.database.annotation.Primary;\n");
        sb.append("import gaarason.database.annotation.Table;\n");
        sb.append("import gaarason.database.query.QueryBuilder;\n");
        sb.append("import lombok.Data;\n");
        sb.append("import lombok.EqualsAndHashCode;\n");
        sb.append("import org.springframework.stereotype.Repository;\n");
        sb.append("\n");
        sb.append("import java.util.List;\n");
        if (needsBigDecimal) {
            sb.append("import java.math.BigDecimal;\n");
        }
        sb.append("\n");

        // 类 Javadoc
        sb.append("/**\n");
        sb.append(" * ").append(className).append(" 模型，对齐 Laravel Eloquent。\n");
        sb.append(" * <p>\n");
        sb.append(" * 对应数据库表：{@code ").append(tableName).append("}\n");
        sb.append(" * <p>\n");
        sb.append(" * 由 make:model-from-table 从数据库反向生成。\n");
        sb.append(" */\n");

        // 类注解
        sb.append("@Data\n");
        sb.append("@EqualsAndHashCode(callSuper = false)\n");
        sb.append("@Repository\n");
        sb.append("@Table(name = \"").append(tableName).append("\")\n");

        // 类声明
        sb.append("public class ").append(className)
                .append(" extends BaseModel<").append(className).append(", Long> {\n\n");

        // 字段
        for (ColumnInfo col : columns) {
            boolean isPrimary = pkColumn != null && col.getName().equalsIgnoreCase(pkColumn);
            String javaType = mapSqlTypeToJava(col, isPrimary);
            String fieldName = toCamelCase(col.getName());

            if (isPrimary) {
                sb.append("    @Primary\n");
            }
            sb.append("    @Column(name = \"").append(col.getName()).append("\")\n");
            sb.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n\n");
        }

        // 软删除支持
        if (hasDeletedAt) {
            sb.append("    @Override\n");
            sb.append("    protected boolean softDeleting() {\n");
            sb.append("        return true;\n");
            sb.append("    }\n\n");
        }

        // 静态查询方法
        sb.append("    // ==================== 静态查询方法 ====================\n\n");
        sb.append("    public static ").append(className).append(" find(Long id) {\n");
        sb.append("        return BaseModel.find(").append(className).append(".class, id);\n");
        sb.append("    }\n\n");
        sb.append("    public static List<").append(className).append("> all() {\n");
        sb.append("        return BaseModel.all(").append(className).append(".class);\n");
        sb.append("    }\n\n");
        sb.append("    public static QueryBuilder<").append(className).append(", Long> query() {\n");
        sb.append("        return BaseModel.query(").append(className).append(".class);\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ==================== 文件写入 ====================

    /**
     * 将 Java 源文件写入到指定包目录下。
     *
     * @param outputDir   输出根目录
     * @param packageName 包名
     * @param className   类名
     * @param content     源代码内容
     * @param force       是否覆盖已存在文件
     * @return 文件绝对路径
     */
    String writeJavaFile(String outputDir, String packageName, String className,
                         String content, boolean force) throws IOException {
        String packagePath = packageName.replace('.', '/');
        Path dir = Paths.get(outputDir, packagePath);
        Files.createDirectories(dir);

        Path file = dir.resolve(className + ".java");
        if (Files.exists(file) && !force) {
            throw new IllegalStateException("文件已存在，拒绝覆盖: " + file.toAbsolutePath()
                    + "（使用 --force 强制覆盖）");
        }

        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file.toAbsolutePath().toString();
    }

    // ==================== 列信息内部类 ====================

    /**
     * 列元数据信息。
     */
    public static class ColumnInfo {
        private final String name;
        private final int dataType;    // java.sql.Types
        private final String typeName; // 数据库特定类型名
        private final int nullable;
        private final int columnSize;
        private final String remarks;

        public ColumnInfo(String name, int dataType, String typeName, int nullable, int columnSize, String remarks) {
            this.name = name;
            this.dataType = dataType;
            this.typeName = typeName;
            this.nullable = nullable;
            this.columnSize = columnSize;
            this.remarks = remarks;
        }

        public String getName() {
            return name;
        }

        public int getDataType() {
            return dataType;
        }

        public String getTypeName() {
            return typeName;
        }

        public int getNullable() {
            return nullable;
        }

        public int getColumnSize() {
            return columnSize;
        }

        public String getRemarks() {
            return remarks;
        }
    }
}
