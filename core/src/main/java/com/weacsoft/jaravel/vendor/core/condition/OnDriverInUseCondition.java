package com.weacsoft.jaravel.vendor.core.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;

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

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        boolean matched = evaluate(env);
        if (logger.isDebugEnabled()) {
            logger.debug("[vendor] 驱动 [{}] {}装配（判定依据：{}）", driverName,
                    matched ? "" : "不", describe());
        }
        return matched;
    }

    /**
     * 执行判定。
     *
     * @param env Spring 环境
     * @return 是否应当装配
     */
    private boolean evaluate(Environment env) {
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

        // 4) 兜底默认驱动：用户完全未显式配置本模块任何驱动键时命中
        if (matchIfAbsent && !hasAnyExplicitDriverKey(env)) {
            return true;
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
