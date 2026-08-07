package com.weacsoft.jaravel.vendor.route;

import com.weacsoft.jaravel.vendor.http.middleware.Middleware;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路由派生结果的<b>内存缓存</b>（进程内，纯 JVM 堆，不序列化、不落盘、不走外部 CacheStore）。
 *
 * <h3>缓存什么</h3>
 * 路由的完整 URI / 名称 / 命名空间、解析后的中间件列表、折叠后的处理链，以及 Router 级的
 * 全量路由表、<b>别名索引</b>与<b>别名→URL 索引</b>。这些都是「沿父级 Router 递归合并」的
 * 纯函数结果：注册完成后不再变化，却处在每请求的必经路径（处理链）与模板/代码的
 * {@code route('name')} 反查路径上，因此值得整体缓存。
 *
 * <h3>为什么不再用版本号</h3>
 * 早期实现用一个全局自增的「结构版本号」做失效：各对象记录自己算过的版本号，读取时比对，
 * 不等则丢弃。它能工作，但把缓存状态摊在了每个 Router / RouteDefinition 里，既没有统一的
 * 清理入口，也无法被命令行主动预热或清空。现在改为标准做法——<b>一个集中的内存缓存</b>：
 * <ul>
 *   <li>读取：{@link #of(Object)} 取得该对象的缓存条目，命中直接返回，未命中就地计算并写回；</li>
 *   <li>失效：任何结构性写操作调用 {@link #clear()} 整体清空，语义直白，不会漏失效；</li>
 *   <li>预热：{@link #warm(Router)} 主动算满，供 {@code artisan route:cache} 使用。</li>
 * </ul>
 * 「运行时惰性缓存 + 命令预热 + 命令清理」三者共用同一份存储，行为完全一致。
 *
 * <h3>清空为什么是安全且廉价的</h3>
 * 路由注册期几乎不发生读取，此时缓存基本为空，{@link #clear()} 近似 O(1)；
 * 注册完成后不再有写操作，缓存进入稳定态。运行时动态增删路由由调用方主动触发写操作，
 * 随之整体失效并在下次访问时重建——这正是用户期望的「改了就重算」语义。
 *
 * <h3>生命周期</h3>
 * 条目以 Router / RouteDefinition 对象本身为键（默认的对象标识语义）。路由树在应用生命周期内
 * 长存，因此不存在实际意义上的泄漏；被丢弃的临时 Router（如单元测试内构造的）会在下一次
 * {@link #clear()} 时随整表一并释放。
 *
 * @see Router
 * @see RouteDefinition
 */
public final class RouteCache {

    private RouteCache() {
    }

    /**
     * 单个 {@link Router} / {@link RouteDefinition} 的派生结果条目。
     * <p>
     * 字段惰性填充，均为纯函数结果：并发下最坏是重复计算一次并覆盖写入同值，
     * 因此只用 {@code volatile} 保证可见性，不加锁。
     */
    static final class Entry {
        /** 完整 URI（Router 为分组前缀链，RouteDefinition 为最终路径） */
        volatile String fullUri;
        /** 完整路由别名 */
        volatile String fullName;
        /** 完整命名空间 */
        volatile String fullNamespace;
        /** 解析后的中间件列表（已展开别名 / 类引用） */
        volatile List<Middleware> middlewares;
        /** 「中间件链 + 控制器动作」折叠后的处理函数（仅 RouteDefinition 使用） */
        volatile Middleware.NextFunction handlerChain;
        /** 该 Router 子树下的全部路由（仅 Router 使用） */
        volatile List<RouteDefinition> allRoutes;
        /** 别名 → 路由定义（仅 Router 使用） */
        volatile Map<String, RouteDefinition> nameIndex;
        /** 别名 → 已规范化的 URL（仅 Router 使用，供 {@code route('name')} 零计算命中） */
        volatile Map<String, String> urlIndex;
    }

    /** 缓存本体：owner 对象 → 派生结果条目 */
    private static final ConcurrentHashMap<Object, Entry> STORE = new ConcurrentHashMap<>();

    /**
     * 取得 owner 对象的缓存条目，不存在则创建空条目。
     * <p>
     * 命中路径为一次普通哈希查找（无锁），未命中才走 {@code computeIfAbsent} 的分段锁。
     *
     * @param owner {@link Router} 或 {@link RouteDefinition} 实例
     * @return 该对象的缓存条目（永不为 null）
     */
    static Entry of(Object owner) {
        Entry entry = STORE.get(owner);
        if (entry != null) {
            return entry;
        }
        return STORE.computeIfAbsent(owner, key -> new Entry());
    }

    /**
     * 清空全部路由缓存。
     * <p>
     * 由所有结构性写操作（setName / setPrefix / middleware / addRoute / group ...）自动调用，
     * 也可由 {@code artisan route:clear} 或应用代码在动态增删路由后主动调用。
     * 清空后下一次访问会按需重算，不会产生错误结果。
     */
    public static void clear() {
        STORE.clear();
    }

    /**
     * @return 当前缓存条目数（Router + RouteDefinition 的总和），供命令行输出统计
     */
    public static int size() {
        return STORE.size();
    }

    /**
     * 预热：从根 Router 出发算满整棵路由树的派生结果。
     * <p>
     * 供 {@code artisan route:cache} 使用，也可在应用启动完成后主动调用，
     * 把首个请求要付的解析成本提前到启动期。
     *
     * @param root 根 {@link Router}，为 null 时不做任何事
     * @return 已预热的路由条数
     */
    public static int warm(Router root) {
        if (root == null) {
            return 0;
        }
        return root.warmCache();
    }
}
