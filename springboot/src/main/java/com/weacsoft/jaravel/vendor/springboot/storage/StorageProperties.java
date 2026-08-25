package com.weacsoft.jaravel.vendor.springboot.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存储模块配置属性，对齐 Laravel {@code config/filesystems.php}。
 *
 * <h3>配置示例</h3>
 * <pre>
 * jaravel:
 *   storage:
 *     enabled: true
 *     default-disk: local
 *     disks:
 *       local:
 *         driver: local
 *         root: storage/app
 *       public:
 *         driver: local
 *         root: storage/app/public
 *         url: /storage
 *         visibility: public
 *       uploads:
 *         driver: local
 *         root: /data/uploads
 *         options:
 *           custom-key: custom-value
 *       files:
 *         driver: database        # storage-database 模块
 *         binary: true            # true=BLOB；false=LONGTEXT(base64)
 *         content-column: content # 内容列名（默认 content）
 *         chunk-size: 1048576     # 分片上限（默认 1MB）
 *         table-prefix: storage_  # 表前缀（默认 storage_）
 *         connection: primary     # 可选，@RegisterConnection 别名；省略=默认连接
 * </pre>
 *
 * <p>
 * 未配置任何 disks 时，模块会自动注册一个名为 {@code local}、
 * 根目录为 {@code storage/app} 的默认磁盘，保证开箱即用。
 */
@ConfigurationProperties(prefix = "jaravel.storage")
public class StorageProperties {

    /** 是否启用存储模块 */
    private boolean enabled = true;

    /** 默认磁盘名称 */
    private String defaultDisk = "local";

    /** 磁盘配置：磁盘名 -> 磁盘配置 */
    private Map<String, DiskConfig> disks = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultDisk() {
        return defaultDisk;
    }

    public void setDefaultDisk(String defaultDisk) {
        this.defaultDisk = defaultDisk;
    }

    public Map<String, DiskConfig> getDisks() {
        return disks;
    }

    public void setDisks(Map<String, DiskConfig> disks) {
        this.disks = disks == null ? new LinkedHashMap<>() : disks;
    }

    /**
     * 单个磁盘的配置。
     * <p>
     * 驱动特定参数（local：root/url/visibility；database：binary/content-column/chunk-size/
     * table-prefix/connection）作为显式字段绑定，对齐 {@code vendor.springboot.cache.CacheProperties.StoreConfig}
     * 的模式，保证 {@code driver: database} 的磁盘参数不会被绑定静默丢弃；
     * 其余自定义参数放 {@link #options}。
     */
    public static class DiskConfig {

        /** 驱动名称，如 local */
        private String driver = "local";

        /** 根目录（local 驱动），相对运行目录或绝对路径 */
        private String root;

        /** 公开访问 URL 前缀，如 /storage */
        private String url;

        /** 默认可见性：public / private */
        private String visibility;

        // ---- database 磁盘驱动（storage-database 模块）特定参数 ----

        /** 是否以二进制（BLOB）存放；false 时 base64 文本（LONGTEXT），默认 true */
        private Boolean binary;

        /** 存放文件内容的列名，默认 content */
        private String contentColumn;

        /** 单条分片字节上限，默认 1MB；0/负=不切分 */
        private Long chunkSize;

        /** 数据表前缀，默认 storage_ */
        private String tablePrefix;

        /** 可选的 @RegisterConnection 连接别名（别名不存在时由驱动给出可操作提示） */
        private String connection;

        /** 驱动自定义配置，会与上述字段合并后传给驱动 */
        private Map<String, Object> options = new LinkedHashMap<>();

        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getVisibility() {
            return visibility;
        }

        public void setVisibility(String visibility) {
            this.visibility = visibility;
        }

        public Boolean getBinary() {
            return binary;
        }

        public void setBinary(Boolean binary) {
            this.binary = binary;
        }

        public String getContentColumn() {
            return contentColumn;
        }

        public void setContentColumn(String contentColumn) {
            this.contentColumn = contentColumn;
        }

        public Long getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(Long chunkSize) {
            this.chunkSize = chunkSize;
        }

        public String getTablePrefix() {
            return tablePrefix;
        }

        public void setTablePrefix(String tablePrefix) {
            this.tablePrefix = tablePrefix;
        }

        public String getConnection() {
            return connection;
        }

        public void setConnection(String connection) {
            this.connection = connection;
        }

        public Map<String, Object> getOptions() {
            return options;
        }

        public void setOptions(Map<String, Object> options) {
            this.options = options == null ? new LinkedHashMap<>() : options;
        }

        /**
         * 合并为驱动配置 Map：显式字段优先于 options 中的同名键。
         *
         * @return 驱动配置
         */
        public Map<String, Object> toConfig() {
            Map<String, Object> config = new LinkedHashMap<>(options);
            if (root != null && !root.isBlank()) {
                config.put("root", root);
            }
            if (url != null && !url.isBlank()) {
                config.put("url", url);
            }
            if (visibility != null && !visibility.isBlank()) {
                config.put("visibility", visibility);
            }
            if (binary != null) {
                config.put("binary", binary);
            }
            if (contentColumn != null && !contentColumn.isBlank()) {
                config.put("content-column", contentColumn);
            }
            if (chunkSize != null) {
                config.put("chunk-size", chunkSize);
            }
            if (tablePrefix != null && !tablePrefix.isBlank()) {
                config.put("table-prefix", tablePrefix);
            }
            if (connection != null && !connection.isBlank()) {
                config.put("connection", connection);
            }
            return config;
        }
    }
}
