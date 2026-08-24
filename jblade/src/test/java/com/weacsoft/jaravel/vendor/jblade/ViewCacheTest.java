package com.weacsoft.jaravel.vendor.jblade;

import com.weacsoft.jaravel.vendor.jblade.view.BladeView;
import com.weacsoft.jaravel.vendor.jblade.view.ViewFacade;
import com.weacsoft.jaravel.vendor.jblade.view.ViewManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ViewCache 静态方法测试：recompile() 全量重编译进缓存（不输出预编译包）、clear() 清空缓存。
 */
class ViewCacheTest {

    static BladeEngine engine;

    @BeforeAll
    static void setUpView() {
        ViewManager manager = new ViewManager();
        engine = new BladeEngine("templates");
        manager.register(new BladeView("blade", engine));
        ViewFacade.bind(manager);
    }

    @AfterAll
    static void tearDown() {
        ViewCache.clear();
    }

    /**
     * recompile() 应扫描并编译模板目录下全部模板，写入内存字节码缓存，
     * 返回成功编译的模板数量（至少 1 个）。
     */
    @Test
    void recompileCompilesAllTemplatesIntoCache() {
        int n = ViewCache.recompile();
        assertTrue(n > 0, "recompile() 应至少编译一个模板，实际: " + n);
        assertTrue(engine.templateClassCacheSize() > 0, "recompile() 后字节码缓存应非空");
    }

    /**
     * recompile() 与 rebuild() 的唯一区别：不输出预编译包 templates.jblade.zip。
     * 即便工作目录存在 storage 目录，recompile() 也不应生成该 zip。
     */
    @Test
    void recompileProducesNoPrecompiledZip() {
        File zip = new File("storage" + File.separator + "framework" + File.separator
                + "views" + File.separator + "templates.jblade.zip");
        if (zip.exists()) {
            assertTrue(zip.delete(), "测试前应先清理遗留的预编译包");
        }
        ViewCache.recompile();
        assertFalse(zip.exists(), "recompile() 不应输出预编译包 templates.jblade.zip");
    }

    /**
     * clear() 应清空全部缓存，并返回清空前字节码缓存条目数（>0）。
     */
    @Test
    void clearEmptiesCache() {
        ViewCache.recompile();
        int before = ViewCache.clear();
        assertTrue(before > 0, "clear() 前应已有缓存，返回: " + before);
        assertEquals(0, engine.templateClassCacheSize(), "clear() 后字节码缓存应为空");
    }
}
