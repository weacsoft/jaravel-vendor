package com.weacsoft.jaravel.vendor.migration.autoconfigure;

import com.weacsoft.jaravel.vendor.core.publish.PublishableConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * migration 模块「发布配置」自动装配。
 * <p>
 * <b>为什么要从 {@link MigrationAutoConfiguration} 中拆出来单独成类？</b>
 * <p>
 * {@code MigrationAutoConfiguration} 在<b>类级别</b>带有运行期开关
 * {@code @ConditionalOnProperty(prefix = "jaravel.migration", name = "enabled", havingValue = "true", matchIfMissing = true)}。
 * 业务方一旦写下 {@code jaravel.migration.enabled=false}（例如生产环境禁止随应用启动跑迁移），
 * 整个自动配置便不再加载，可发布配置声明也随之消失。
 * <p>
 * 而 {@code artisan vendor:publish} 属于<b>构建期脚手架</b>：能否生成
 * {@code MigrationConfig.java} 模板，与「迁移运行期是否启用」是两回事。
 * 关闭自动迁移的工程同样需要这份配置模板来声明迁移目录、迁移源模式等。
 * <p>
 * 因此本类<b>只保留 {@code @ConditionalOnClass(PublishableConfig.class)} 这一个条件</b>，
 * 不含任何运行时条件，确保任何情况下都能执行 {@code artisan vendor:publish --tag=migration}。
 */
@AutoConfiguration
@ConditionalOnClass(PublishableConfig.class)
public class MigrationPublishAutoConfiguration {

    /**
     * 声明 migration 模块的可发布配置类，供 {@code artisan vendor:publish --tag=migration} 使用。
     * <p>
     * 仅声明元数据，不依赖 artisan 模块；未引入 artisan 时该 Bean 无人消费，无副作用。
     *
     * @return 可发布配置声明
     */
    @Bean
    @ConditionalOnMissingBean(MigrationPublishableConfig.class)
    public MigrationPublishableConfig migrationPublishableConfig() {
        return new MigrationPublishableConfig();
    }
}
