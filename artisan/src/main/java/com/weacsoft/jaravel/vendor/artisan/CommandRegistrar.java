package com.weacsoft.jaravel.vendor.artisan;

import com.weacsoft.jaravel.vendor.core.registrar.AnnotationDrivenRegistrar;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;

/**
 * Artisan 命令注解扫描注册器。
 * <p>
 * 继承 {@link AnnotationDrivenRegistrar}，在所有单例 Bean 初始化完成后扫描
 * {@link RegisterCommand} 注解方法，调用方法获取 {@link ArtisanCommand} 实例，
 * 并通过 {@link ArtisanApplication#register(ArtisanCommand)} 注册到命令管理器。
 * <p>
 * 命令实例<b>不注册为 Spring Bean</b>，只存入 ArtisanApplication 的内部注册表。
 */
public class CommandRegistrar extends AnnotationDrivenRegistrar<RegisterCommand> {

    private final ArtisanApplication artisanApplication;

    public CommandRegistrar(ApplicationContext context, ArtisanApplication artisanApplication) {
        super(context, RegisterCommand.class);
        this.artisanApplication = artisanApplication;
    }

    @Override
    protected void register(Object result, Method method, RegisterCommand annotation) {
        ArtisanCommand command = requireType(result, ArtisanCommand.class, method);
        artisanApplication.register(command);
        String desc = annotation.value().isEmpty() ? "" : " (" + annotation.value() + ")";
        log.info("[artisan] 注解注册命令: {} -> {}{}", command.commandName(),
                command.getClass().getSimpleName(), desc);
    }
}
