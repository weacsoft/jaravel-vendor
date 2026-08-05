package com.weacsoft.jaravel.vendor.jblade.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.jblade.ViewCache;

/**
 * Artisan 命令：{@code view:clear}，清除全部 Blade 模板编译缓存。
 * <p>
 * 对齐 Laravel {@code php artisan view:clear}。清除范围：
 * <ul>
 *   <li>一级缓存：内存中已加载的模板类；</li>
 *   <li>二级缓存：CacheStore 中的编译产物；</li>
 *   <li>PJAX 区域元数据（模板继承结构分析结果）。</li>
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
        return "清除全部 Blade 模板编译缓存与 PJAX 区域元数据";
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
