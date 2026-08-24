package com.weacsoft.jaravel.vendor.springboot.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 缓存配置属性，前缀 {@code jaravel.cache}，对齐 Laravel {@code config/cache.php}。
 * <p>
 * 本类是纯数据载体（{@code @ConfigurationProperties} 绑定），自动装配时映射为缓存核心模块的
 * 纯 Java POJO {@code com.weacsoft.jaravel.vendor.cache.CacheConfig} 后交给 {@code CacheManager}——
 * 缓存核心（cache 模块）对 Spring 零依赖。
 * <pre>
 * jaravel:
 *   cache:
 *     default-store: array          # 默认 store 名称：array / file / database / redis
 *     prefix: jaravel               # 缓存键前缀
 *     stores:                       # 按需配置 store，对齐 Laravel stores 数组
 *       array:
 *         driver: array
 *       file:
 *         driver: file
 *         dir: /tmp/jaravel-cache
 *       database:
 *         driver: database
 *         table: jaravel_cache
 *       redis:
 *         driver: redis
 *         connection: cache
 * </pre>
 * <p>
 * <b>stores 配置</b>：只有配置在 stores 中的 store 才会被创建。
 * 若 stores 为空，则只创建 default-store 对应的默认 store（driver 名与 store 名相同）。
 * <p>
 * <b>向后兼容</b>：{@code file-dir} 和 {@code database-table} 仍可直接在顶层配置，
 * 也可在对应 store 的配置中通过 {@code dir} / {@code table} 指定。
 */
@ConfigurationProperties(prefix = "jaravel.cache")
public class CacheProperties {

    /** 默认 store 名称 */
    private String defaultStore = "array";

    /** 缓存键前缀 */
    private String prefix = "jaravel";

    /** file 驱动目录，空串表示使用系统临时目录下的 jaravel-cache 子目录（顶层快捷配置） */
    private String fileDir = "";

    /** database 驱动表名，默认 jaravel_cache（顶层快捷配置） */
    private String databaseTable = "jaravel_cache";

    /** store 配置映射，key 为 store 名称，value 为该 store 的驱动配置 */
    private Map<String, StoreConfig> stores = new LinkedHashMap<>();

    public String getDefaultStore() {
        return defaultStore;
    }

    public void setDefaultStore(String defaultStore) {
        this.defaultStore = defaultStore;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getFileDir() {
        return fileDir;
    }

    public void setFileDir(String fileDir) {
        this.fileDir = fileDir;
    }

    public String getDatabaseTable() {
        return databaseTable;
    }

    public void setDatabaseTable(String databaseTable) {
        this.databaseTable = databaseTable;
    }

    public Map<String, StoreConfig> getStores() {
        return stores;
    }

    public void setStores(Map<String, StoreConfig> stores) {
        this.stores = stores;
    }

    /**
     * 映射为缓存核心模块的纯 Java 配置对象（cache 模块不感知本类）。
     *
     * @return 纯 Java 配置
     */
    public com.weacsoft.jaravel.vendor.cache.CacheConfig toCacheConfig() {
        com.weacsoft.jaravel.vendor.cache.CacheConfig cfg = new com.weacsoft.jaravel.vendor.cache.CacheConfig();
        cfg.setDefaultStore(defaultStore);
        cfg.setPrefix(prefix);
        cfg.setFileDir(fileDir);
        cfg.setDatabaseTable(databaseTable);
        Map<String, com.weacsoft.jaravel.vendor.cache.CacheConfig.StoreConfig> stores = new LinkedHashMap<>();
        for (Map.Entry<String, StoreConfig> entry : this.stores.entrySet()) {
            com.weacsoft.jaravel.vendor.cache.CacheConfig.StoreConfig sc =
                    new com.weacsoft.jaravel.vendor.cache.CacheConfig.StoreConfig();
            sc.setDriver(entry.getValue().getDriver());
            sc.setDir(entry.getValue().getDir());
            sc.setTable(entry.getValue().getTable());
            sc.setConnection(entry.getValue().getConnection());
            stores.put(entry.getKey(), sc);
        }
        cfg.setStores(stores);
        return cfg;
    }

    /**
     * 单个 store 的配置，对齐 Laravel {@code config/cache.php} 的 stores 数组项。
     * <p>
     * 每个配置项含 {@code driver}（驱动名）和驱动特定的参数（如 {@code dir}、{@code table}、
     * {@code connection} 等），由 {@code CacheDriverFactory} 解释。
     */
    public static class StoreConfig {

        /** 驱动名称：array / file / database / redis / 自定义 */
        private String driver;

        /** file 驱动目录（可选，覆盖顶层 file-dir） */
        private String dir;

        /** database 驱动表名（可选，覆盖顶层 database-table） */
        private String table;

        /** redis 连接名（可选，默认 cache） */
        private String connection;

        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getConnection() {
            return connection;
        }

        public void setConnection(String connection) {
            this.connection = connection;
        }

        /**
         * 转为工厂驱动的配置 Map。
         *
         * @return 配置参数 Map，含 dir / table / connection 等
         */
        public Map<String, Object> toConfigMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            if (dir != null) map.put("dir", dir);
            if (table != null) map.put("table", table);
            if (connection != null) map.put("connection", connection);
            return map;
        }
    }
}
