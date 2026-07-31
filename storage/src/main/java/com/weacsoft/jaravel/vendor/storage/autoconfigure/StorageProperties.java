package com.weacsoft.jaravel.vendor.storage.autoconfigure;

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
            return config;
        }
    }
}
