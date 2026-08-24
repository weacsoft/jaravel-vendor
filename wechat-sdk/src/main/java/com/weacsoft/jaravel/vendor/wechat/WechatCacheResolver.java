package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 微信 SDK 内部缓存仓库解析（共享工具）：
 * <ul>
 *   <li>首选 store 名为 {@code jaravel.wechat.cache-store} 配置（如 "redis"）</li>
 *   <li>未配置/未注册时回退到 cache 模块默认 store（由 {@code jaravel.cache.default-store} 决定）</li>
 *   <li>CacheManager 未注入时回退到内存 store，保证 SDK 无 cache 环境仍可用</li>
 * </ul>
 *
 * 供 access_token / jsapi_ticket 等票据缓存使用（对齐「用 core + cache 模块管理票据」的约定）。
 *
 * @author weacsoft
 */
final class WechatCacheResolver {

    private static final Logger logger = LoggerFactory.getLogger(WechatCacheResolver.class);

    private WechatCacheResolver() {
    }

    /**
     * 解析缓存仓库。
     *
     * @param cacheManager 缓存管理器，可为 null
     * @param preferredStore 首选 store 名，为空时使用默认 store
     * @return 缓存仓库
     */
    static CacheStore resolve(CacheManager cacheManager, String preferredStore) {
        if (cacheManager == null) {
            logger.warn("[wechat] CacheManager 未注入，票据使用本地内存缓存");
            return CacheManager.createDefaultStore();
        }
        if (preferredStore == null || preferredStore.isEmpty()) {
            return cacheManager.store();
        }
        try {
            return cacheManager.store(preferredStore);
        } catch (IllegalStateException e) {
            logger.debug("[wechat] 缓存 store '{}' 未注册，票据回退到默认 store: {}", preferredStore, e.getMessage());
            return cacheManager.store();
        }
    }
}
