package com.weacsoft.jaravel.vendor.springboot.aetherupload;

import com.weacsoft.jaravel.vendor.aetherupload.AetherUploadManager;
import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadProperties;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.http.controller.ControllerRegistry;
import com.weacsoft.jaravel.vendor.springboot.cache.CacheAutoConfiguration;
import com.weacsoft.jaravel.vendor.springboot.storage.StorageAutoConfiguration;
import com.weacsoft.jaravel.vendor.storage.StorageManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AetherUpload 大文件上传自动配置。
 * <p>
 * 引入本模块依赖即自动装配 {@link AetherUploadManager} 与上传控制器；
 * 应用只需在路由定义中调用 {@code AetherUploadRoutes.register()} 挂载端点。
 * <p>
 * 通过 {@code jaravel.aether-upload.enabled=false} 可整体关闭。
 * 记录头存储依赖 cache 模块的 {@link CacheManager}（组配置 header-store 为
 * cache/redis 等 store 名时生效），无 CacheManager 时自动降级为内存记录头。
 * <p>
 * 落盘依赖 storage 模块的 {@link StorageManager}（组配置 disk 时生效，
 * 可将完成的文件落到任意磁盘/驱动），未配置 disk 时直接写本地 {@code save-dir}。
 * <p>
 * {@link AetherUploadProperties} 为纯 POJO（保留在 aether-upload 模块，{@code AetherUploadManager} 直接消费），
 * 经 {@code @Bean @ConfigurationProperties} 方法绑定。
 *
 * @author springboot
 */
@AutoConfiguration
@AutoConfigureAfter({CacheAutoConfiguration.class, StorageAutoConfiguration.class})
@ConditionalOnClass({AetherUploadManager.class, AetherUploadProperties.class})
@ConditionalOnProperty(prefix = "jaravel.aether-upload", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AetherUploadAutoConfiguration {

    /**
     * 上传配置 Bean，绑定 {@code jaravel.aether-upload.*} 配置。
     *
     * @return 上传配置
     */
    @Bean
    @ConfigurationProperties(prefix = "jaravel.aether-upload")
    @ConditionalOnMissingBean
    public AetherUploadProperties jaravelAetherUploadProperties() {
        return new AetherUploadProperties();
    }

    /**
     * 上传核心管理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public AetherUploadManager aetherUploadManager(AetherUploadProperties properties,
                                                    ObjectProvider<CacheManager> cacheManagerProvider,
                                                    ObjectProvider<StorageManager> storageManagerProvider) {
        return new AetherUploadManager(properties,
                cacheManagerProvider.getIfAvailable(),
                storageManagerProvider.getIfAvailable());
    }

    /**
     * 上传端点控制器，注册到全局控制器注册表，
     * 路由按全限定名 {@code AetherUploadController::action} 解析。
     */
    @Bean
    @ConditionalOnMissingBean
    public AetherUploadController aetherUploadController(AetherUploadManager manager) {
        AetherUploadController controller = new AetherUploadController(manager);
        ControllerRegistry.registerGlobal(controller);
        return controller;
    }
}
