package com.weacsoft.jaravel.vendor.plugin.jar.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weacsoft.jaravel.vendor.plugin.jar.annotation.HttpMethod;
import com.weacsoft.jaravel.vendor.plugin.jar.model.PluginInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JsonMetadataPersistence} JSON 元数据持久化单元测试（使用临时目录）。
 */
class JsonMetadataPersistenceTest {

    @TempDir
    Path tempDir;

    private JsonMetadataPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new JsonMetadataPersistence(tempDir, new ObjectMapper());
    }

    private PluginInfo newPersisted(String id) {
        PluginInfo info = new PluginInfo(id, "1.0.0", "/plugins/" + id + ".jar");
        info.setPersisted(true);
        info.setState(PluginInfo.State.ENABLED);
        info.setRouteMappings(Set.of(
                new RouteInfo("/" + id, HttpMethod.GET, id + "Controller", "index", null)));
        return info;
    }

    @Test
    void saveAndLoadRoundTrip() {
        PluginInfo info = newPersisted("demo");
        persistence.save(info);

        // 文件落盘
        assertTrue(Files.exists(tempDir.resolve("demo").resolve("metadata.json")));

        PluginInfo loaded = persistence.load("demo");
        assertNotNull(loaded);
        assertEquals("demo", loaded.getPluginId());
        assertEquals("1.0.0", loaded.getVersion());
        assertEquals(PluginInfo.State.ENABLED, loaded.getState());
        assertTrue(loaded.isPersisted());
        assertEquals(1, loaded.getRouteMappings().size());
    }

    @Test
    void loadReturnsNullWhenMissing() {
        assertNull(persistence.load("not-exist"));
    }

    @Test
    void saveSkipsNonPersistedPlugin() {
        PluginInfo info = new PluginInfo("mem", "1.0.0", "/tmp/mem.jar");
        info.setPersisted(false);
        persistence.save(info);

        // 不落盘，load 返回 null
        assertFalse(Files.exists(tempDir.resolve("mem").resolve("metadata.json")));
        assertNull(persistence.load("mem"));
    }

    @Test
    void loadAllReturnsOnlyPersistedAndSkipsSharedDir() throws Exception {
        persistence.save(newPersisted("a"));
        persistence.save(newPersisted("b"));

        // 创建一个 shared 目录（应被跳过）
        Files.createDirectories(tempDir.resolve("shared"));
        Files.writeString(tempDir.resolve("shared").resolve("metadata.json"), "{}");

        // 创建一个无 metadata.json 的目录（应被跳过）
        Files.createDirectories(tempDir.resolve("empty"));

        List<PluginInfo> all = persistence.loadAll();
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(i -> "a".equals(i.getPluginId())));
        assertTrue(all.stream().anyMatch(i -> "b".equals(i.getPluginId())));
    }

    @Test
    void deleteRemovesMetadataFileAndEmptyDir() {
        persistence.save(newPersisted("gone"));
        assertTrue(Files.exists(tempDir.resolve("gone").resolve("metadata.json")));

        persistence.delete("gone");
        assertFalse(Files.exists(tempDir.resolve("gone").resolve("metadata.json")));
        // 目录为空时一并删除
        assertFalse(Files.exists(tempDir.resolve("gone")));
        assertNull(persistence.load("gone"));
    }

    @Test
    void deleteIsNoopForMissing() {
        // 不存在时不抛异常
        persistence.delete("never-existed");
        persistence.save(null);  // null 安全
        assertNull(persistence.load(null));
    }
}
