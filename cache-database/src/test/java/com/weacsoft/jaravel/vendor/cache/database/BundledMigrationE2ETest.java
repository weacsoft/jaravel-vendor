package com.weacsoft.jaravel.vendor.cache.database;

import com.weacsoft.jaravel.vendor.migration.engine.Migrator;
import com.weacsoft.jaravel.vendor.migration.engine.MigrationScanner;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端：模块内置迁移（对齐 vendor:publish 发布产物）→ 运行时内存编译 →
 * 在 H2 上执行 migrate → 驱动真实读写 → rollback。
 * <p>
 * 覆盖 0.1.3 架构对齐的核心链路：
 * 「模块自带迁移 Java 源（打包在 jar 内）→ 发布到业务工程迁移目录 → artisan migrate 建表
 * → 驱动经由 database 模块执行器 + migration 模块方言读写」。
 */
class BundledMigrationE2ETest {

    @TempDir
    Path tempDir;

    private static int countTable(JdbcDataSource ds, String tableName) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?")) {
            ps.setString(1, tableName.toUpperCase());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return 0;
        }
    }

    @Test
    void publishBundledMigrationThenMigrateAndUseDriver() throws Exception {
        // 1) 模拟 vendor:publish：读取模块 jar 内置迁移资源，落盘到业务工程迁移目录
        Path migDir = Files.createDirectories(tempDir.resolve("src/main/java/migrations"));
        try (InputStream in = CacheDatabaseMigrationPublishable.class.getClassLoader()
                .getResourceAsStream(CacheDatabaseMigrationPublishable.CLASSPATH_RESOURCE)) {
            assertTrue(in != null, "内置迁移资源必须存在");
            Path file = migDir.resolve(CacheDatabaseMigrationPublishable.FILE_NAME);
            Files.write(file, in.readAllBytes());
        }
        // 注意：真实发布流程会把 package 重写为工程迁移包（见 VendorPublishCommandTest 断言）；
        // 此处直接使用模块默认包编译即可（Migrator 不依赖包名）

        // 2) H2 内存库（模拟业务工程数据源）
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:cache_e2e_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");

        // 3) 内存编译（DIRECTORY 模式）+ 迁移引擎执行 up
        MigrationScanner scanner = new MigrationScanner();
        try {
            scanner.compileFromFile(migDir.resolve(CacheDatabaseMigrationPublishable.FILE_NAME).toFile());
            assertEquals(1, scanner.getAllMigrationClassNames().size(),
                    "应恰好编译出 1 个迁移类");

            Migrator migrator = new Migrator(Map.of("sqlite", ds), "migrations", scanner);
            List<String> executed = migrator.run();
            assertEquals(List.of("Migration_20240101_CreateJaravelCacheTable"), executed,
                    "migrate 应执行内置迁移（H2 上建表成功证明方言 DDL 正确）");

            // 4) 驱动经由 database 模块执行器 + migration 模块方言真实读写
            DatabaseCacheDriver driver = new DatabaseCacheDriver(ds);
            assertTrue(driver.put("greeting", "hello jaravel", 60), "put 应成功（upsert 建表后落库）");
            assertEquals("hello jaravel", driver.get("greeting"), "get 应命中");
            assertTrue(driver.exists("greeting"), "exists 应命中");
            assertEquals(1, driver.allKeys().size(), "allKeys 应返回 1 个键");

            // 覆盖写入（再次命中 upsert 冲突分支）
            assertTrue(driver.put("greeting", "second", 60), "覆盖写入应走 upsert 更新分支");
            assertEquals("second", driver.get("greeting"), "覆盖读回应为新值");

            // 删除
            assertTrue(driver.remove("greeting"), "remove 应成功");
            assertFalse(driver.exists("greeting"), "删除后 exists 应未命中");
            assertEquals(0, driver.allKeys().size(), "删除后 allKeys 应为空");

            // 5) rollback（down 回滚建表）
            List<String> rolled = migrator.rollback(1);
            assertEquals(List.of("Migration_20240101_CreateJaravelCacheTable"), rolled,
                    "rollback 应回滚内置迁移");
            assertEquals(0, countTable(ds, "jaravel_cache"), "down 后 cache 表应被删除");
        } finally {
            scanner.finish();
        }
    }
}
