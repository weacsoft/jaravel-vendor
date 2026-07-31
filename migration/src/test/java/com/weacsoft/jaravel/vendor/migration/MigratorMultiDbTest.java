package com.weacsoft.jaravel.vendor.migration;

import com.weacsoft.jaravel.vendor.migration.autoconfigure.MigrationProperties;
import com.weacsoft.jaravel.vendor.migration.engine.MigrationScanner;
import com.weacsoft.jaravel.vendor.migration.engine.Migrator;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多数据库迁移测试：验证不同迁移按 connection() 落到不同数据源，
 * 且默认 "primary" 别名回退到主数据源。
 */
public class MigratorMultiDbTest {

    private DataSource h2(String name) throws Exception {
        // 使用 MySQL 兼容模式以支持 LONGBLOB / LONGTEXT 等方言语法
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private boolean tableExists(DataSource ds, String table) throws Exception {
        try (Connection c = ds.getConnection();
             ResultSet rs = c.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private int countMigrations(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM migrations")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Test
    public void testMultiDatabaseMigration() throws Exception {
        DataSource primary = h2("primary");
        DataSource mysql = h2("mysql");
        DataSource sqlite = h2("sqlite");

        Map<String, DataSource> aliases = new LinkedHashMap<>();
        aliases.put("primary", primary);
        aliases.put("mysql", mysql);
        aliases.put("sqlite", sqlite);

        MigrationProperties props = new MigrationProperties();
        props.setEnabled(true);
        props.setAutoRun(false);
        props.setTable("migrations");
        props.setDirectory("src/test/resources/multidb");

        // 构建 Migrator（绕过 MigrationExecutor 的 Spring 依赖，直接构造）
        MigrationScanner scanner = new MigrationScanner();
        scanner.compileFromDirectory(props.getDirectory());
        Migrator migrator = new Migrator(aliases, props.getTable(), scanner);

        // 执行迁移
        migrator.run();

        // 1. 各表落在对应的数据库
        assertTrue(tableExists(mysql, "mysql_table"), "mysql_table 应建在 mysql 库");
        assertTrue(tableExists(sqlite, "sqlite_table"), "sqlite_table 应建在 sqlite 库");
        assertTrue(tableExists(primary, "primary_table"), "primary_table 应建在 primary 库");

        // 2. 反向验证：表不应出现在错误的库
        assertFalse(tableExists(primary, "mysql_table"), "mysql_table 不应出现在 primary 库");
        assertFalse(tableExists(mysql, "sqlite_table"), "sqlite_table 不应出现在 mysql 库");

        // 3. 迁移记录分别写在各自库
        assertEquals(1, countMigrations(mysql), "mysql 库应有 1 条迁移记录");
        assertEquals(1, countMigrations(sqlite), "sqlite 库应有 1 条迁移记录");
        assertEquals(1, countMigrations(primary), "primary 库应有 1 条迁移记录");

        // 4. 回滚：每张表从各自的库被删除
        migrator.reset();
        assertFalse(tableExists(mysql, "mysql_table"), "回滚后 mysql_table 应被删除");
        assertFalse(tableExists(sqlite, "sqlite_table"), "回滚后 sqlite_table 应被删除");
        assertFalse(tableExists(primary, "primary_table"), "回滚后 primary_table 应被删除");
    }
}
