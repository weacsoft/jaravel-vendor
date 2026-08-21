package com.weacsoft.jaravel.vendor.database.dialect;

import gaarason.database.appointment.DbType;
import gaarason.database.config.DefaultQueryBuilderConfig;
import gaarason.database.config.QueryBuilderConfig;
import gaarason.database.contract.connection.GaarasonDataSource;
import gaarason.database.contract.eloquent.Builder;
import gaarason.database.contract.eloquent.Model;
import gaarason.database.contract.query.Grammar;
import gaarason.database.exception.TypeNotSupportedException;
import gaarason.database.query.QueryBuilder;
import gaarason.database.util.ObjectUtils;

import java.util.Locale;

/**
 * jaravel Oracle 方言路由配置（dialect name: <b>jaravel-oracle</b>）。
 * <p>
 * 与官方 {@link DefaultQueryBuilderConfig} 的行为一致，唯一区别：
 * JDBC 产品名识别为 Oracle 家族（11g 或 12c 分组）时，使用
 * {@link JaravelOracleGrammar} 生成 SQL；其他数据库类型完全委托官方默认配置，
 * 不改变任何现有行为。
 * <p>
 * 通过 {@link JaravelOracleDialect#register(Object)} 注册进 gaarason 容器后生效，
 * 注册优先级高于官方默认配置（{@code getOrder() = -100 < 0}）。
 *
 * @author weacsoft
 */
public class JaravelOracleQueryBuilderConfig implements QueryBuilderConfig {

    /** 绑定的 JDBC 产品名（小写）；null 表示未绑定（工厂实例） */
    private final String productName;

    /** 绑定的数据库类型；null 表示未绑定 */
    private final DbType dbType;

    /**
     * 工厂实例（未绑定方言），由 gaarason 容器实例化；
     * 实际方言实例由 {@link #forProductName(String)} 生成。
     */
    public JaravelOracleQueryBuilderConfig() {
        this(null, null);
    }

    private JaravelOracleQueryBuilderConfig(String productName, DbType dbType) {
        this.productName = productName;
        this.dbType = dbType;
    }

    @Override
    public String getValueSymbol() {
        return "'";
    }

    @Override
    public boolean support(String databaseProductName) {
        // 仅认领 Oracle 家族(11g / 12c / 达梦 / 虚谷)的连接，其余交给官方默认配置，
        // 保证本方言对非 Oracle 环境零影响
        DbType detected = DbType.fromProductName(databaseProductName);
        return detected != null
            && (detected.getDialectGroup() == DbType.DialectGroup.ORACLE
                || detected.getDialectGroup() == DbType.DialectGroup.ORACLE_12C);
    }

    @Override
    public QueryBuilderConfig forProductName(String databaseProductName) {
        DbType detected = DbType.fromProductName(databaseProductName);
        if (detected == null) {
            throw new TypeNotSupportedException(
                "Database product name [" + databaseProductName + "] not supported.");
        }
        if (!isOracleGroup(detected)) {
            // 兜底：非 Oracle 家族不应路由到本配置（support() 已拦截），委托官方行为
            return new DefaultQueryBuilderConfig().forProductName(databaseProductName);
        }
        return new JaravelOracleQueryBuilderConfig(
            databaseProductName.toLowerCase(Locale.ENGLISH), detected);
    }

    /**
     * 判断某数据库类型是否属于 Oracle 家族（11g ROWNUM 路径或 12c OFFSET/FETCH 分组，
     * 含达梦 DM、虚谷 Xugu 等 12c 分组数据库）
     */
    private static boolean isOracleGroup(DbType type) {
        return type != null
            && (type.getDialectGroup() == DbType.DialectGroup.ORACLE
                || type.getDialectGroup() == DbType.DialectGroup.ORACLE_12C);
    }

    private boolean isOracleFamily() {
        return isOracleGroup(dbType);
    }

    /**
     * 已绑定的方言实例委托给官方默认配置
     */
    private QueryBuilderConfig officialBound() {
        if (productName == null) {
            throw new IllegalStateException(
                "DbType not bound. This config should be obtained via forProductName() first.");
        }
        return new DefaultQueryBuilderConfig().forProductName(productName);
    }

    @Override
    public Grammar newGrammar(String tableName) {
        if (isOracleFamily()) {
            return new JaravelOracleGrammar(tableName);
        }
        return officialBound().newGrammar(tableName);
    }

    @Override
    public <T, K> Builder<?, T, K> newBuilder(GaarasonDataSource gaarasonDataSource, Model<?, T, K> model) {
        if (isOracleFamily()) {
            return new QueryBuilder<T, K>().initBuilder(
                gaarasonDataSource, ObjectUtils.typeCast(model),
                new JaravelOracleGrammar(model.getTableName()));
        }
        return officialBound().newBuilder(gaarasonDataSource, model);
    }
}
