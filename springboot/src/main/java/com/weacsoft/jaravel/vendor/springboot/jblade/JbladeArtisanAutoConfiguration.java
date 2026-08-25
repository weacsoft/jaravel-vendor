package com.weacsoft.jaravel.vendor.springboot.jblade;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.artisan.RegisterCommand;
import com.weacsoft.jaravel.vendor.jblade.artisan.ViewCacheCommand;
import com.weacsoft.jaravel.vendor.jblade.artisan.ViewClearCommand;
import com.weacsoft.jaravel.vendor.jblade.view.ViewManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * jblade 模块与 Artisan CLI 的集成自动装配。
 * <p>
 * 当 classpath 中同时存在 {@link ArtisanCommand}（artisan 模块）与 jblade 时，
 * 自动注册模板缓存相关命令为 Artisan 命令 Bean：
 * <ul>
 *   <li>{@code view:cache} — 编译全部模板并写入缓存；</li>
 *   <li>{@code view:clear} — 清除全部模板缓存。</li>
 * </ul>
 * artisan 为 optional 依赖，未引入时本配置整体不生效，无任何副作用。
 */
@AutoConfiguration
@AutoConfigureAfter(ViewAutoConfiguration.class)
@ConditionalOnClass({ArtisanCommand.class, ViewManager.class})
public class JbladeArtisanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JbladeArtisanAutoConfiguration.class);

    /**
     * 注册 {@code view:cache} 命令。
     */
    @RegisterCommand("编译全部模板并写入缓存")
    @ConditionalOnMissingBean(ViewCacheCommand.class)
    public ViewCacheCommand viewCacheCommand() {
        log.debug("[jblade-artisan] 注册命令: view:cache");
        return new ViewCacheCommand();
    }

    /**
     * 注册 {@code view:clear} 命令。
     */
    @RegisterCommand("清除全部模板缓存")
    @ConditionalOnMissingBean(ViewClearCommand.class)
    public ViewClearCommand viewClearCommand() {
        log.debug("[jblade-artisan] 注册命令: view:clear");
        return new ViewClearCommand();
    }
}
