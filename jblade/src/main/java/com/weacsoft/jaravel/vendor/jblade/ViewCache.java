package com.weacsoft.jaravel.vendor.jblade;

import com.weacsoft.jaravel.vendor.core.view.View;
import com.weacsoft.jaravel.vendor.jblade.view.BladeView;
import com.weacsoft.jaravel.vendor.jblade.view.ViewFacade;

import java.io.File;

/**
 * 视图缓存管理（对应 {@code artisan view:cache} / {@code view:clear} 命令）。
 * <p>
 * 提供两个静态方法，分别用于<b>清除</b>与<b>重建</b>全部模板编译缓存：
 * <ul>
 *   <li>{@link #clear()}：清空内存一级缓存 + CacheStore 二级缓存 + PJAX 区域元数据；</li>
 *   <li>{@link #rebuild()}：先清空，再强制编译全部模板并写入缓存，同时输出预编译包
 *       （{@code storage/framework/views/templates.jblade.zip}）供 JRE 部署形态使用。</li>
 * </ul>
 * 实现依赖当前激活的 {@link BladeView}（经由 {@link ViewFacade} 取得底层 {@link BladeEngine}），
 * 因此必须在应用上下文就绪后调用（命令/运行时均可）。
 */
public final class ViewCache {

    private ViewCache() {
    }

    /**
     * 取得当前激活的 Blade 引擎；非 Blade 视图或未就绪时返回 null。
     */
    private static BladeEngine activeEngine() {
        try {
            View view = ViewFacade.getView();
            if (view instanceof BladeView) {
                return ((BladeView) view).getEngine();
            }
        } catch (Exception ignored) {
            // 视图未就绪
        }
        return null;
    }

    /**
     * 清除全部模板缓存（一级 + 二级 + 区域元数据）。
     *
     * @return 清除前缓存的模板数量；无可用引擎时返回 0
     */
    public static int clear() {
        BladeEngine engine = activeEngine();
        if (engine == null) {
            return 0;
        }
        int before = engine.templateClassCacheSize();
        engine.clearCache();
        engine.clearRegionMeta();
        return before;
    }

    /**
     * 重建全部模板缓存：清空后强制编译所有模板并写入缓存，
     * 同时输出预编译包（.jblade.zip）到 {@code storage/framework/views}（best-effort）。
     *
     * @return 成功编译的模板数量；无可用引擎时返回 0
     */
    public static int rebuild() {
        BladeEngine engine = activeEngine();
        if (engine == null) {
            return 0;
        }
        engine.clearCache();
        engine.clearRegionMeta();
        java.util.List<String> names = engine.scanTemplateNames();
        int ok = 0;
        for (String name : names) {
            try {
                engine.loadTemplate(name);
                ok++;
            } catch (Exception ignored) {
                // 单个模板编译失败不阻断其余
            }
        }
        // 额外输出预编译包（best-effort，供 JRE 部署形态使用）
        try {
            String outDir = "storage" + File.separator + "framework" + File.separator + "views";
            new BladePrecompiler(engine.getTemplateDir(), engine.getSuffix())
                    .compileAllToZip(outDir, "templates.jblade.zip");
        } catch (Exception ignored) {
            // 预编译包输出失败不影响缓存重建
        }
        return ok;
    }
}
