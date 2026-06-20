package com.weacsoft.jaravel.vendor.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 缓存配置属性，前缀 {@code jaravel.cache}，对齐 Laravel {@code config/cache.php}。
 * <pre>
 * jaravel:
 *   cache:
 *     default-store: array      # 默认 store 名称：array / file
 *     prefix: jaravel           # 缓存键前缀
 *     file-dir: /tmp/jaravel    # file 驱动目录，空则使用系统临时目录
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.cache")
public class CacheProperties {

    /** 默认 store 名称 */
    private String defaultStore = "array";

    /** 缓存键前缀 */
    private String prefix = "jaravel";

    /** file 驱动目录，空串表示使用系统临时目录下的 jaravel-cache 子目录 */
    private String fileDir = "";

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
}
