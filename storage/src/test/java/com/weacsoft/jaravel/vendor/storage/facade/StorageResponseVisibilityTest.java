package com.weacsoft.jaravel.vendor.storage.facade;

import com.weacsoft.jaravel.vendor.core.lookup.GlobalBeanProvider;
import com.weacsoft.jaravel.vendor.core.lookup.GlobalLookup;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.storage.StorageManager;
import com.weacsoft.jaravel.vendor.storage.contract.Visibility;
import com.weacsoft.jaravel.vendor.storage.local.LocalFilesystemDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Storage.response(disk, path)} 的可见性与响应头行为（审计 L4 / N2 / N3）。
 * <p>
 * 修复前：不论文件可见性一律下发 {@code Cache-Control: public, max-age=3600} ——
 * 私有文件（证件照/发票）经 CDN 或共享代理会被缓存给其他人。
 * 修复后：按 {@code fs.visibility(path)} 分流，取不到可见性时按 PRIVATE 兜底（安全默认）。
 * <p>
 * 装配要点：{@code Storage} 是静态门面，经 {@code Facade.resolve} 取 {@code StorageManager}；
 * 单测用 {@link GlobalLookup#install} 注入，并在 {@link AfterEach} 卸载（静态全局，避免串测）。
 */
class StorageResponseVisibilityTest {

    @TempDir
    Path root;

    private StorageManager manager;

    @BeforeEach
    void setUp() {
        manager = new StorageManager();
        manager.registerDriver(new LocalFilesystemDriver());
        // 私有盘：默认 PRIVATE（LocalFilesystem 默认可见性）
        manager.registerDisk("priv", com.weacsoft.jaravel.vendor.storage.contract.DiskDefinition
                .local(root.resolve("priv").toString()));
        // 公共盘：Windows 无法读 POSIX 权限（visibility() 失败），用桩固定 PUBLIC 语义
        manager.registerDisk("pub", new com.weacsoft.jaravel.vendor.storage.local.LocalFilesystem(
                "pub", root.resolve("pub").toString(), null, Visibility.PUBLIC) {
            @Override
            public Visibility visibility(String path) {
                return Visibility.PUBLIC;
            }
        });
        // 可见性不可用的盘：visibility() 抛异常 → 必须按私有兜底（安全默认，审计 F4）
        manager.registerDisk("broken", new com.weacsoft.jaravel.vendor.storage.local.LocalFilesystem(
                "broken", root.resolve("broken").toString(), null, Visibility.PRIVATE) {
            @Override
            public Visibility visibility(String path) {
                throw new UnsupportedOperationException("模拟不支持读取可见性的驱动");
            }
        });

        manager.disk("priv").put("invoice.pdf", "secret".getBytes());
        manager.disk("pub").put("a.css", "body{}".getBytes());
        manager.disk("broken").put("x.txt", "x".getBytes());

        GlobalLookup.install(new TestProvider(manager));
    }

    @AfterEach
    void tearDown() {
        GlobalLookup.uninstall();
    }

    private static String header(Response response, String name) {
        List<String> values = response.getHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    @Test
    void privateFileMustNotBePubliclyCacheable() {
        Response response = Storage.response("priv", "invoice.pdf");

        assertNotNull(response);
        assertEquals("private, no-store", header(response, "Cache-Control"),
                "私有文件必须禁止共享缓存（修复前是 public, max-age=3600）");
        assertEquals("nosniff", header(response, "X-Content-Type-Options"),
                "预览响应必须禁止内容嗅探（防上传 .html/.svg 后的同源 XSS）");
        String disposition = header(response, "Content-Disposition");
        assertNotNull(disposition, "内联预览应带文件名");
        assertTrue(disposition.startsWith("inline;"), disposition);
        assertTrue(disposition.contains("invoice.pdf"), disposition);
    }

    @Test
    void publicFileKeepsPublicCache() {
        Response response = Storage.response("pub", "a.css");

        assertNotNull(response);
        assertEquals("public, max-age=3600", header(response, "Cache-Control"),
                "公共资源仍应可缓存（不要误伤）");
    }

    @Test
    void missingFileReturns404() {
        Response response = Storage.response("priv", "nope.pdf");
        assertEquals(404, response.getStatus());
    }

    @Test
    void unavailableVisibilityFallsBackToNoStore() {
        Response response = Storage.response("broken", "x.txt");

        assertNotNull(response);
        assertEquals("private, no-store", header(response, "Cache-Control"),
                "可见性不可用时必须按私有处理（安全默认 / fail-closed）");
    }

    /** 最小的全局 Bean 提供者：只为把 StorageManager 交给 Facade */
    private static final class TestProvider implements GlobalBeanProvider {

        private final StorageManager manager;

        TestProvider(StorageManager manager) {
            this.manager = manager;
        }

        @Override
        public Object bean(Class<?> type) {
            return StorageManager.class.equals(type) ? manager : null;
        }

        @Override
        public Object bean(String name) {
            return null;
        }

        @Override
        public Object bean(String name, Class<?> type) {
            return bean(type);
        }

        @Override
        public boolean contains(String name) {
            return false;
        }

        @Override
        public List<String> beanNames() {
            return List.of();
        }

        @Override
        public void registerSingleton(String name, Object instance) {
            // 测试替身无需注册能力
        }
    }
}