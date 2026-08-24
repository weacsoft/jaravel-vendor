package com.weacsoft.jaravel.vendor.migration.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 方言工厂，根据数据源自动检测数据库产品名并返回对应的 {@link Dialect} 实现。
 * <p>
 * 支持的数据库：MySQL、SQLite、H2、SQL Server、PostgreSQL、Oracle。
 * 未识别的数据库默认使用 MySQL 方言。
 */
public final class DialectFactory {

    private static final Logger log = LoggerFactory.getLogger(DialectFactory.class);

    private DialectFactory() {
    }

    /**
     * 从数据源检测数据库产品名并返回对应的方言实现。
     *
     * @param dataSource 数据源
     * @return 方言实现
     */
    public static Dialect detect(DataSource dataSource) {
        String productName = detectProductName(dataSource);
        return create(productName);
    }

    /**
     * 根据数据库产品名（小写）创建方言实现。
     *
     * @param productName 数据库产品名（大小写不敏感）
     * @return 方言实现
     */
    public static Dialect create(String productName) {
        if (productName == null || productName.isEmpty()) {
            log.warn("[migration] 数据库产品名为空，使用默认 MySQL 方言");
            return new MysqlDialect();
        }
        String lower = productName.toLowerCase();

        if (lower.contains("mysql")) {
            return new MysqlDialect();
        }
        if (lower.contains("sqlite")) {
            return new SqliteDialect();
        }
        if (lower.contains("h2")) {
            return new H2Dialect();
        }
        if (lower.contains("sql server") || lower.contains("microsoft sql")) {
            return new SqlServerDialect();
        }
        if (lower.contains("postgresql") || lower.contains("postgres")) {
            return new PostgresqlDialect();
        }
        if (lower.contains("oracle")) {
            return new OracleDialect();
        }
        // 默认 MySQL
        log.warn("[migration] 未识别的数据库产品 '{}'，使用默认 MySQL 方言", productName);
        return new MysqlDialect();
    }

    /**
     * 从数据源获取数据库产品名。
     *
     * @param dataSource 数据源
     * @return 数据库产品名（小写），失败时返回 "mysql"
     */
    public static String detectProductName(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDatabaseProductName().toLowerCase();
        } catch (Exception e) {
            log.warn("[migration] 无法识别数据库产品，使用默认 MySQL 方言: {}", e.getMessage());
            return "mysql";
        }
    }
}
