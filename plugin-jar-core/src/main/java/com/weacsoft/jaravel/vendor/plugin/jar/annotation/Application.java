package com.weacsoft.jaravel.vendor.plugin.jar.annotation;

/**
 * 插件互调代理类。
 * <p>
 * 插件之间通过本类静态方法获取其他插件暴露的 Bean，避免直接依赖 {@code HotPluginManager}。
 * <p>
 * 工作原理：
 * <ol>
 *   <li>主程序在初始化 {@code HotPluginManager} 后调用 {@link #setManagerRef(HotPluginManagerRef)}
 *       注入管理器引用。</li>
 *   <li>插件代码调用 {@link #getService(String, Class, String)} 时，本类委托给管理器引用，
 *       由管理器从对应插件的 Spring 容器中取出 Bean。</li>
 *   <li>由于 {@code Application} 由主程序的 ClassLoader 加载（共享包前缀），插件 ClassLoader
 *       会将本类的加载委托给共享 ClassLoader，从而保证全进程唯一实例。</li>
 * </ol>
 *
 * <h3>线程安全说明</h3>
 * {@link #managerRef} 使用 {@code volatile}，保证多线程下的可见性。
 */
public final class Application {

    /** 管理器引用，由主程序在启动时注入，volatile 保证可见性 */
    private static volatile HotPluginManagerRef managerRef;

    private Application() {
    }

    /**
     * 注入插件管理器引用。主程序在创建 {@code HotPluginManager} 后调用。
     *
     * @param ref 管理器引用
     */
    public static void setManagerRef(HotPluginManagerRef ref) {
        managerRef = ref;
    }

    /**
     * 从指定插件获取服务 Bean。
     *
     * @param pluginId    目标插件 ID
     * @param serviceType 期望的服务类型（仅用于类型校验/转换，可为 {@code null}）
     * @param beanName    Bean 名称
     * @param <T>         服务类型
     * @return 服务实例，若插件不存在或 Bean 不存在则返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <T> T getService(String pluginId, Class<T> serviceType, String beanName) {
        HotPluginManagerRef ref = managerRef;
        if (ref == null) {
            throw new IllegalStateException("HotPluginManagerRef 未注入，无法跨插件获取服务");
        }
        Object bean = ref.getServiceFromPlugin(pluginId, beanName);
        if (bean == null) {
            return null;
        }
        if (serviceType != null && !serviceType.isInstance(bean)) {
            throw new ClassCastException("插件 " + pluginId + " 的 Bean " + beanName
                    + " 类型 " + bean.getClass().getName() + " 无法转换为 " + serviceType.getName());
        }
        return (T) bean;
    }

    /**
     * 管理器引用接口，由 {@code HotPluginManager} 实现。
     * <p>
     * 抽象为接口以避免插件代码直接依赖 {@code HotPluginManager} 具体类。
     */
    public interface HotPluginManagerRef {

        /**
         * 从指定插件获取 Bean。
         *
         * @param pluginId 插件 ID
         * @param beanName Bean 名称
         * @return Bean 实例，不存在返回 {@code null}
         */
        Object getServiceFromPlugin(String pluginId, String beanName);
    }
}
