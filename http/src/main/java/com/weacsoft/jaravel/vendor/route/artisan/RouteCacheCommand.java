package com.weacsoft.jaravel.vendor.route.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.route.Route;
import com.weacsoft.jaravel.vendor.route.RouteCache;
import com.weacsoft.jaravel.vendor.route.Router;

/**
 * Artisan 命令：{@code route:cache}，预热路由派生结果的内存缓存。
 * <p>
 * 对齐 Laravel {@code php artisan route:cache}。从根 Router 出发，遍历全部路由
 * 并预先计算完整 URI / 名称 / 命名空间 / 中间件链 / 别名索引 / 别名→URL 索引，
 * 把首个请求的解析成本提前到启动期。
 * <p>
 * 缓存为纯 JVM 堆内存（{@link RouteCache}），不序列化、不落盘、不走外部 CacheStore。
 * 任何结构性写操作会自动整体失效，下次访问按需重建。
 *
 * @see RouteCache#warm(Router)
 * @see RouteClearCommand
 */
public class RouteCacheCommand extends ArtisanCommand {

    @Override
    public String signature() {
        return "route:cache";
    }

    @Override
    public String description() {
        return "预热路由别名索引、URI 反查与中间件链的内存缓存";
    }

    @Override
    public int handle() {
        Router root;
        try {
            root = Route.getRootRouter();
        } catch (IllegalStateException e) {
            error("路由未初始化：" + e.getMessage());
            return 1;
        }
        int count = RouteCache.warm(root);
        info("路由缓存已预热：" + count + " 条路由");
        return 0;
    }
}
