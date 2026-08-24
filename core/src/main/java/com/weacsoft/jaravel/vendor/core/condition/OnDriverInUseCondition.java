package com.weacsoft.jaravel.vendor.core.condition;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 「驱动被用上了才装配」条件基类，是整个 vendor 模块组的统一装配原则。
 *
 * <h3>设计原则</h3>
 * jaravel 的模块化对齐 Laravel：<b>安装（放进 classpath）≠ 启用</b>。
 * 一个驱动型模块（redis 缓存、redis session、database 缓存、redis 队列……）
 * 只有在用户<b>显式选用</b>它时才应该注册与配置；否则应当完全静默，
 * 不创建任何 Bean、不连接任何外部服务、不影响应用启动。
 * <p>
 * 典型反例：项目引入了 {@code session-redis} 依赖但 {@code jaravel.session.driver} 配的是
 * {@code file}，此时若 session-redis 仍自动装配并连接 Redis，就会导致无 Redis 环境启动失败。
 *
 * <h3>判定方式</h3>
 * 子类通过构造参数声明：
 * <ul>
 *   <li>{@code driverName} — 本模块提供的驱动名，如 {@code redis}、{@code database}；</li>
 *   <li>{@code singleKeys} — 单值配置项，如 {@code jaravel.session.driver}；</li>
 *   <li>{@code mapKeyPrefix} / {@code mapKeySuffix} — 映射式配置，
 *       如 {@code jaravel.cache.stores.*.driver}。</li>
 * </ul>
 * 任意一处的值等于 {@code driverName}（忽略大小写）即判定为「被用上」。
 *
 * <h3>实现说明</h3>
 * 只依赖 {@code spring-context} 的 {@link Condition} 接口，不引入
 * {@code spring-boot-autoconfigure}，以保持 core 模块的依赖足迹不变。
 */
public abstract class OnDriverInUseCondition implements Condition {

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(OnDriverInUseCondition.class);

    /**
     * 便捷构造器：仅依赖单个单值配置键，无映射式配置。
     *
     * @param driverName 驱动名
     * @param singleKey  单值配置键，如 {@code jaravel.queue.driver}
     */
    protected OnDriverInUseCondition(String driverName, String singleKey) {
        this(driverName, null, null, singleKey);
    }

    /** 本模块提供的驱动名。 */
    private final String driverName;

    /** 单值配置键，任意一个等于驱动名即命中。 */
    private final String[] singleKeys;

    /** 映射式配置的前缀，例如 {@code jaravel.cache.stores.}。可为 {@code null}。 */
    private final String mapKeyPrefix;

    /** 映射式配置的后缀，例如 {@code .driver}。配合 {@link #mapKeyPrefix} 使用。 */
    private final String mapKeySuffix;

    /**
     * @param driverName   驱动名
     * @param mapKeyPrefix 映射式配置前缀，无则传 {@code null}
     * @param mapKeySuffix 映射式配置后缀，无则传 {@code null}
     * @param singleKeys   单值配置键
     */
    protected OnDriverInUseCondition(String driverName, String mapKeyPrefix, String mapKeySuffix,
                                     String... singleKeys) {
        this.driverName = driverName;
        this.mapKeyPrefix = mapKeyPrefix;
        this.mapKeySuffix = mapKeySuffix;
        this.singleKeys = singleKeys == null ? new String[0] : singleKeys;
    }

    /**
     * 显式开关键，值为 {@code true} 时直接命中；为 {@code false} 时直接否决。
     * 子类可通过 {@link #enableKey(String)} 设置。
     */
    private String enableKey;

    /** 缺省即命中标志，用于兜底默认驱动。 */
    private boolean matchIfAbsent = false;

    /**
     * 声明式注册注解的全限定名。存在被其标注的方法时命中。
     *
     * @see #matchIfDeclaredBy(String...)
     */
    private String[] declarativeAnnotations = new String[0];

    /**
     * 设置显式开关键，例如 {@code jaravel.session.redis.auto-register}。
     * <p>
     * 该键优先级最高：显式 {@code true} 强制装配，显式 {@code false} 强制不装配，
     * 未配置时才走驱动名匹配逻辑。
     *
     * @param key 配置键
     * @return this，便于链式调用
     */
    protected OnDriverInUseCondition enableKey(String key) {
        this.enableKey = key;
        return this;
    }

    /**
     * 缺省即命中标志。适用于<b>兜底默认驱动</b>（如 session 守卫、local 磁盘）：
     * 当用户<b>完全没有显式配置</b>本模块的任何驱动键（单值键为空且其映射配置块下无任何键）时，
     * 视为选用了本默认驱动而装配。
     * <p>
     * 一旦用户显式写了任何驱动键（即使是其它驱动名），本条件不再命中，
     * 由对应驱动的 condition 负责按需装配，从而保证「用上了才注册」。
     *
     * @return this，便于链式调用
     */
    protected OnDriverInUseCondition matchIfAbsent() {
        this.matchIfAbsent = true;
        return this;
    }

    /**
     * 「声明式注册在用即命中」标志，用于<b>驱动名写在方法返回值里、注解属性中读不到</b>的场景。
     *
     * <h3>为什么需要</h3>
     * jaravel 的声明式注册（{@code @RegisterGuard} / {@code @RegisterCacheStore} /
     * {@code @RegisterSessionStore} / {@code @RegisterDisk} 等）只在注解里写<b>名字</b>，
     * 真正的驱动名在方法体返回的定义对象里，例如：
     * <pre>
     * &#64;RegisterGuard("api")
     * public GuardDefinition apiGuard() {
     *     return GuardDefinition.of("jwt", "users");   // ← 驱动名是运行时值
     * }
     * </pre>
     * Spring {@link Condition} 在 Bean 定义阶段求值，<b>无法执行方法体</b>，因此仅凭
     * 配置属性（{@code jaravel.auth.guards.*.driver}）无法判定 jwt 是否被用上。
     * 结果就是：用注解声明 jwt 守卫的应用，驱动 Bean 永远不装配，
     * 所有 jwt 守卫路由在运行期抛「未知 guard driver: jwt」。
     *
     * <h3>判定策略</h3>
     * 一旦发现容器中存在被这些注解标注的方法，说明<b>驱动是以声明式方式配置的</b>，
     * 静态属性检查不再充分，此时采取<b>宽松策略</b>予以装配。
     * 装配后是否真正被使用，仍由运行期的驱动匹配（如 {@code driver.support(name)}）决定，
     * 未被选中的驱动只是一个惰性对象，不产生任何副作用。
     *
     * <h3>适用边界（重要）</h3>
     * 仅可用于<b>不持有外部资源</b>的驱动（如 jwt：只做签名/校验，不连任何中间件）。
     * 对 redis 等<b>会建立外部连接</b>的驱动<b>禁止</b>使用本策略，
     * 否则会退化成「装了依赖就连 Redis」，正是本类要避免的反例；
     * 那类模块应继续走 {@link #enableKey(String)} 显式开关。
     *
     * @param annotationClassNames 声明式注册注解的全限定类名
     * @return this，便于链式调用
     */
    protected OnDriverInUseCondition matchIfDeclaredBy(String... annotationClassNames) {
        this.declarativeAnnotations =
                annotationClassNames == null ? new String[0] : annotationClassNames;
        return this;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean matched = evaluate(context);
        if (logger.isDebugEnabled()) {
            logger.debug("[vendor] 驱动 [{}] {}装配（判定依据：{}）", driverName,
                    matched ? "" : "不", describe());
        }
        return matched;
    }

    /**
     * 执行判定。
     *
     * @param context 条件求值上下文
     * @return 是否应当装配
     */
    private boolean evaluate(ConditionContext context) {
        Environment env = context.getEnvironment();
        // 1) 显式开关优先级最高
        if (enableKey != null) {
            String flag = env.getProperty(enableKey);
            if (flag != null) {
                return Boolean.parseBoolean(flag);
            }
        }

        // 2) 单值配置键
        for (String key : singleKeys) {
            if (driverName.equalsIgnoreCase(env.getProperty(key))) {
                return true;
            }
        }

        // 3) 映射式配置键，如 jaravel.cache.stores.<name>.driver
        if (mapKeyPrefix != null) {
            for (String key : mapDriverKeys(env)) {
                if (driverName.equalsIgnoreCase(env.getProperty(key))) {
                    return true;
                }
            }
        }

        // 4) 声明式注册在用：驱动名在方法返回值里，属性检查不充分，宽松装配
        if (declarativeAnnotations.length > 0 && hasDeclarativeRegistration(context)) {
            return true;
        }

        // 5) 兜底默认驱动：用户完全未显式配置本模块任何驱动键时命中
        if (matchIfAbsent && !hasAnyExplicitDriverKey(env)) {
            return true;
        }

        return false;
    }

    /**
     * 判断容器中是否存在被声明式注册注解标注的方法。
     * <p>
     * 基于 Bean 定义的 {@link org.springframework.core.type.AnnotationMetadata}（ASM 读取，
     * 不触发类加载）扫描，因此不会因为提前加载类而影响启动。
     * <p>
     * 时序说明：Spring Boot 的自动配置由 {@code DeferredImportSelector} 导入，
     * 在<b>所有用户 {@code @Configuration} 类注册完毕之后</b>才求值，
     * 因此此处能够看到应用侧（如 {@code AuthConfig}）的 Bean 定义。
     *
     * @param context 条件求值上下文
     * @return 存在任一标注方法返回 true
     */
    private boolean hasDeclarativeRegistration(ConditionContext context) {
        BeanDefinitionRegistry registry = context.getRegistry();
        if (registry == null) {
            return false;
        }
        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition bd;
            try {
                bd = registry.getBeanDefinition(beanName);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!(bd instanceof AnnotatedBeanDefinition)) {
                continue;
            }
            try {
                var metadata = ((AnnotatedBeanDefinition) bd).getMetadata();
                for (String annotation : declarativeAnnotations) {
                    if (!metadata.getAnnotatedMethods(annotation).isEmpty()) {
                        return true;
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
                // 元数据读取失败（如缺失可选依赖）时跳过该 Bean 定义，不影响整体判定
            }
        }
        return false;
    }

    /**
     * 判断用户是否显式配置了本模块的任意驱动键。
     * <p>
     * 单值键非空，或映射配置块下存在任意键（无论其值是否为本驱动名）均视为已显式配置。
     *
     * @param env Spring 环境
     * @return 存在任意显式驱动键返回 true
     */
    private boolean hasAnyExplicitDriverKey(Environment env) {
        for (String key : singleKeys) {
            if (env.getProperty(key) != null) {
                return true;
            }
        }
        if (mapKeyPrefix != null) {
            return !mapDriverKeys(env).isEmpty();
        }
        return false;
    }

    /**
     * @return 判定依据的可读描述，用于调试日志
     */
    private String describe() {
        StringBuilder sb = new StringBuilder();
        if (enableKey != null) {
            sb.append(enableKey).append(' ');
        }
        for (String key : singleKeys) {
            sb.append(key).append(' ');
        }
        if (mapKeyPrefix != null) {
            sb.append(mapKeyPrefix).append('*').append(mapKeySuffix == null ? "" : mapKeySuffix);
        }
        for (String annotation : declarativeAnnotations) {
            sb.append(" @").append(annotation.substring(annotation.lastIndexOf('.') + 1));
        }
        return sb.toString().trim();
    }

    /**
     * 枚举形如 {@code <prefix><name><suffix>} 的配置键。
     *
     * @param env Spring 环境
     * @return 命中的配置键集合
     */
    private Set<String> mapDriverKeys(Environment env) {
        Set<String> keys = new LinkedHashSet<>();
        if (!(env instanceof ConfigurableEnvironment)) {
            return keys;
        }
        for (PropertySource<?> source : ((ConfigurableEnvironment) env).getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource)) {
                continue;
            }
            for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                if (name.startsWith(mapKeyPrefix)
                        && (mapKeySuffix == null || name.endsWith(mapKeySuffix))) {
                    keys.add(name);
                }
            }
        }
        return keys;
    }
}
