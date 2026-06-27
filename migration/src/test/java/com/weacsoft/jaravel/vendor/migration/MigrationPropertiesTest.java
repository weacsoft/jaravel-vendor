package com.weacsoft.jaravel.vendor.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MigrationProperties} 纯 POJO 测试。
 * <p>
 * 验证默认值与各属性的 getter/setter 行为，确认该配置类已脱离 Spring 注解、
 * 可在独立运行场景下通过手动 set 完成配置绑定。
 */
class MigrationPropertiesTest {

    /**
     * 验证默认值：模块默认启用、表名 migrations、源类型 DIRECTORY、目录 migrations、
     * jar 路径为空、不打包进 jar、不自动运行。
     */
    @Test
    void testDefaults() {
        MigrationProperties props = new MigrationProperties();

        assertTrue(props.isEnabled(), "默认应启用迁移模块");
        assertEquals("migrations", props.getTable(), "默认迁移记录表名为 migrations");
        assertEquals(MigrationSource.DIRECTORY, props.getSource(), "默认迁移源为 DIRECTORY");
        assertEquals("migrations", props.getDirectory(), "默认迁移目录为 migrations");
        assertEquals("", props.getJarPath(), "默认 jar 路径为空字符串");
        assertFalse(props.isPackageInJar(), "默认不将迁移目录打包进 jar");
        assertFalse(props.isAutoRun(), "默认不在启动时自动执行 migrate");
    }

    /**
     * 验证各属性的 setter 均能正确回写并通过 getter 读取。
     */
    @Test
    void testSetters() {
        MigrationProperties props = new MigrationProperties();

        // enabled
        props.setEnabled(false);
        assertFalse(props.isEnabled());

        // table
        props.setTable("custom_migrations");
        assertEquals("custom_migrations", props.getTable());

        // source - 覆盖三种迁移源
        props.setSource(MigrationSource.JAR);
        assertEquals(MigrationSource.JAR, props.getSource());

        props.setSource(MigrationSource.CLASSPATH);
        assertEquals(MigrationSource.CLASSPATH, props.getSource());

        props.setSource(MigrationSource.DIRECTORY);
        assertEquals(MigrationSource.DIRECTORY, props.getSource());

        // directory
        props.setDirectory("/path/to/migrations");
        assertEquals("/path/to/migrations", props.getDirectory());

        // jarPath
        props.setJarPath("/path/to/migrations.jar");
        assertEquals("/path/to/migrations.jar", props.getJarPath());

        // packageInJar
        props.setPackageInJar(true);
        assertTrue(props.isPackageInJar());

        // autoRun
        props.setAutoRun(true);
        assertTrue(props.isAutoRun());

        // 再次切换布尔值，确认 setter 非恒真
        props.setEnabled(true);
        assertTrue(props.isEnabled());
        props.setPackageInJar(false);
        assertFalse(props.isPackageInJar());
        props.setAutoRun(false);
        assertFalse(props.isAutoRun());
    }
}
