package com.weacsoft.jaravel.vendor.plugin.jar.database.autoconfigure;

import com.weacsoft.jaravel.vendor.plugin.jar.database.model.PluginMetadataModel;
import com.weacsoft.jaravel.vendor.plugin.jar.database.persistence.ModelMetadataPersistence;
import com.weacsoft.jaravel.vendor.plugin.jar.persistence.MetadataPersistence;
import com.weacsoft.jaravel.vendor.plugin.jar.autoconfigure.PluginJarAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 插件 JAR 数据库持久化自动装配。
 * <p>
 * 当 classpath 中同时存在 {@link MetadataPersistence} 接口与 {@link PluginMetadataModel} 模型时，
 * 注册 {@link ModelMetadataPersistence} 作为 {@link MetadataPersistence} 的默认实现，
 * 覆盖 {@link PluginJarAutoConfiguration} 中的 JSON 文件实现。
 * <p>
 * 通过 {@link AutoConfigureBefore} 确保本配置在 {@link PluginJarAutoConfiguration} 之前加载，
 * 使 {@code @ConditionalOnMissingBean} 优先匹配到 {@link ModelMetadataPersistence}，
 * 从而让数据库持久化优先于 JSON 文件持久化生效。
 */
@AutoConfiguration
@AutoConfigureBefore(PluginJarAutoConfiguration.class)
@ConditionalOnClass({MetadataPersistence.class, PluginMetadataModel.class})
public class PluginJarDatabaseAutoConfiguration {

    /**
     * 注册基于数据库的元数据持久化 Bean。
     * <p>
     * 当容器中不存在其他 {@link MetadataPersistence} 实现时生效。
     *
     * @return 数据库元数据持久化实例
     */
    @Bean
    @ConditionalOnMissingBean(MetadataPersistence.class)
    public MetadataPersistence modelMetadataPersistence() {
        return new ModelMetadataPersistence();
    }
}
