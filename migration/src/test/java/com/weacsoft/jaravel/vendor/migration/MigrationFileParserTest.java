package com.weacsoft.jaravel.vendor.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 迁移文件解析相关测试。
 * <p>
 * 覆盖以下组件：
 * <ul>
 *   <li>{@link CapturingSchema} — 捕获 Blueprint 定义</li>
 *   <li>{@link ParsedTable} / {@link ParsedColumn} — 列合并、主键检测、软删除检测</li>
 *   <li>{@link MigrationFileParser} — 从 .java 迁移文件解析表结构</li>
 *   <li>{@link ReverseModelGenerator#generateFromParsedTable} — 从解析表定义生成 Model</li>
 *   <li>{@link ReverseModelGenerator#mapMigrationTypeToJava} — 迁移类型到 Java 类型映射</li>
 * </ul>
 */
class MigrationFileParserTest {

    @TempDir
    File tempDir;

    // ==================== CapturingSchema 测试 ====================

    /**
     * 测试迁移：创建 users 表，含 id、name、email、timestamps、softDeletes。
     */
    private static class TestCreateUsersMigration implements Migration {
        @Override
        public void up(Schema schema) {
            schema.create("users", table -> {
                table.id();
                table.string("name", 50);
                table.string("email", 100).unique();
                table.timestamps();
                table.softDeletes();
            });
        }

        @Override
        public void down(Schema schema) {
            schema.dropIfExists("users");
        }
    }

    /**
     * 测试迁移：修改 users 表，添加 phone 列。
     */
    private static class TestAlterUsersMigration implements Migration {
        @Override
        public void up(Schema schema) {
            schema.table("users", table -> {
                table.string("phone", 20).nullable();
            });
        }

        @Override
        public void down(Schema schema) {
        }
    }

    /**
     * 测试迁移：创建 products 表，含 decimal 类型。
     */
    private static class TestCreateProductsMigration implements Migration {
        @Override
        public void up(Schema schema) {
            schema.create("products", table -> {
                table.id();
                table.string("name");
                table.decimal("price", 10, 2);
                table.integer("stock");
                table.booleanColumn("active");
                table.text("description").nullable();
                table.timestamps();
            });
        }

        @Override
        public void down(Schema schema) {
            schema.dropIfExists("products");
        }
    }

    @Test
    @DisplayName("CapturingSchema 捕获 create() 定义的 Blueprint")
    void testCapturingSchemaCreate() {
        CapturingSchema schema = new CapturingSchema();
        new TestCreateUsersMigration().up(schema);

        List<Blueprint> blueprints = schema.getBlueprints();
        assertEquals(1, blueprints.size());
        assertEquals("users", blueprints.get(0).getTable());
        assertEquals(6, blueprints.get(0).getColumns().size());
    }

    @Test
    @DisplayName("CapturingSchema 捕获 table() 定义的 Blueprint")
    void testCapturingSchemaTable() {
        CapturingSchema schema = new CapturingSchema();
        new TestAlterUsersMigration().up(schema);

        List<Blueprint> blueprints = schema.getBlueprints();
        assertEquals(1, blueprints.size());
        assertEquals("users", blueprints.get(0).getTable());
        assertEquals(1, blueprints.get(0).getColumns().size());
        assertEquals("phone", blueprints.get(0).getColumns().get(0).getName());
    }

    @Test
    @DisplayName("CapturingSchema clear() 清除已捕获的 Blueprint")
    void testCapturingSchemaClear() {
        CapturingSchema schema = new CapturingSchema();
        new TestCreateUsersMigration().up(schema);
        assertEquals(1, schema.getBlueprints().size());

        schema.clear();
        assertTrue(schema.getBlueprints().isEmpty());

        new TestCreateProductsMigration().up(schema);
        assertEquals(1, schema.getBlueprints().size());
        assertEquals("products", schema.getBlueprints().get(0).getTable());
    }

    @Test
    @DisplayName("CapturingSchema 的 drop/hasTable/hasColumn 为空实现")
    void testCapturingSchemaNoOps() {
        CapturingSchema schema = new CapturingSchema();
        // 这些方法不应抛出异常
        schema.dropIfExists("any_table");
        schema.drop("any_table");
        schema.rename("a", "b");
        assertFalse(schema.hasTable("any_table"));
        assertFalse(schema.hasColumn("any_table", "any_column"));
    }

    // ==================== ParsedTable / ParsedColumn 测试 ====================

    @Test
    @DisplayName("ParsedTable 列合并：同名列覆盖，新列追加")
    void testParsedTableColumnMerge() {
        ParsedTable table = new ParsedTable("users");
        table.addColumn(new ParsedColumn("id", "bigInteger", false, true, false, true, false, null, null, null, null));
        table.addColumn(new ParsedColumn("name", "string", false, false, false, false, false, 50, null, null, null));
        assertEquals(2, table.columnCount());

        // 同名列覆盖
        table.addColumn(new ParsedColumn("name", "string", true, false, false, false, false, 100, null, null, null));
        assertEquals(2, table.columnCount());
        assertTrue(table.getColumns().get(1).isNullable());
        assertEquals(100, table.getColumns().get(1).getLength());

        // 新列追加
        table.addColumn(new ParsedColumn("email", "string", false, false, true, false, false, 100, null, null, null));
        assertEquals(3, table.columnCount());
    }

    @Test
    @DisplayName("ParsedTable 主键检测")
    void testParsedTablePrimaryKey() {
        ParsedTable table = new ParsedTable("users");
        table.addColumn(new ParsedColumn("id", "bigInteger", false, true, false, true, false, null, null, null, null));
        table.addColumn(new ParsedColumn("name", "string", false, false, false, false, false, 50, null, null, null));

        assertEquals("id", table.getPrimaryKeyColumn());
    }

    @Test
    @DisplayName("ParsedTable 软删除检测：含 deleted_at 列")
    void testParsedTableSoftDeletes() {
        ParsedTable table = new ParsedTable("users");
        table.addColumn(new ParsedColumn("id", "bigInteger", false, true, false, true, false, null, null, null, null));
        assertFalse(table.hasSoftDeletes());

        table.addColumn(new ParsedColumn("deleted_at", "timestamp", true, false, false, false, false, null, null, null, null));
        assertTrue(table.hasSoftDeletes());
    }

    @Test
    @DisplayName("ParsedColumn.from() 从 ColumnDefinition 转换")
    void testParsedColumnFromColumnDefinition() {
        CapturingSchema schema = new CapturingSchema();
        new TestCreateUsersMigration().up(schema);
        Blueprint bp = schema.getBlueprints().get(0);

        ColumnDefinition idCol = bp.getColumns().get(0);
        ParsedColumn parsed = ParsedColumn.from(idCol);

        assertEquals("id", parsed.getName());
        assertEquals("bigInteger", parsed.getMigrationType());
        assertTrue(parsed.isPrimary());
        assertTrue(parsed.isAutoIncrement());
    }

    // ==================== ReverseModelGenerator 类型映射测试 ====================

    @Test
    @DisplayName("迁移类型到 Java 类型映射")
    void testMapMigrationTypeToJava() {
        ReverseModelGenerator generator = new ReverseModelGenerator(null);

        assertEquals("Long", generator.mapMigrationTypeToJava("bigInteger", false));
        assertEquals("Long", generator.mapMigrationTypeToJava("bigInteger", true));
        assertEquals("Long", generator.mapMigrationTypeToJava("integer", true));
        assertEquals("Integer", generator.mapMigrationTypeToJava("integer", false));
        assertEquals("Integer", generator.mapMigrationTypeToJava("tinyInteger", false));
        assertEquals("Integer", generator.mapMigrationTypeToJava("smallInteger", false));
        assertEquals("String", generator.mapMigrationTypeToJava("string", false));
        assertEquals("String", generator.mapMigrationTypeToJava("text", false));
        assertEquals("String", generator.mapMigrationTypeToJava("json", false));
        assertEquals("Boolean", generator.mapMigrationTypeToJava("boolean", false));
        assertEquals("BigDecimal", generator.mapMigrationTypeToJava("decimal", false));
        assertEquals("Float", generator.mapMigrationTypeToJava("float", false));
        assertEquals("Double", generator.mapMigrationTypeToJava("double", false));
        assertEquals("String", generator.mapMigrationTypeToJava("timestamp", false));
        assertEquals("String", generator.mapMigrationTypeToJava("date", false));
        assertEquals("byte[]", generator.mapMigrationTypeToJava("binary", false));
        assertEquals("String", generator.mapMigrationTypeToJava("unknown_type", false));
        assertEquals("String", generator.mapMigrationTypeToJava(null, false));
        assertEquals("String", generator.mapMigrationTypeToJava("", false));
    }

    // ==================== ReverseModelGenerator 源代码生成测试 ====================

    @Test
    @DisplayName("从 ParsedTable 生成 Model 源代码")
    void testGenerateFromParsedTable() throws IOException {
        ReverseModelGenerator generator = new ReverseModelGenerator(null);

        ParsedTable table = new ParsedTable("users");
        table.addColumn(new ParsedColumn("id", "bigInteger", false, true, false, true, false, null, null, null, null));
        table.addColumn(new ParsedColumn("name", "string", false, false, false, false, false, 50, null, null, null));
        table.addColumn(new ParsedColumn("email", "string", false, false, true, false, false, 100, null, null, null));
        table.addColumn(new ParsedColumn("deleted_at", "timestamp", true, false, false, false, false, null, null, null, null));

        String outputPath = generator.generateFromParsedTable(table, "com.example.app", tempDir.getAbsolutePath(), true);

        // 验证文件存在
        Path expectedFile = Path.of(tempDir.getAbsolutePath(), "com/example/app/models", "User.java");
        assertEquals(expectedFile.toAbsolutePath().toString(), outputPath);
        assertTrue(Files.exists(expectedFile));

        // 验证文件内容
        String content = Files.readString(expectedFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("package com.example.app.models;"));
        assertTrue(content.contains("public class User extends BaseModel<User, Long>"));
        assertTrue(content.contains("@Table(name = \"users\")"));
        assertTrue(content.contains("@Primary"));
        assertTrue(content.contains("private Long id;"));
        assertTrue(content.contains("@Column(name = \"name\")"));
        assertTrue(content.contains("private String name;"));
        assertTrue(content.contains("@Column(name = \"email\")"));
        assertTrue(content.contains("private String email;"));
        assertTrue(content.contains("protected boolean softDeleting()"));
        assertTrue(content.contains("return true;"));
        assertTrue(content.contains("由 make:model-from-migration 从迁移文件生成"));
    }

    @Test
    @DisplayName("从 ParsedTable 生成含 BigDecimal 的 Model")
    void testGenerateFromParsedTableWithBigDecimal() throws IOException {
        ReverseModelGenerator generator = new ReverseModelGenerator(null);

        ParsedTable table = new ParsedTable("products");
        table.addColumn(new ParsedColumn("id", "bigInteger", false, true, false, true, false, null, null, null, null));
        table.addColumn(new ParsedColumn("price", "decimal", false, false, false, false, false, null, 10, 2, null));

        String outputPath = generator.generateFromParsedTable(table, "com.example", tempDir.getAbsolutePath(), true);

        String content = Files.readString(Path.of(outputPath), StandardCharsets.UTF_8);
        assertTrue(content.contains("import java.math.BigDecimal;"));
        assertTrue(content.contains("private BigDecimal price;"));
    }

    @Test
    @DisplayName("generateFromParsedTable 拒绝空表定义")
    void testGenerateFromParsedTableEmptyTable() {
        ReverseModelGenerator generator = new ReverseModelGenerator(null);
        assertThrows(IllegalStateException.class, () -> {
            generator.generateFromParsedTable(new ParsedTable("empty"), "com.example", tempDir.getAbsolutePath(), true);
        });
    }

    @Test
    @DisplayName("generateFromParsedTable 拒绝覆盖已存在文件（无 --force）")
    void testGenerateFromParsedTableNoForce() throws IOException {
        ReverseModelGenerator generator = new ReverseModelGenerator(null);

        ParsedTable table = new ParsedTable("items");
        table.addColumn(new ParsedColumn("id", "bigInteger", false, true, false, true, false, null, null, null, null));

        // 第一次生成成功
        generator.generateFromParsedTable(table, "com.example", tempDir.getAbsolutePath(), true);

        // 第二次不使用 force 应失败
        assertThrows(IllegalStateException.class, () -> {
            generator.generateFromParsedTable(table, "com.example", tempDir.getAbsolutePath(), false);
        });
    }

    // ==================== MigrationFileParser 测试 ====================

    /**
     * 创建测试用迁移 .java 文件。
     */
    private Path createTestMigrationFile(String fileName, String className, String tableDef) throws IOException {
        String source = String.format(
            "package test.migrations;\n" +
            "\n" +
            "import com.weacsoft.jaravel.vendor.migration.Migration;\n" +
            "import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;\n" +
            "import com.weacsoft.jaravel.vendor.migration.Schema;\n" +
            "\n" +
            "@MigrationAnnotation\n" +
            "public class %s implements Migration {\n" +
            "    @Override\n" +
            "    public void up(Schema schema) {\n" +
            "        %s\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public void down(Schema schema) {\n" +
            "        schema.dropIfExists(\"test_table\");\n" +
            "    }\n" +
            "}\n",
            className, tableDef);

        Path file = tempDir.toPath().resolve(fileName);
        Files.write(file, source.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    @DisplayName("MigrationFileParser 从 .java 文件解析表结构")
    void testParseFromJavaFiles() throws IOException {
        createTestMigrationFile("Migration_2024_01_01_CreateTestTable.java",
            "Migration_2024_01_01_CreateTestTable",
            "schema.create(\"test_table\", table -> {\n" +
            "            table.id();\n" +
            "            table.string(\"name\", 50);\n" +
            "            table.text(\"description\").nullable();\n" +
            "            table.timestamps();\n" +
            "            table.softDeletes();\n" +
            "        });");

        MigrationFileParser parser = new MigrationFileParser();
        Map<String, ParsedTable> tables = parser.parseAll(tempDir.getAbsolutePath());

        assertEquals(1, tables.size());
        assertTrue(tables.containsKey("test_table"));

        ParsedTable table = tables.get("test_table");
        assertEquals("test_table", table.getTableName());
        assertEquals(6, table.columnCount());

        // 验证列
        List<ParsedColumn> columns = table.getColumns();
        assertEquals("id", columns.get(0).getName());
        assertEquals("bigInteger", columns.get(0).getMigrationType());
        assertTrue(columns.get(0).isPrimary());
        assertTrue(columns.get(0).isAutoIncrement());

        assertEquals("name", columns.get(1).getName());
        assertEquals("string", columns.get(1).getMigrationType());

        assertEquals("description", columns.get(2).getName());
        assertEquals("text", columns.get(2).getMigrationType());
        assertTrue(columns.get(2).isNullable());

        // 验证软删除检测
        assertTrue(table.hasSoftDeletes());
        assertEquals("id", table.getPrimaryKeyColumn());
    }

    @Test
    @DisplayName("MigrationFileParser 合并多个迁移对同一张表的定义")
    void testParseMultipleMigrationsSameTable() throws IOException {
        createTestMigrationFile("Migration_2024_01_01_CreateTestTable.java",
            "Migration_2024_01_01_CreateTestTable",
            "schema.create(\"test_table\", table -> {\n" +
            "            table.id();\n" +
            "            table.string(\"name\", 50);\n" +
            "            table.timestamps();\n" +
            "        });");

        createTestMigrationFile("Migration_2024_01_02_AddPhoneToTestTable.java",
            "Migration_2024_01_02_AddPhoneToTestTable",
            "schema.table(\"test_table\", table -> {\n" +
            "            table.string(\"phone\", 20).nullable();\n" +
            "        });");

        MigrationFileParser parser = new MigrationFileParser();
        Map<String, ParsedTable> tables = parser.parseAll(tempDir.getAbsolutePath());

        assertEquals(1, tables.size());
        ParsedTable table = tables.get("test_table");
        // id + name + created_at + updated_at + phone = 5
        assertEquals(5, table.columnCount());
        assertTrue(table.getColumns().stream().anyMatch(c -> c.getName().equals("phone")));
    }

    @Test
    @DisplayName("MigrationFileParser listTables 返回排序后的表名列表")
    void testListTables() throws IOException {
        createTestMigrationFile("Migration_2024_01_01_CreateAlphaTable.java",
            "Migration_2024_01_01_CreateAlphaTable",
            "schema.create(\"alpha_table\", table -> { table.id(); });");

        createTestMigrationFile("Migration_2024_01_02_CreateBetaTable.java",
            "Migration_2024_01_02_CreateBetaTable",
            "schema.create(\"beta_table\", table -> { table.id(); });");

        MigrationFileParser parser = new MigrationFileParser();
        List<String> tableNames = parser.listTables(tempDir.getAbsolutePath());

        assertEquals(2, tableNames.size());
        assertEquals("alpha_table", tableNames.get(0));
        assertEquals("beta_table", tableNames.get(1));
    }

    @Test
    @DisplayName("MigrationFileParser findTable 查找特定表")
    void testFindTable() throws IOException {
        createTestMigrationFile("Migration_2024_01_01_CreateTestTable.java",
            "Migration_2024_01_01_CreateTestTable",
            "schema.create(\"test_table\", table -> { table.id(); table.string(\"name\"); });");

        MigrationFileParser parser = new MigrationFileParser();
        ParsedTable table = parser.findTable(tempDir.getAbsolutePath(), "test_table");

        assertNotNull(table);
        assertEquals("test_table", table.getTableName());
        assertEquals(2, table.columnCount());

        // 不存在的表
        assertNull(parser.findTable(tempDir.getAbsolutePath(), "nonexistent"));
    }

    @Test
    @DisplayName("MigrationFileParser 目录不存在时返回空 Map")
    void testParseAllNonexistentDir() {
        MigrationFileParser parser = new MigrationFileParser();
        Map<String, ParsedTable> tables = parser.parseAll("/nonexistent/path/migrations");
        // 应回退到 classpath，可能找到一些迁移类，也可能为空
        // 关键是不抛出异常
        assertNotNull(tables);
    }
}
