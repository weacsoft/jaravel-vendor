package com.weacsoft.jaravel.vendor.plugin.jar.annotation;

/**
 * 共享服务标记接口。
 * <p>
 * 插件中实现此接口的类会被放入共享 ClassLoader，可被其他插件跨插件调用。
 * <p>
 * 典型用法：插件 A 提供一个 {@code @PluginComponent @SharedService} 的服务，
 * 插件 B 通过 {@link Application#getService(String, Class, String)} 获取该服务实例。
 */
public interface SharedService {
}
