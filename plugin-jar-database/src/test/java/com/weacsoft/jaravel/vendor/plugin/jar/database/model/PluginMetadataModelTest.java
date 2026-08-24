package com.weacsoft.jaravel.vendor.plugin.jar.database.model;

import com.weacsoft.jaravel.vendor.database.BaseModel;
import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import gaarason.database.annotation.Table;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PluginMetadataModel} 数据库模型定义单元测试（通过反射校验注解与契约）。
 */
class PluginMetadataModelTest {

    @Test
    void tableAnnotationMapsToPluginMetadata() {
        Table table = PluginMetadataModel.class.getAnnotation(Table.class);
        assertNotNull(table, "必须标注 @Table");
        assertEquals("plugin_metadata", table.name());
    }

    @Test
    void isAnnotatedAsRepository() {
        assertNotNull(PluginMetadataModel.class.getAnnotation(Repository.class),
                "必须标注 @Repository 以注册为 Spring Bean");
    }

    @Test
    void extendsBaseModelParameterizedByLong() throws Exception {
        assertTrue(BaseModel.class.isAssignableFrom(PluginMetadataModel.class),
                "必须继承 jaravel BaseModel");
        // 校验泛型父类 BaseModel<PluginMetadataModel, Long> 的主键类型实参为 Long
        java.lang.reflect.ParameterizedType pt =
                (java.lang.reflect.ParameterizedType) PluginMetadataModel.class.getGenericSuperclass();
        assertEquals(Long.class, pt.getActualTypeArguments()[1]);
    }

    @Test
    void idFieldIsPrimaryAndColumnAnnotated() throws Exception {
        Field id = PluginMetadataModel.class.getDeclaredField("id");
        assertNotNull(id.getAnnotation(Primary.class), "id 字段必须标注 @Primary");
        Column column = id.getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals("id", column.name());
    }

    @Test
    void complexFieldsAreColumnMapped() throws Exception {
        // 校验关键字段到数据库列的映射
        assertEquals("plugin_id", columnOf("pluginId"));
        assertEquals("version", columnOf("version"));
        assertEquals("jar_path", columnOf("jarPath"));
        assertEquals("state", columnOf("state"));
        assertEquals("shared_class_dependencies", columnOf("sharedClassDependencies"));
        assertEquals("component_classes", columnOf("componentClasses"));
        assertEquals("route_mappings", columnOf("routeMappings"));
        assertEquals("error_message", columnOf("errorMessage"));
        assertEquals("persisted", columnOf("persisted"));
    }

    private String columnOf(String fieldName) throws Exception {
        Field f = PluginMetadataModel.class.getDeclaredField(fieldName);
        Column c = f.getAnnotation(Column.class);
        assertNotNull(c, "字段 " + fieldName + " 必须标注 @Column");
        return c.name();
    }

    @Test
    void exposesStaticEloquentQueryMethods() throws Exception {
        // Eloquent 风格的静态查询入口
        Method query = PluginMetadataModel.class.getMethod("query");
        assertNotNull(query);
        Method all = PluginMetadataModel.class.getMethod("all");
        assertNotNull(all);
        Method find = PluginMetadataModel.class.getMethod("find", Long.class);
        assertNotNull(find);
    }
}
