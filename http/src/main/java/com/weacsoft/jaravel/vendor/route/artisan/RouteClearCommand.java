package com.weacsoft.jaravel.vendor.route.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.route.RouteCache;

/**
 * Artisan 命令：{@code route:clear}，清空路由派生结果的全部内存缓存。
 * <p>
 * 对齐 Laravel {@code php artisan route:clear}。清空 {@link RouteCache} 中所有
 * Router / RouteDefinition 的派生结果条目（完整 URI / 名称 / 中间件链 / 别名索引等）。
 * 清空后下一次请求会自动按需重建，不会产生错误结果。
 *
 * @see RouteCache#clear()
 * @see RouteCacheCommand
 */
public class RouteClearCommand extends ArtisanCommand {

    @Override
    public String signature() {
        return "route:clear";
    }

    @Override
    public String description() {
        return "清空路由别名索引、URI 反查与中间件链的内存缓存";
    }

    @Override
    public int handle() {
        int before = RouteCache.size();
        RouteCache.clear();
        info("路由缓存已清空（之前 " + before + " 个条目）");
        return 0;
    }
}
