package com.weacsoft.jaravel.vendor.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TableMigrator 测试：使用 H2 内存数据库验证 1 步迁移和 2 步迁移。
 * <p>
 * 测试场景：
 * <ol>
 *   <li>1 步迁移：源库 → 目标库直接迁移，验证表结构和数据完整性</li>
 *   <li>2 步迁移：源库 → 导出文件 → 导入目标库，验证中间文件正确性</li>
 *   <li>2 步迁移：选择性导入（只导入指定表）</li>
 *   <li>migrations 记录表迁移</li>
 * </ol>
 */
class TableMigratorTest {

    @TempDir
    File tempDir;

    private DataSource sourceDs;
    private DataSource targetDs;

    @BeforeEach
    void setUp() {
        // 使用 H2 内存数据库
        sourceDs = createH2DataSource("test_source_" + System.nanoTime());
        targetDs = createH2DataSource("test_target_" + System.nanoTime());

        // 在源库创建测试表和数据
        setupSourceData();
    }

    @AfterEach
    void tearDown() {
        // H2 内存库在连接关闭后自动销毁
    }

    /** 创建 H2 内存数据源 */
    private DataSource createH2DataSource(String dbName) {
        org.h2.jdbcx.JdbcConnectionPool pool = org.h2.jdbcx.JdbcConnectionPool.create(
            "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");
        return pool;
    }

    /** 在源库创建测试表和数据 */
    private void setupSourceData() {
        try (Connection conn = sourceDs.getConnection();
             Statement stmt = conn.createStatement()) {

            // users 表：包含自增主键、字符串、整数、布尔、时间戳
            stmt.execute("CREATE TABLE users (" +
                "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "age INT, " +
                "active BOOLEAN DEFAULT TRUE, " +
                "balance DECIMAL(10,2), " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");

            stmt.execute("INSERT INTO users (name, age, active, balance) VALUES " +
                "('Alice', 30, TRUE, 1000.50)," +
                "('Bob', 25, FALSE, 500.00)," +
                "('Charlie', NULL, TRUE, NULL)");

            // orders 表：包含外键关联（整数、字符串、日期）
            stmt.execute("CREATE TABLE orders (" +
                "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                "user_id INT NOT NULL, " +
                "product_name VARCHAR(200) NOT NULL, " +
                "quantity INT DEFAULT 1, " +
                "price DECIMAL(10,2) NOT NULL, " +
                "order_date DATE" +
                ")");

            stmt.execute("INSERT INTO orders (user_id, product_name, quantity, price, order_date) VALUES " +
                "(1, 'Laptop', 1, 999.99, '2024-01-15')," +
                "(1, 'Mouse', 2, 25.50, '2024-02-20')," +
                "(2, 'Keyboard', 1, 75.00, '2024-03-10')");

            // tags 表：只有字符串（测试简单表）
            stmt.execute("CREATE TABLE tags (" +
                "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                "name VARCHAR(50) NOT NULL UNIQUE" +
                ")");
            stmt.execute("INSERT INTO tags (name) VALUES ('java'), ('python'), ('go')");

            // migrations 记录表
            stmt.execute("CREATE TABLE migrations (" +
                "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                "migration VARCHAR(255) NOT NULL, " +
                "batch INT NOT NULL" +
                ")");
            stmt.execute("INSERT INTO migrations (migration, batch) VALUES " +
                "('2024_01_01_create_users', 1)," +
                "('2024_01_02_create_orders', 1)," +
                "('2024_01_03_create_tags', 2)");

        } catch (Exception e) {
            throw new RuntimeException("初始化源数据失败", e);
        }
    }

    // ==================== 1 步迁移测试 ====================

    @Test
    @DisplayName("1步迁移：migrateAll 迁移所有表（含 migrations）")
    void testMigrateAll() {
        TableMigrator migrator = new TableMigrator(sourceDs, targetDs);
        int count = migrator.migrateAll();

        // 4 张表：users, orders, tags, migrations
        assertEquals(4, count, "应迁移 4 张表");

        // 验证 users 表
        verifyTableExists(targetDs, "USERS");
        verifyTableRowCount(targetDs, "USERS", 3);
        verifyUsersData();

        // 验证 orders 表
        verifyTableExists(targetDs, "ORDERS");
        verifyTableRowCount(targetDs, "ORDERS", 3);

        // 验证 tags 表
        verifyTableExists(targetDs, "TAGS");
        verifyTableRowCount(targetDs, "TAGS", 3);

        // 验证 migrations 表
        verifyTableExists(targetDs, "MIGRATIONS");
        verifyTableRowCount(targetDs, "MIGRATIONS", 3);
    }

    @Test
    @DisplayName("1步迁移：migrateAll(false) 不含 migrations 表")
    void testMigrateAllWithoutMigrations() {
        TableMigrator migrator = new TableMigrator(sourceDs, targetDs);
        int count = migrator.migrateAll(false);

        assertEquals(3, count, "应迁移 3 张用户表");
        verifyTableExists(targetDs, "USERS");
        verifyTableExists(targetDs, "ORDERS");
        verifyTableExists(targetDs, "TAGS");
        assertFalse(tableExists(targetDs, "MIGRATIONS"), "migrations 表不应被迁移");
    }

    @Test
    @DisplayName("1步迁移：migrateTables 迁移指定表")
    void testMigrateTables() {
        TableMigrator migrator = new TableMigrator(sourceDs, targetDs);
        int count = migrator.migrateTables("users", "tags");

        assertEquals(2, count);
        verifyTableExists(targetDs, "USERS");
        verifyTableRowCount(targetDs, "USERS", 3);
        verifyTableExists(targetDs, "TAGS");
        verifyTableRowCount(targetDs, "TAGS", 3);
        assertFalse(tableExists(targetDs, "ORDERS"));
    }

    @Test
    @DisplayName("1步迁移：数据完整性验证（含 NULL 值、BigDecimal、Boolean）")
    void testDataIntegrity() {
        TableMigrator migrator = new TableMigrator(sourceDs, targetDs);
        migrator.migrateTable("users");

        try (Connection conn = targetDs.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM USERS ORDER BY ID")) {

            assertTrue(rs.next());
            assertEquals(1, rs.getInt("ID"));
            assertEquals("Alice", rs.getString("NAME"));
            assertEquals(30, rs.getInt("AGE"));
            assertTrue(rs.getBoolean("ACTIVE"));
            assertEquals(new BigDecimal("1000.50"), rs.getBigDecimal("BALANCE"));

            assertTrue(rs.next());
            assertEquals(2, rs.getInt("ID"));
            assertEquals("Bob", rs.getString("NAME"));
            assertEquals(25, rs.getInt("AGE"));
            assertFalse(rs.getBoolean("ACTIVE"));
            assertEquals(new BigDecimal("500.00"), rs.getBigDecimal("BALANCE"));

            assertTrue(rs.next());
            assertEquals(3, rs.getInt("ID"));
            assertEquals("Charlie", rs.getString("NAME"));
            // age 为 NULL
            assertEquals(0, rs.getInt("AGE"));
            assertTrue(rs.wasNull(), "AGE 应为 NULL");
            // balance 为 NULL
            assertNull(rs.getBigDecimal("BALANCE"), "BALANCE 应为 NULL");
        } catch (Exception e) {
            fail("数据完整性验证失败", e);
        }
    }

    // ==================== 2 步迁移测试 ====================

    @Test
    @DisplayName("2步迁移：export → import 全流程")
    void testTwoStepMigration() {
        File dumpFile = new File(tempDir, "dump.jvd");

        // 第一步：导出
        TableMigrator exporter = new TableMigrator(sourceDs, null);
        int exported = exporter.exportAll(dumpFile);

        assertEquals(4, exported, "应导出 4 张表");
        assertTrue(dumpFile.exists(), "导出文件应存在");
        assertTrue(dumpFile.length() > 0, "导出文件不应为空");

        // 第二步：导入（模拟不同时间、不同实例）
        TableMigrator importer = new TableMigrator(null, targetDs);
        int imported = importer.importAll(dumpFile);

        assertEquals(4, imported, "应导入 4 张表");

        // 验证数据
        verifyTableExists(targetDs, "USERS");
        verifyTableRowCount(targetDs, "USERS", 3);
        verifyUsersData();
        verifyTableExists(targetDs, "ORDERS");
        verifyTableRowCount(targetDs, "ORDERS", 3);
        verifyTableExists(targetDs, "TAGS");
        verifyTableRowCount(targetDs, "TAGS", 3);
        verifyTableExists(targetDs, "MIGRATIONS");
        verifyTableRowCount(targetDs, "MIGRATIONS", 3);
    }

    @Test
    @DisplayName("2步迁移：exportAll(false) 不含 migrations 表")
    void testTwoStepWithoutMigrations() {
        File dumpFile = new File(tempDir, "dump_no_mig.jvd");

        TableMigrator exporter = new TableMigrator(sourceDs, null);
        int exported = exporter.exportAll(dumpFile, false);
        assertEquals(3, exported);

        TableMigrator importer = new TableMigrator(null, targetDs);
        int imported = importer.importAll(dumpFile);
        assertEquals(3, imported);

        verifyTableExists(targetDs, "USERS");
        assertFalse(tableExists(targetDs, "MIGRATIONS"));
    }

    @Test
    @DisplayName("2步迁移：importTables 选择性导入")
    void testTwoStepSelectiveImport() {
        File dumpFile = new File(tempDir, "dump_selective.jvd");

        TableMigrator exporter = new TableMigrator(sourceDs, null);
        exporter.exportAll(dumpFile);

        // 只导入 users 和 tags
        TableMigrator importer = new TableMigrator(null, targetDs);
        int imported = importer.importTables(dumpFile, "users", "tags");

        assertEquals(2, imported);
        verifyTableExists(targetDs, "USERS");
        verifyTableRowCount(targetDs, "USERS", 3);
        verifyTableExists(targetDs, "TAGS");
        verifyTableRowCount(targetDs, "TAGS", 3);
        assertFalse(tableExists(targetDs, "ORDERS"), "orders 不应被导入");
        assertFalse(tableExists(targetDs, "MIGRATIONS"), "migrations 不应被导入");
    }

    @Test
    @DisplayName("2步迁移：导出后文件中可列出表名")
    void testListTablesInDump() {
        File dumpFile = new File(tempDir, "dump_list.jvd");

        TableMigrator exporter = new TableMigrator(sourceDs, null);
        exporter.exportAll(dumpFile);

        // 通过 importAll 间接验证（内部调用 listTablesInDump）
        TableMigrator importer = new TableMigrator(null, targetDs);
        // 这里不实际导入，只验证文件能被正确解析
        // listTablesInDump 是私有方法，通过 importAll 间接测试
        assertDoesNotThrow(() -> importer.importAll(dumpFile));
    }

    @Test
    @DisplayName("2步迁移：数据完整性验证（NULL、BigDecimal、Boolean 经过文件中转）")
    void testTwoStepDataIntegrity() {
        File dumpFile = new File(tempDir, "dump_integrity.jvd");

        new TableMigrator(sourceDs, null).exportTables(dumpFile, "users");
        new TableMigrator(null, targetDs).importTables(dumpFile, "users");

        try (Connection conn = targetDs.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM USERS ORDER BY ID")) {

            assertTrue(rs.next());
            assertEquals("Alice", rs.getString("NAME"));
            assertEquals(30, rs.getInt("AGE"));
            assertTrue(rs.getBoolean("ACTIVE"));
            assertEquals(new BigDecimal("1000.50"), rs.getBigDecimal("BALANCE"));

            assertTrue(rs.next());
            assertEquals("Bob", rs.getString("NAME"));
            assertEquals(25, rs.getInt("AGE"));
            assertFalse(rs.getBoolean("ACTIVE"));

            assertTrue(rs.next());
            assertEquals("Charlie", rs.getString("NAME"));
            assertEquals(0, rs.getInt("AGE"));
            assertTrue(rs.wasNull(), "AGE 应为 NULL");
            assertNull(rs.getBigDecimal("BALANCE"), "BALANCE 应为 NULL");
        } catch (Exception e) {
            fail("数据完整性验证失败", e);
        }
    }

    @Test
    @DisplayName("2步迁移：多次导入同一文件（覆盖已有表）")
    void testRepeatedImport() {
        File dumpFile = new File(tempDir, "dump_repeat.jvd");
        new TableMigrator(sourceDs, null).exportTables(dumpFile, "tags");

        TableMigrator importer = new TableMigrator(null, targetDs);
        importer.importTables(dumpFile, "tags");
        verifyTableRowCount(targetDs, "TAGS", 3);

        // 再次导入应覆盖（先 DROP IF EXISTS）
        importer.importTables(dumpFile, "tags");
        verifyTableRowCount(targetDs, "TAGS", 3);
    }

    @Test
    @DisplayName("错误处理：无效文件格式")
    void testInvalidFileFormat() {
        File badFile = new File(tempDir, "bad.jvd");
        try {
            // 写入一个非 jvd 格式的文件
            java.io.FileOutputStream fos = new java.io.FileOutputStream(badFile);
            fos.write("not a valid jvd file".getBytes());
            fos.close();
        } catch (Exception e) {
            fail("创建测试文件失败", e);
        }

        TableMigrator importer = new TableMigrator(null, targetDs);
        assertThrows(RuntimeException.class, () -> importer.importAll(badFile));
    }

    @Test
    @DisplayName("错误处理：未设置源数据源时导出应报错")
    void testExportWithoutSource() {
        TableMigrator exporter = new TableMigrator(null, null);
        assertThrows(IllegalStateException.class, () -> exporter.exportAll(new File(tempDir, "x.jvd")));
    }

    @Test
    @DisplayName("错误处理：未设置目标数据源时导入应报错")
    void testImportWithoutTarget() {
        TableMigrator importer = new TableMigrator(null, null);
        assertThrows(IllegalStateException.class, () -> importer.importAll(new File(tempDir, "x.jvd")));
    }

    // ==================== 辅助方法 ====================

    private void verifyUsersData() {
        try (Connection conn = targetDs.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT NAME FROM USERS ORDER BY ID")) {
            List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            assertEquals(3, names.size());
            assertEquals("Alice", names.get(0));
            assertEquals("Bob", names.get(1));
            assertEquals("Charlie", names.get(2));
        } catch (Exception e) {
            fail("验证 users 数据失败", e);
        }
    }

    private void verifyTableExists(DataSource ds, String tableName) {
        assertTrue(tableExists(ds, tableName), "表 " + tableName + " 应存在");
    }

    private boolean tableExists(DataSource ds, String tableName) {
        try (Connection conn = ds.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private void verifyTableRowCount(DataSource ds, String tableName, int expected) {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            rs.next();
            int actual = rs.getInt(1);
            assertEquals(expected, actual, "表 " + tableName + " 行数不匹配");
        } catch (Exception e) {
            fail("验证表 " + tableName + " 行数失败", e);
        }
    }
}
