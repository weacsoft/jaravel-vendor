package com.weacsoft.jaravel.vendor.database.dialect;

import gaarason.database.config.QueryBuilderConfig;
import gaarason.database.contract.function.InstanceCreatorFunctionalInterface;
import gaarason.database.core.Container;

/**
 * jaravel Oracle 方言注册入口（dialect name: <b>jaravel-oracle</b>）。
 * <p>
 * 用法（在创建 gaarason {@code ContainerBootstrap} 之后、执行任何查询之前调用一次）：
 * <pre>{@code
 * ContainerBootstrap bootstrap = ContainerBootstrap.build();
 * bootstrap.defaultRegister();
 * JaravelOracleDialect.register(bootstrap);   // ← 注册 Oracle 方言补丁
 * bootstrap.bootstrapGaarasonAutoconfiguration();
 * bootstrap.initialization();
 * }</pre>
 * 注册后，JDBC 产品名为 Oracle 的连接（11g / 12c）自动改用
 * {@link JaravelOracleGrammar} 生成 SQL；其他数据库类型行为不变。
 * <p>
 * 本方言为纯子类扩展，基于 gaarason/database 7.0.15（未修改）编译，
 * 不依赖任何 gaarason 未公开 API。
 *
 * @author weacsoft
 */
public final class JaravelOracleDialect {

    /** 方言名称（注册优先级序号：越小越优先） */
    public static final String NAME = "jaravel-oracle";

    /** 注册优先级（官方默认为 0） */
    private static final int ORDER = -100;

    private JaravelOracleDialect() {
    }

    /**
     * 把本方言注册进 gaarason 容器。
     * <p>
     * 注意：必须在容器首次解析 {@link QueryBuilderConfig}（即执行第一条查询）之前调用，
     * gaarason 容器不允许在使用后追加注册。
     *
     * @param container gaarason 容器（{@code ContainerBootstrap} 或其子类实例）
     */
    public static void register(Container container) {
        container.register(QueryBuilderConfig.class,
            new InstanceCreatorFunctionalInterface<QueryBuilderConfig>() {
                @Override
                public QueryBuilderConfig execute(Class<QueryBuilderConfig> clazz) {
                    return new JaravelOracleQueryBuilderConfig();
                }

                @Override
                public Integer getOrder() {
                    return ORDER;
                }
            });
    }
}
