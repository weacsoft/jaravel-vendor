package com.weacsoft.jaravel.vendor.storage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式注册存储磁盘，替代 {@code @Bean} 方式（避免 bean name 冲突）。
 * <p>
 * 标注在 {@code @Configuration} 类的方法上，方法返回
 * {@link com.weacsoft.jaravel.vendor.storage.contract.DiskDefinition}
 * （由驱动工厂创建磁盘）或直接返回
 * {@link com.weacsoft.jaravel.vendor.storage.contract.Filesystem}（完全自定义实例）。
 * {@code com.weacsoft.jaravel.vendor.springboot.storage.StorageRegistrar}
 * 会在所有 Bean 初始化完成后扫描此注解，调用方法并按 {@link #value()} 指定的名称注册到
 * {@link StorageManager}。
 *
 * <h3>为什么不用 {@code @Bean}？</h3>
 * {@code @Bean("public")} 的 bean name 在整个 Spring 容器内必须唯一。如果另一处也有
 * {@code @Bean("public")}（返回不同类型），Spring Boot 会抛出
 * {@code BeanDefinitionOverrideException}。使用本注解后，磁盘名称与 bean name 解耦，
 * 不会注册为 Spring Bean，因此不会与同名 bean 冲突。
 * 这与 auth 模块的 {@code @RegisterGuard} / {@code @RegisterProvider} 设计完全一致。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Configuration
 * public class StorageConfig {
 *
 *     // 返回 DiskDefinition，由 local 驱动工厂创建
 *     &#64;RegisterDisk("local")
 *     public DiskDefinition localDisk() {
 *         return DiskDefinition.local("storage/app");
 *     }
 *
 *     // 标记为默认磁盘，覆盖 jaravel.storage.default-disk 配置
 *     &#64;RegisterDisk(value = "public", defaultDisk = true)
 *     public DiskDefinition publicDisk() {
 *         return DiskDefinition.local("storage/app/public")
 *                 .url("/storage")
 *                 .visibility(Visibility.PUBLIC);
 *     }
 *
 *     // 直接返回 Filesystem 实例，完全自定义
 *     &#64;RegisterDisk("memory")
 *     public Filesystem memoryDisk() {
 *         return new InMemoryFilesystem("memory");
 *     }
 * }
 * </pre>
 *
 * <h3>方法参数注入</h3>
 * 方法可声明任意参数，{@code StorageRegistrar} 会从 Spring 容器中按类型自动解析注入，
 * 行为与 {@code @Bean} 方法的参数注入一致：
 * <pre>
 * &#64;RegisterDisk("s3")
 * public DiskDefinition s3Disk(AwsProperties aws) {
 *     return DiskDefinition.of("s3").with("bucket", aws.getBucket());
 * }
 * </pre>
 *
 * <h3>与配置式的关系</h3>
 * 本注解注册的磁盘覆盖同名配置式磁盘（在配置式注册之后执行）。
 *
 * @see StorageManager#registerDisk(String, com.weacsoft.jaravel.vendor.storage.contract.Filesystem)
 * @see com.weacsoft.jaravel.vendor.storage.contract.DiskDefinition
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterDisk {

    /**
     * 磁盘名称，用于 {@code Storage.disk(name)} 或 {@code StorageManager.disk(name)} 解析。
     *
     * @return 磁盘名称
     */
    String value();

    /**
     * 是否设为默认磁盘。
     * <p>
     * 设为 {@code true} 时，等效于调用 {@link StorageManager#setDefaultDisk(String)}，
     * 会覆盖 {@code jaravel.storage.default-disk} 配置值。
     * 若多个 {@code @RegisterDisk} 同时标记 {@code defaultDisk = true}，最后注册的生效。
     *
     * @return 是否为默认磁盘
     */
    boolean defaultDisk() default false;
}
