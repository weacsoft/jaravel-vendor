package com.weacsoft.jaravel.vendor.queue.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式注册队列驱动，替代 {@code @Bean} 方式。
 * <p>
 * 标注在 {@code @Configuration} 类的方法上，方法返回 {@link QueueDriver}。
 * {@code QueueDriverRegistrar} 会在所有 Bean 初始化完成后扫描此注解，
 * 调用方法并把返回的实例注册为全局队列驱动。
 *
 * <h3>为什么只允许注册一个？</h3>
 * 队列驱动决定「任务存放在哪里」，一个应用只会有一个生效的队列后端
 * （database 或 redis），因此本注解不带名称参数，且框架强制唯一性：
 * 若扫描到多个 {@code @RegisterQueueDriver}，启动时直接报错，
 * 避免出现「任务被推送到哪个队列」的隐式歧义。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Configuration
 * public class QueueConfig {
 *
 *     &#64;RegisterQueueDriver
 *     public QueueDriver redisQueueDriver(RedisManager redisManager, QueueProperties props) {
 *         return new RedisQueueDriver(redisManager, props.getConnection(), 90);
 *     }
 * }
 * </pre>
 *
 * <h3>覆盖已有注册</h3>
 * 框架已按配置自动装配了 database / redis 驱动，业务工程若想替换，
 * 可设置 {@link #override()} 为 {@code true}：
 * <pre>
 * &#64;RegisterQueueDriver(override = true)
 * public QueueDriver myQueueDriver() { ... }
 * </pre>
 *
 * <h3>回退默认</h3>
 * 若未注册任何 {@code @RegisterQueueDriver}，也没有 {@link QueueDriver} Bean
 * （例如没有 {@code DataSource}、或 {@code jaravel.queue.driver=sync}），
 * 队列退化为 <b>sync 同步模式</b>：任务在当前线程立即执行，无需任何外部依赖。
 *
 * @see QueueDriver
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterQueueDriver {

    /**
     * 是否覆盖已有的队列驱动注册。
     * <p>
     * 默认 {@code false}：此时若存在多个注册项，启动报错。
     * 设为 {@code true} 时，本注册项优先生效。
     *
     * @return 是否覆盖，默认 {@code false}
     */
    boolean override() default false;
}
