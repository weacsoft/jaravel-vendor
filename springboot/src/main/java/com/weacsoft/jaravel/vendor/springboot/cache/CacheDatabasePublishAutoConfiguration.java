package com.weacsoft.jaravel.vendor.springboot.cache;

import com.weacsoft.jaravel.vendor.cache.database.CacheDatabaseMigrationPublishable;
import com.weacsoft.jaravel.vendor.core.publish.PublishableRegistry;

/**
 * cache-database 模块「迁移发布」自动装配（0.1.3 新增）。
 * <p>
 * 与 {@link CacheAutoConfiguration} 解耦：后者绑定 cache 核心模块，而
 * {@link CacheDatabaseMigrationPublishable} 位于<b>可选</b>的 cache-database 模块——
 * 未引入该模块时本自动配置被 {@code @ConditionalOnClass} 跳过，不会抛
 * {@code NoClassDefFoundError}（对齐 {@code vendor.springboot.wechat.WechatPublishAutoConfiguration}
 * 的守卫模式）。
 * <p>
 * 注册后可执行：
 * <pre>
 * artisan vendor:publish --tag=migrations        # 发布所有模块的建表迁移（含本项）
 * artisan vendor:publish --tag=cache-database    # 只发布 cache-database 的建表迁移
 * </pre>
 * 发布产物为 {@code jaravel_cache} 缓存表迁移（Java 源文件，落到业务工程迁移源代码目录），
 * 随后 {@code artisan migrate} 完成建表——对齐 Laravel vendor:publish --tag=migrations。
 */
@org.springframework.boot.autoconfigure.AutoConfiguration
@org.springframework.boot.autoconfigure.condition.ConditionalOnClass(CacheDatabaseMigrationPublishable.class)
public class CacheDatabasePublishAutoConfiguration {
    static {
        PublishableRegistry.register(new CacheDatabaseMigrationPublishable());
    }
}
