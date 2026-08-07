package com.weacsoft.jaravel.vendor.jblade.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.jblade.ViewCache;

/**
 * Artisan 命令：{@code view:clear}，清除全部 Blade 模板编译缓存。
 * <p>
 * 对齐 Laravel {@code php artisan view:clear}。清除范围：
 * <ul>
 *   <li>内存字节码缓存（模板名→字节码，view:cache 预热的主要成果）；</li>
 *   <li>模板类缓存（内存中已加载的 Class 对象）；</li>
 *   <li>可选外部 CacheStore 中的编译产物；</li>
 *   <li>（模板继承结构分析结果）。</li>
 * </ul>
 * 底层能力由静态方法 {@link ViewCache#clear()} 提供，可在代码中直接调用。
 */
public class ViewClearCommand extends ArtisanCommand {

    @Override
    public String signature() {
        return "view:clear";
    }

    @Override
    public String description() {
        return "清除全部 Blade 模板编译缓存与 ";
    }

    @Override
    public int handle() {
        int cleared;
        try {
            cleared = ViewCache.clear();
        } catch (RuntimeException e) {
            error("模板缓存清除失败: " + e.getMessage());
            return 1;
        }
        info("模板缓存已清除：" + cleared + " 个模板");
        return 0;
    }
}
