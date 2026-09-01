package com.weacsoft.jaravel.vendor.cache.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CacheDatabaseMigrationPublishable} 单元测试：
 * 声明正确 + 内置迁移资源确实打包在 classpath + 内容为合法迁移 Java 源。
 */
class CacheDatabaseMigrationPublishableTest {

    @AfterEach
    void tearDown() {
        com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry.clearForTest();
    }

    @Test
    void testDeclaration() {
        CacheDatabaseMigrationPublishable p = new CacheDatabaseMigrationPublishable();
        assertEquals("cache-database", p.tag());
        assertEquals(com.weacsoft.jaravel.vendor.core.publish.PublishType.MIGRATION, p.type());
        List<Map.Entry<String, String>> files = p.migrationFiles();
        assertEquals(1, files.size());
        assertEquals(CacheDatabaseMigrationPublishable.CLASSPATH_RESOURCE, files.get(0).getKey());
        assertEquals(CacheDatabaseMigrationPublishable.FILE_NAME, files.get(0).getValue());
    }

    @Test
    void testBundledMigrationResourceExistsOnClasspath() throws Exception {
        try (InputStream in = CacheDatabaseMigrationPublishable.class
                .getClassLoader()
                .getResourceAsStream(CacheDatabaseMigrationPublishable.CLASSPATH_RESOURCE)) {
            assertNotNull(in, "内置迁移资源必须打包在模块 jar 中: "
                    + CacheDatabaseMigrationPublishable.CLASSPATH_RESOURCE);
            String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(source.startsWith("package com.weacsoft.jaravel.database.migrations;"),
                    "内置迁移应声明框架默认迁移包（发布时会被重写为工程基包）");
            assertTrue(source.contains("@MigrationAnnotation"), "应带迁移标记注解");
            assertTrue(source.contains("schema.create(\"jaravel_cache\""), "应创建 jaravel_cache 表");
            assertTrue(source.contains("schema.dropIfExists(\"jaravel_cache\")"), "应含对称回滚");
        }
    }
}
