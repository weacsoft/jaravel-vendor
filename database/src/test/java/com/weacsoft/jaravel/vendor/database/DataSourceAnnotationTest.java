package com.weacsoft.jaravel.vendor.database;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataSource} 多数据源注解单元测试。
 */
class DataSourceAnnotationTest {

    @DataSource("secondaryDataSource")
    static class SecondaryEntity {
    }

    static class DefaultEntity {
    }

    @Test
    void retentionIsRuntimeAndTargetIsType() {
        Retention retention = DataSource.class.getAnnotation(Retention.class);
        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());

        Target target = DataSource.class.getAnnotation(Target.class);
        assertNotNull(target);
        boolean typeOnly = false;
        for (ElementType et : target.value()) {
            if (et == ElementType.TYPE) {
                typeOnly = true;
            }
        }
        assertTrue(typeOnly, "@DataSource 必须可作用于 TYPE");
    }

    @Test
    void valueReturnsConfiguredBeanName() {
        DataSource ds = SecondaryEntity.class.getAnnotation(DataSource.class);
        assertNotNull(ds, "SecondaryEntity 应标注 @DataSource");
        assertEquals("secondaryDataSource", ds.value());
    }

    @Test
    void absenceIndicatesDefaultDataSource() {
        // 未标注 @DataSource 的类应返回 null（表示使用默认 @Primary 数据源）
        assertNull(DefaultEntity.class.getAnnotation(DataSource.class));
    }
}
