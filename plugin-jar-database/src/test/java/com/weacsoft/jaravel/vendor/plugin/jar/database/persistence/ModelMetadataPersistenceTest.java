package com.weacsoft.jaravel.vendor.plugin.jar.database.persistence;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;
import com.weacsoft.jaravel.vendor.plugin.jar.model.PluginInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.persistence.MetadataPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModelMetadataPersistence} 数据库元数据持久化单元测试。
 * <p>
 * 由于该实现与 gaarason Eloquent（依赖 Spring 容器与 Druid 数据源）紧耦合，
 * 此处聚焦于：1) 入参空值 / 非磁盘插件的短路逻辑；2) 复杂字段的 JSON 序列化/反序列化
 * （通过反射访问私有方法，覆盖“插件数据库隔离”中字段编解码的核心逻辑）。
 * 真实 CRUD 需完整的 gaarason 数据源上下文，不在单元测试范围。
 */
class ModelMetadataPersistenceTest {

    private ModelMetadataPersistence persistence;

    @BeforeEach
    void setUp() {
        // 构造无需 Spring / 数据库上下文
        persistence = new ModelMetadataPersistence();
    }

    // ==================== 短路逻辑（不触达数据库） ====================

    @Test
    void saveNullAndNonPersistedAreNoops() {
        PluginInfo info = new PluginInfo("p", "1.0", "/x.jar");
        info.setPersisted(false);

        // 以下均不应抛出异常，也不触达数据库
        assertDoesNotThrow(() -> persistence.save(null));
        assertDoesNotThrow(() -> persistence.save(new PluginInfo())); // pluginId == null
        assertDoesNotThrow(() -> persistence.save(info));             // persisted == false
    }

    @Test
    void loadNullReturnsNull() {
        assertNull(persistence.load(null));
    }

    @Test
    void deleteNullIsNoop() {
        assertDoesNotThrow(() -> persistence.delete(null));
    }

    @Test
    void implementsMetadataPersistenceContract() {
        // 确保实现实现了插件核心模块定义的持久化契约
        assertTrue(persistence instanceof MetadataPersistence);
    }

    // ==================== JSON 字段编解码（反射访问私有方法） ====================

    private String invokeToJson(Object arg) throws Exception {
        Method m = ModelMetadataPersistence.class.getDeclaredMethod("toJson", Set.class);
        m.setAccessible(true);
        return (String) m.invoke(persistence, arg);
    }

    private Set<String> invokeFromJson(String json) throws Exception {
        Method m = ModelMetadataPersistence.class.getDeclaredMethod("fromJson", String.class);
        m.setAccessible(true);
        return (Set<String>) m.invoke(persistence, json);
    }

    private String invokeToJsonRoutes(Set<RouteInfo> routes) throws Exception {
        Method m = ModelMetadataPersistence.class.getDeclaredMethod("toJsonRouteInfos", Set.class);
        m.setAccessible(true);
        return (String) m.invoke(persistence, routes);
    }

    private Set<RouteInfo> invokeFromJsonRoutes(String json) throws Exception {
        Method m = ModelMetadataPersistence.class.getDeclaredMethod("fromJsonRouteInfos", String.class);
        m.setAccessible(true);
        return (Set<RouteInfo>) m.invoke(persistence, json);
    }

    @Test
    void toJsonSerializesStringSetAndHandlesEmptyNull() throws Exception {
        assertEquals("[]", invokeToJson(null));
        assertEquals("[]", invokeToJson(new HashSet<>()));

        String json = invokeToJson(Set.of("alpha", "beta"));
        // 反序列化回来应与原集合相等（与顺序无关）
        assertEquals(Set.of("alpha", "beta"), invokeFromJson(json));
    }

    @Test
    void fromJsonHandlesNullAndEmptyAndInvalid() throws Exception {
        assertTrue(invokeFromJson(null).isEmpty());
        assertTrue(invokeFromJson("").isEmpty());
        // 非法 JSON 回退为空集合，而非抛异常
        assertTrue(invokeFromJson("not-json").isEmpty());
    }

    @Test
    void routeInfosJsonRoundTrip() throws Exception {
        Set<RouteInfo> routes = Set.of(
                new RouteInfo("/users/{id}", HttpMethod.GET, "userController", "show", "application/json"),
                new RouteInfo("/users", HttpMethod.POST, "userController", "create", null));

        String json = invokeToJsonRoutes(routes);
        assertNotNull(json);
        assertTrue(json.contains("/users"));

        Set<RouteInfo> parsed = invokeFromJsonRoutes(json);
        // RouteInfo.equals 基于 path/method/beanName/methodName，与 produces 无关
        assertEquals(routes, parsed);
    }

    @Test
    void routeInfosJsonHandlesEmptyAndNull() throws Exception {
        assertEquals("[]", invokeToJsonRoutes(null));
        assertEquals("[]", invokeToJsonRoutes(new HashSet<>()));
        assertTrue(invokeFromJsonRoutes(null).isEmpty());
        assertTrue(invokeFromJsonRoutes("").isEmpty());
    }
}
