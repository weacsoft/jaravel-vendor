package com.weacsoft.jaravel.vendor.core.publish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可发布项静态注册表。
 * <p>
 * 各模块在初始化时调用 {@link #register} 注册自己的可发布项，
 * {@code vendor:publish} 命令在执行时统一扫描本注册表，
 * <b>不需要任何 Spring Bean</b>。
 * <p>
 * 对齐 {@code @RegisterGuard} 的静态注册模式：
 * 模块只管注册，命令只管扫描。
 *
 * <h3>使用方式</h3>
 * <pre>
 * // 在模块 AutoConfiguration 的构造器或静态块中注册
 * public class MyAutoConfiguration {
 *     public MyAutoConfiguration() {
 *         PublishableRegistry.register(new MyPublishableConfig());
 *         PublishableRegistry.register(new MyStaticPublishable());
 *     }
 * }
 * </pre>
 */
public class PublishableRegistry {

    private static final List<Publishable> publishables = new ArrayList<>();

    private PublishableRegistry() {
        // 工具类，禁止实例化
    }

    /**
     * 注册一个可发布项。
     * <p>
     * 各模块在初始化时调用此方法注册自己的配置类或静态资源声明。
     *
     * @param publishable 可发布项
     */
    public static void register(Publishable publishable) {
        synchronized (publishables) {
            publishables.add(publishable);
        }
    }

    /**
     * 获取所有已注册的可发布项。
     * <p>
     * 返回不可修改的列表，防止外部意外修改。
     *
     * @return 可发布项列表
     */
    public static List<Publishable> list() {
        synchronized (publishables) {
            return Collections.unmodifiableList(new ArrayList<>(publishables));
        }
    }

    /**
     * 清空注册表（仅用于测试）。
     */
    public static void clearForTest() {
        synchronized (publishables) {
            publishables.clear();
        }
    }
}
