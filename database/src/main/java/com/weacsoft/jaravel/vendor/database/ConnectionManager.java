package com.weacsoft.jaravel.vendor.database;

import com.weacsoft.jaravel.vendor.core.SpringContext;
import gaarason.database.bootstrap.ContainerBootstrap;
import gaarason.database.connection.GaarasonDataSourceBuilder;
import gaarason.database.contract.connection.GaarasonDataSource;
import gaarason.database.provider.ModelInstanceProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接注册表，对齐 Laravel 的 {@code Illuminate\Database\DatabaseManager}。
 * <p>
 * 保存由 {@link RegisterConnection} 注册的连接别名 → {@link GaarasonDataSource} 映射，
 * 并统一持有全局唯一的 {@link ContainerBootstrap}。
 *
 * <h3>解析顺序（重点）</h3>
 * {@link #connection(String)} 按以下顺序解析别名，<b>先扫描注册表，找不到再回退 Spring 容器</b>：
 * <ol>
 *   <li>本注册表（{@code @RegisterConnection} 注册的别名）；</li>
 *   <li>Spring 容器中同名的 {@code GaarasonDataSource} bean；</li>
 *   <li>Spring 容器中同名的 {@code javax.sql.DataSource} bean（自动用全局 Container 包装）；</li>
 *   <li>以上均无 → 抛出 {@link IllegalStateException}。</li>
 * </ol>
 * 这样业务方既可以用注解式别名，也兼容历史的 {@code @Bean} 写法。
 *
 * <h3>ContainerBootstrap 唯一性</h3>
 * gaarason 在 SpringBoot 环境下，每个 {@code GaarasonDataSource} 都必须绑定
 * {@code ContainerBootstrap}；且<b>所有数据源必须共用同一个实例</b>，否则 Model 注册表、
 * 类型转换器等会分裂到不同容器中，导致查询报错。
 * 因此本类通过 {@link #container()} 暴露全局唯一实例，任何包装动作都复用它。
 */
public final class ConnectionManager {

    /** 默认连接别名，对齐 Laravel 的 {@code database.default}。 */
    public static final String DEFAULT_CONNECTION = "sqlite";

    /** 别名 → 连接。使用 LinkedHashMap 保持注册顺序，便于 {@code db:list} 之类命令输出。 */
    private static final Map<String, GaarasonDataSource> CONNECTIONS =
            java.util.Collections.synchronizedMap(new LinkedHashMap<>());

    /** 别名 → 该连接底层的原始 {@link javax.sql.DataSource}，供事务管理器/JdbcTemplate 使用。 */
    private static final Map<String, javax.sql.DataSource> RAW_DATA_SOURCES =
            java.util.Collections.synchronizedMap(new LinkedHashMap<>());

    /** 由裸 DataSource 包装而来的连接缓存，避免重复包装产生多个 GaarasonDataSource。 */
    private static final Map<String, GaarasonDataSource> WRAPPED_CACHE = new ConcurrentHashMap<>();

    /** 全局唯一的 gaarason 容器。 */
    private static volatile ContainerBootstrap container;

    /** 当前默认连接别名。 */
    private static volatile String defaultConnection = DEFAULT_CONNECTION;

    /** 是否有连接通过 {@code defaultConnection = true} 显式声明为默认。 */
    private static volatile boolean defaultExplicitlySet = false;

    private ConnectionManager() {
    }

    // ------------------------------------------------------------------
    // ContainerBootstrap
    // ------------------------------------------------------------------

    /**
     * 设置全局唯一的 gaarason 容器。
     * <p>
     * 由 {@code DatabaseConfig#containerBootstrap()}（发布出来的配置类）或框架自动配置调用。
     * 重复设置同一实例是幂等的；设置为<b>不同</b>实例会被拒绝并保留首个，
     * 以确保「从头到尾使用同一个 ContainerBootstrap」这一硬性约束。
     *
     * @param bootstrap gaarason 容器
     */
    public static void setContainer(ContainerBootstrap bootstrap) {
        if (bootstrap == null) {
            return;
        }
        if (container == null) {
            synchronized (ConnectionManager.class) {
                if (container == null) {
                    container = bootstrap;
                    return;
                }
            }
        }
        if (container != bootstrap) {
            throw new IllegalStateException(
                    "检测到重复的 ContainerBootstrap 实例。gaarason 要求所有 GaarasonDataSource "
                            + "共用同一个 ContainerBootstrap，请勿重复创建；"
                            + "在 DatabaseConfig 中注入已有的 ContainerBootstrap 参数即可。");
        }
    }

    /**
     * 获取全局唯一的 gaarason 容器。
     * <p>
     * 若尚未设置（例如业务工程没有发布 {@code DatabaseConfig}），
     * 则依次尝试：Spring 容器中的 {@code ContainerBootstrap} bean → 现场创建并初始化一个。
     *
     * @return 全局 gaarason 容器，永不为 {@code null}
     */
    public static ContainerBootstrap container() {
        ContainerBootstrap local = container;
        if (local != null) {
            return local;
        }
        synchronized (ConnectionManager.class) {
            if (container != null) {
                return container;
            }
            ContainerBootstrap fromSpring = SpringContext.beanOrNull(ContainerBootstrap.class);
            container = fromSpring != null ? fromSpring : createDefaultContainer();
            return container;
        }
    }

    /**
     * 兜底创建一个 gaarason 容器，行为与发布出来的 {@code DatabaseConfig#containerBootstrap()} 一致。
     *
     * @return 已完成初始化的容器
     */
    private static ContainerBootstrap createDefaultContainer() {
        ContainerBootstrap bootstrap = ContainerBootstrap.build();
        bootstrap.defaultRegister();
        ModelInstanceProvider provider = bootstrap.getBean(ModelInstanceProvider.class);
        provider.register(SpringContext::bean);
        bootstrap.bootstrapGaarasonAutoconfiguration();
        bootstrap.initialization();
        return bootstrap;
    }

    // ------------------------------------------------------------------
    // 注册
    // ------------------------------------------------------------------

    /**
     * 注册一个连接别名。
     *
     * @param name       连接别名
     * @param dataSource gaarason 数据源
     */
    public static void addConnection(String name, GaarasonDataSource dataSource) {
        addConnection(name, dataSource, null);
    }

    /**
     * 注册一个连接别名，并同时登记其底层原始数据源。
     * <p>
     * <b>默认连接的自动推选</b>：若此前没有任何连接通过
     * {@link RegisterConnection#defaultConnection()} 显式声明为默认，
     * 则<b>第一个注册的连接</b>自动成为默认连接。这样业务方即便一个默认标记都不写，
     * 事务管理器、{@code JdbcTemplate} 等仍能拿到可用的数据源。
     *
     * @param name       连接别名
     * @param dataSource gaarason 数据源
     * @param raw        底层原始数据源，可为 {@code null}（此时尝试从 gaarason 数据源上取）
     */
    public static void addConnection(String name, GaarasonDataSource dataSource, javax.sql.DataSource raw) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("连接别名不能为空");
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("连接 [" + name + "] 的 GaarasonDataSource 不能为 null");
        }
        boolean first;
        synchronized (CONNECTIONS) {
            first = CONNECTIONS.isEmpty();
            CONNECTIONS.put(name, dataSource);
        }
        javax.sql.DataSource effectiveRaw = raw != null ? raw : extractRaw(dataSource);
        if (effectiveRaw != null) {
            RAW_DATA_SOURCES.put(name, effectiveRaw);
        }
        // 第一个注册的连接在无显式默认声明时自动成为默认连接
        if (first && !defaultExplicitlySet) {
            defaultConnection = name;
        }
    }

    /**
     * 尝试从 {@link GaarasonDataSource} 上取出其底层的原始数据源。
     *
     * @param dataSource gaarason 数据源
     * @return 原始数据源，取不到返回 {@code null}
     */
    private static javax.sql.DataSource extractRaw(GaarasonDataSource dataSource) {
        try {
            java.util.List<javax.sql.DataSource> masters = dataSource.getMasterDataSourceList();
            if (masters != null && !masters.isEmpty()) {
                return masters.get(0);
            }
        } catch (RuntimeException ignored) {
            // gaarason 版本差异导致取不到时静默降级，不影响连接注册
        }
        return null;
    }

    /**
     * 设置默认连接别名（由 {@code @RegisterConnection(defaultConnection = true)} 触发）。
     *
     * @param name 连接别名
     */
    public static void setDefaultConnection(String name) {
        if (name != null && !name.isEmpty()) {
            defaultConnection = name;
            defaultExplicitlySet = true;
        }
    }

    /**
     * @return 当前默认连接别名
     */
    public static String getDefaultConnection() {
        return defaultConnection;
    }

    /**
     * @return 当前默认连接别名，语义同 {@link #getDefaultConnection()}
     */
    public static String defaultConnectionName() {
        return defaultConnection;
    }

    /**
     * 取默认连接底层的原始 {@link javax.sql.DataSource}。
     * <p>
     * 供 {@link JaravelDataSource} 惰性委托，进而支撑
     * {@code DataSourceTransactionManager}、{@code JdbcTemplate} 等 Spring 组件。
     * <b>先查本注册表，找不到再回退 Spring 容器</b>，与 {@link #connection(String)} 一致。
     *
     * @return 原始数据源，完全无连接时返回 {@code null}
     */
    public static javax.sql.DataSource defaultRawDataSource() {
        return rawDataSource(defaultConnection);
    }

    /**
     * 按别名取底层原始 {@link javax.sql.DataSource}：先注册表，后 Spring 容器。
     *
     * @param name 连接别名，{@code null} 或空表示默认连接
     * @return 原始数据源，找不到返回 {@code null}
     */
    public static javax.sql.DataSource rawDataSource(String name) {
        String alias = (name == null || name.isEmpty()) ? defaultConnection : name;

        javax.sql.DataSource registered = RAW_DATA_SOURCES.get(alias);
        if (registered != null) {
            return registered;
        }
        // 注册表里没有，但可能有 GaarasonDataSource（raw 抽取失败），再试一次
        GaarasonDataSource gaarason = CONNECTIONS.get(alias);
        if (gaarason != null) {
            javax.sql.DataSource raw = extractRaw(gaarason);
            if (raw != null) {
                RAW_DATA_SOURCES.put(alias, raw);
                return raw;
            }
        }
        // 回退 Spring 容器：同名 bean → 任意/主 DataSource（但要排除框架自身的委托，避免自引用死循环）
        javax.sql.DataSource fromSpring = SpringContext.beanOrNull(alias, javax.sql.DataSource.class);
        if (fromSpring == null) {
            fromSpring = SpringContext.beanOrNull(javax.sql.DataSource.class);
        }
        if (fromSpring instanceof JaravelDataSource) {
            return null;
        }
        if (fromSpring instanceof GaarasonDataSource) {
            // 容器里放的是 gaarason 数据源，取其底层原始数据源
            return extractRaw((GaarasonDataSource) fromSpring);
        }
        return fromSpring;
    }

    /**
     * 是否已有任何连接可用（注册表非空）。
     * <p>
     * 供 cache、queue 等模块判断「数据库驱动是否可用」，避免依赖
     * {@code @ConditionalOnBean(DataSource.class)} 这种与 Spring 强绑定的判断。
     *
     * @return 注册表中是否存在连接
     */
    public static boolean hasAnyConnection() {
        return !CONNECTIONS.isEmpty();
    }

    /**
     * @return 已注册的连接别名集合（不含仅存在于 Spring 容器中的）
     */
    public static Set<String> connectionNames() {
        synchronized (CONNECTIONS) {
            return new java.util.LinkedHashSet<>(CONNECTIONS.keySet());
        }
    }

    /**
     * 判断别名是否已在注册表中（不查 Spring）。
     *
     * @param name 连接别名
     * @return 是否已注册
     */
    public static boolean hasConnection(String name) {
        return name != null && CONNECTIONS.containsKey(name);
    }

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    /**
     * 获取默认连接。
     *
     * @return 默认 {@link GaarasonDataSource}
     */
    public static GaarasonDataSource connection() {
        return connection(defaultConnection);
    }

    /**
     * 按别名解析连接：<b>先查注册表，再回退 Spring 容器</b>。
     *
     * @param name 连接别名，{@code null} 或空表示默认连接
     * @return 对应的 {@link GaarasonDataSource}
     * @throws IllegalStateException 别名无法解析时
     */
    public static GaarasonDataSource connection(String name) {
        String alias = (name == null || name.isEmpty()) ? defaultConnection : name;

        // 1) 注册表（@RegisterConnection）
        GaarasonDataSource registered = CONNECTIONS.get(alias);
        if (registered != null) {
            return registered;
        }

        // 2) 已包装过的 Spring DataSource 缓存
        GaarasonDataSource cached = WRAPPED_CACHE.get(alias);
        if (cached != null) {
            return cached;
        }

        // 3) 回退 Spring 容器
        GaarasonDataSource fromSpring = resolveFromSpring(alias);
        if (fromSpring != null) {
            return fromSpring;
        }

        throw new IllegalStateException(
                "未找到数据库连接 [" + alias + "]。已注册的别名: " + connectionNames()
                        + "。请在 config/DatabaseConfig.java 中使用 @RegisterConnection(\"" + alias
                        + "\") 注册，或确保 Spring 容器中存在同名的 DataSource bean。"
                        + "（可执行 artisan vendor:publish --tag=database 生成 DatabaseConfig）");
    }

    /**
     * 从 Spring 容器解析别名对应的数据源，并用全局 Container 包装裸 DataSource。
     *
     * @param alias 连接别名
     * @return 解析结果，找不到返回 {@code null}
     */
    private static GaarasonDataSource resolveFromSpring(String alias) {
        // 3.1 同名的 GaarasonDataSource bean
        GaarasonDataSource gaarason = SpringContext.beanOrNull(alias, GaarasonDataSource.class);
        if (gaarason != null) {
            return WRAPPED_CACHE.computeIfAbsent(alias, k -> gaarason);
        }

        // 3.2 默认别名特殊照顾：按类型取容器中唯一/主的 GaarasonDataSource，直接使用不再包装
        if (DEFAULT_CONNECTION.equals(alias)) {
            GaarasonDataSource primaryGaarason = SpringContext.beanOrNull(GaarasonDataSource.class);
            if (primaryGaarason != null) {
                return WRAPPED_CACHE.computeIfAbsent(alias, k -> primaryGaarason);
            }
        }

        // 3.3 同名的 javax.sql.DataSource bean → 用全局 Container 包装
        javax.sql.DataSource raw = SpringContext.beanOrNull(alias, javax.sql.DataSource.class);
        if (raw == null && DEFAULT_CONNECTION.equals(alias)) {
            // "sqlite" 特殊照顾：回退到容器中唯一/主 DataSource
            raw = SpringContext.beanOrNull(javax.sql.DataSource.class);
        }
        if (raw instanceof JaravelDataSource) {
            // 框架自身的惰性委托，包装它会造成自引用死循环
            raw = null;
        }
        if (raw instanceof GaarasonDataSource) {
            // 已经是 gaarason 数据源，直接使用，避免二次包装
            GaarasonDataSource already = (GaarasonDataSource) raw;
            return WRAPPED_CACHE.computeIfAbsent(alias, k -> already);
        }
        if (raw != null) {
            javax.sql.DataSource finalRaw = raw;
            return WRAPPED_CACHE.computeIfAbsent(alias, k -> wrap(finalRaw));
        }
        return null;
    }

    /**
     * 用<b>全局唯一</b>的 {@link ContainerBootstrap} 将裸 {@link javax.sql.DataSource}
     * 包装为 {@link GaarasonDataSource}。
     *
     * @param dataSource 原始数据源
     * @return gaarason 数据源
     */
    public static GaarasonDataSource wrap(javax.sql.DataSource dataSource) {
        return GaarasonDataSourceBuilder.build(dataSource, container());
    }

    /**
     * 清空注册表，仅供测试与应用关闭时使用。
     */
    public static void clear() {
        CONNECTIONS.clear();
        RAW_DATA_SOURCES.clear();
        WRAPPED_CACHE.clear();
        defaultConnection = DEFAULT_CONNECTION;
        defaultExplicitlySet = false;
        container = null;
    }
}
