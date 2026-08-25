package com.weacsoft.jaravel.vendor.core;

import com.weacsoft.jaravel.vendor.core.lookup.GlobalBeanProvider;
import com.weacsoft.jaravel.vendor.core.lookup.GlobalLookup;

/**
 * 全局 Bean 访问门面（纯 Java，零 Spring 依赖）。
 * <p>
 * 供 {@link Facade} 门面静态解析 Bean，模仿 Laravel 的 Facade 机制：
 * 门面是一个静态代理，背后真正干活的是宿主容器中解析出的实例。
 * <p>
 * <h3>P3 解耦说明</h3>
 * 本类不再依赖 {@code ApplicationContext}：所有操作统一委托给
 * {@link GlobalLookup} 安装的 {@link GlobalBeanProvider}。
 * <ul>
 *   <li>Spring 宿主由 springboot 模块的 {@code CoreSpringConfiguration}
 *       安装基于 {@code ApplicationContext} 的适配器（含「注册前先销毁同名单例」的更新语义）；</li>
 *   <li>非 Spring 宿主（纯 JVM / 测试 / 嵌入式网关）安装一个 Map 版提供者即可使用全部能力；</li>
 *   <li>类名与包名保持不变，是已对外发布的 stable API（多个 publish 模板代码引用本 FQCN）。</li>
 * </ul>
 * 行为契约与 P3 前一致：未初始化时强依赖路径抛 {@code IllegalStateException}，
 * 空安全路径（{@code beanOrNull}）返回 {@code null} 让调用方降级。
 */
public final class SpringContext {

    private SpringContext() {
    }

    /**
     * 按类型解析 Bean。
     *
     * @param type Bean 类型
     * @param <T>  Bean 类型
     * @return Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T bean(Class<T> type) {
        return (T) GlobalLookup.require().bean(type);
    }

    /**
     * 按名称 + 类型解析 Bean。
     *
     * @param name Bean 名称
     * @param type Bean 类型
     * @param <T>  Bean 类型
     * @return Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T bean(String name, Class<T> type) {
        return (T) GlobalLookup.require().bean(name, type);
    }

    /**
     * 按名称解析 Bean（不强转类型）。
     *
     * @param name Bean 名称
     * @param <T>  期望类型
     * @return Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T bean(String name) {
        return (T) GlobalLookup.require().bean(name);
    }

    /** 容器是否包含指定名称的 Bean。 */
    public static boolean contains(String name) {
        return GlobalLookup.require().contains(name);
    }

    /**
     * 按类型安全获取 Bean，不存在（或宿主尚未安装）时返回 {@code null} 而非抛异常。
     * <p>
     * 供「先查自有注册表、找不到再回退宿主容器」这类解析逻辑使用。
     *
     * @param type Bean 类型
     * @param <T>  Bean 类型
     * @return Bean 实例，不存在时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <T> T beanOrNull(Class<T> type) {
        GlobalBeanProvider provider = GlobalLookup.getIfInstalled();
        if (provider == null) {
            return null;
        }
        try {
            return (T) provider.bean(type);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 按名称 + 类型安全获取 Bean，不存在（或类型不匹配）时返回 {@code null} 而非抛异常。
     *
     * @param name Bean 名称
     * @param type Bean 类型
     * @param <T>  Bean 类型
     * @return Bean 实例，不存在时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <T> T beanOrNull(String name, Class<T> type) {
        GlobalBeanProvider provider = GlobalLookup.getIfInstalled();
        if (provider == null || name == null || !provider.contains(name)) {
            return null;
        }
        try {
            return (T) provider.bean(name, type);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 运行时注册/替换单例 Bean。
     * <p>
     * 如果同名 Bean 已存在，先销毁旧实例再注册新实例，实现「更新」语义
     * （销毁逻辑由宿主适配器实现：Spring 适配器走 BeanFactory 的
     * {@code destroySingleton + registerSingleton}，纯 Map 适配器直接覆盖条目）。
     * 注册后可通过 {@code bean(name)} 或 {@code bean(type)} 获取。
     *
     * @param name Bean 名称
     * @param bean Bean 实例
     * @throws IllegalStateException 宿主提供者未安装时
     */
    public static void registerSingleton(String name, Object bean) {
        GlobalLookup.require().registerSingleton(name, bean);
    }
}
