package com.weacsoft.jaravel.vendor.route.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.RegisterCommand;
import com.weacsoft.jaravel.vendor.route.Router;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * 将 {@code route:cache} / {@code route:clear} artisan 命令暴露给 Spring 容器。
 * <p>
 * 仅在 classpath 中存在 {@link ArtisanCommand}（即应用引入了 artisan 模块）且
 * 存在 {@link Router} 时才激活。命令本身以 {@code @ConditionalOnMissingBean} 暴露，
 * 允许应用层覆盖默认实现。
 */
@AutoConfiguration
@ConditionalOnClass({ArtisanCommand.class, Router.class})
public class HttpRouteArtisanAutoConfiguration {

    @RegisterCommand("缓存路由")
    @ConditionalOnMissingBean
    public RouteCacheCommand routeCacheCommand() {
        return new RouteCacheCommand();
    }

    @RegisterCommand("清除路由缓存")
    @ConditionalOnMissingBean
    public RouteClearCommand routeClearCommand() {
        return new RouteClearCommand();
    }
}
