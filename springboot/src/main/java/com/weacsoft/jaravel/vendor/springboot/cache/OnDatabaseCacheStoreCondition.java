package com.weacsoft.jaravel.vendor.springboot.cache;

import com.weacsoft.jaravel.vendor.springboot.condition.OnDriverInUseCondition;

/**
 * 仅当确实声明了 {@code driver: database} 的缓存 store 时才装配数据库缓存驱动。
 *
 * <p>命中以下任一配置即装配：
 * <pre>
 * jaravel:
 *   cache:
 *     default-store: db          # 且 stores.db.driver = database
 *     stores:
 *       db:
 *         driver: database       # ← 命中
 *         table: jaravel_cache
 * </pre>
 *
 * 未声明 database 驱动时，数据库缓存驱动工厂（cache-database 模块）完全不注册，
 * 应用无需任何数据源即可正常启动。
 *
 * @see OnDriverInUseCondition
 */
public class OnDatabaseCacheStoreCondition extends OnDriverInUseCondition {

    public OnDatabaseCacheStoreCondition() {
        super("database", "jaravel.cache.stores.", ".driver", "jaravel.cache.driver");
    }
}
