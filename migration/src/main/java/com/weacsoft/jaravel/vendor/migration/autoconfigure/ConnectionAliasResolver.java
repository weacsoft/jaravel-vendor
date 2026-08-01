package com.weacsoft.jaravel.vendor.migration.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 读取 database 模块 {@code ConnectionManager} 中由 {@code @RegisterConnection} 注册的连接别名。
 * <p>
 * migration 模块<b>不依赖</b> database 模块（迁移引擎需保持可在纯 Java / 无 gaarason 环境下运行），
 * 因此这里通过反射做「软依赖」桥接：
 * <ul>
 *   <li>类路径上存在 database 模块 → 取出注册表中的别名，与 Spring 的 DataSource bean 合并；</li>
 *   <li>不存在 → 静默返回空表，退化为原有的「bean 名即别名」行为。</li>
 * </ul>
 * 由此保证 {@code Migration#connection()} 与 Model 的 {@code @DataSource} 使用同一套别名语义。
 */
final class ConnectionAliasResolver {

    private static final Logger log = LoggerFactory.getLogger(ConnectionAliasResolver.class);

    private static final String MANAGER_CLASS =
            "com.weacsoft.jaravel.vendor.database.ConnectionManager";

    private ConnectionAliasResolver() {
    }

    /**
     * 取出 {@code ConnectionManager} 中已注册的连接别名及其底层 {@link DataSource}。
     *
     * @return 别名 → 数据源；database 模块不存在或读取失败时返回空表
     */
    @SuppressWarnings("unchecked")
    static Map<String, DataSource> registeredConnections() {
        Map<String, DataSource> result = new LinkedHashMap<>();
        try {
            Class<?> manager = Class.forName(MANAGER_CLASS, false,
                    Thread.currentThread().getContextClassLoader());

            Method namesMethod = manager.getMethod("connectionNames");
            Set<String> names = (Set<String>) namesMethod.invoke(null);
            if (names == null || names.isEmpty()) {
                return result;
            }

            Method connectionMethod = manager.getMethod("connection", String.class);
            for (String name : names) {
                Object gaarasonDataSource = connectionMethod.invoke(null, name);
                DataSource raw = unwrap(gaarasonDataSource);
                if (raw != null) {
                    result.put(name, raw);
                }
            }
            log.debug("[migration] 从 ConnectionManager 读取到连接别名: {}", result.keySet());
        } catch (ClassNotFoundException e) {
            // database 模块未引入，属正常情况
            log.debug("[migration] 未检测到 database 模块，跳过 ConnectionManager 别名合并");
        } catch (Exception e) {
            log.warn("[migration] 读取 ConnectionManager 连接别名失败，将仅使用 Spring DataSource bean: {}",
                    e.getMessage());
        }
        return result;
    }

    /**
     * 从 {@code GaarasonDataSource} 中取出底层的 {@link DataSource}。
     * <p>
     * gaarason 的 {@code GaarasonDataSource} 本身继承 {@link DataSource}，
     * 可直接使用；此处保留兜底以兼容不同版本。
     *
     * @param gaarasonDataSource gaarason 数据源对象
     * @return 底层 JDBC 数据源，无法解析时返回 {@code null}
     */
    private static DataSource unwrap(Object gaarasonDataSource) {
        if (gaarasonDataSource == null) {
            return null;
        }
        if (gaarasonDataSource instanceof DataSource) {
            return (DataSource) gaarasonDataSource;
        }
        for (String getter : new String[]{"getMasterDataSource", "getRealDataSource", "getDataSource"}) {
            try {
                Method m = gaarasonDataSource.getClass().getMethod(getter);
                Object value = m.invoke(gaarasonDataSource);
                if (value instanceof DataSource) {
                    return (DataSource) value;
                }
                if (value instanceof java.util.List<?> list && !list.isEmpty()
                        && list.get(0) instanceof DataSource ds) {
                    return ds;
                }
            } catch (Exception ignored) {
                // 尝试下一个 getter
            }
        }
        return null;
    }
}
