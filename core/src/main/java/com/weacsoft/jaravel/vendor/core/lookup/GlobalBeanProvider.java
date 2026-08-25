package com.weacsoft.jaravel.vendor.core.lookup;

/**
 * 全局 Bean 提供者：在 {@link BeanLookup} 基础上增加单例注册能力，
 * 对齐传统实现中「向容器发布服务」的动作（如 {@code registerSingleton}）。
 * <p>
 * 宿主（Spring 适配器或纯运行时）在启动时创建实现并通过
 * {@link GlobalLookup#install(GlobalBeanProvider)} 安装一次，此后
 * core 的 Facade / Application / 注册器等纯代码即可经由全局查找解析 Bean。
 */
public interface GlobalBeanProvider extends BeanLookup {

    /**
     * 注册单例 Bean（纯实现可直接覆盖 Map 条目；
     * Spring 适配器先销毁既有单例再注册，行为与原 {@code SpringContext.registerSingleton} 一致）。
     *
     * @param name     Bean 名称
     * @param instance Bean 实例
     */
    void registerSingleton(String name, Object instance);
}
