package com.weacsoft.jaravel.vendor.aetherupload.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AetherUpload 大文件上传配置，对齐 Laravel peinhu/AetherUpload 的 config/aetherupload.php。
 * <p>
 * 支持多组（group）配置，不同组可独立配置分片大小、类型/大小限制、base64 传输、
 * 记录头存储（内存或 Cache 模块 store，如 redis）、临时目录、保存目录、中间件等。
 * <pre>
 * jaravel:
 *   aether-upload:
 *     enabled: true
 *     route-prefix: aetherupload      # 上传端点路由前缀（可自定义）
 *     default-group: file
 *     middleware:                     # 应用到所有上传端点的中间件别名
 *       - throttle
 *     groups:
 *       file:
 *         chunk-size: 1048576         # 分片大小（字节，默认 1MB）
 *         max-size: 0                 # 文件大小上限（字节，0 = 不限制）
 *         allowed-extensions: []      # 允许的扩展名（空 = 不限制），如 [mp4, zip]
 *         allowed-mime-types: []      # 允许的 MIME（空 = 不限制），支持 video/* 通配
 *         base64: false               # 前端是否以 base64 分片传输（规避安全软件拦截二进制）
 *         header-store: memory        # 记录头存储：memory / cache（默认 store）/ 任意 cache store 名（如 redis）
 *         temp-dir: temp              # 分片临时目录（默认运行目录 temp 下）
 *         save-dir: uploads           # 上传完成后的保存目录（配置 disk 时为磁盘内相对子目录）
 *         disk:                       # 落盘磁盘名（需引入 storage 模块）；为空则直接写本地 save-dir
 *         header-ttl-seconds: 86400   # 未完成上传记录头的过期秒数
 *         allow-client-chunk-size: true # 是否允许前端在 prepare 时自定义分片大小
 *         middleware: []              # 该组专属中间件别名
 * </pre>
 */
@ConfigurationProperties(prefix = "jaravel.aether-upload")
public class AetherUploadProperties {

    /** 是否启用模块 */
    private boolean enabled = true;

    /** 上传端点路由前缀（可自定义端点） */
    private String routePrefix = "aetherupload";

    /** 默认组名 */
    private String defaultGroup = "file";

    /** 应用到所有上传端点的全局中间件别名 */
    private List<String> middleware = new ArrayList<>();

    /** 多组配置：组名 -> 组配置。未配置时自动创建 default-group 的默认组 */
    private Map<String, GroupConfig> groups = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRoutePrefix() {
        return routePrefix;
    }

    public void setRoutePrefix(String routePrefix) {
        this.routePrefix = routePrefix;
    }

    public String getDefaultGroup() {
        return defaultGroup;
    }

    public void setDefaultGroup(String defaultGroup) {
        this.defaultGroup = defaultGroup;
    }

    public List<String> getMiddleware() {
        return middleware;
    }

    public void setMiddleware(List<String> middleware) {
        this.middleware = middleware;
    }

    public Map<String, GroupConfig> getGroups() {
        return groups;
    }

    public void setGroups(Map<String, GroupConfig> groups) {
        this.groups = groups;
    }

    /**
     * 单个上传组的配置。
     */
    public static class GroupConfig {

        /** 分片大小（字节），默认 1MB */
        private long chunkSize = 1024 * 1024;

        /** 文件大小上限（字节），0 表示不限制（底层支持任意大小） */
        private long maxSize = 0;

        /** 允许的扩展名（小写、不带点），空表示不限制 */
        private List<String> allowedExtensions = new ArrayList<>();

        /** 允许的 MIME 类型，支持 {@code video/*} 通配，空表示不限制 */
        private List<String> allowedMimeTypes = new ArrayList<>();

        /** 是否以 base64 分片传输（前端读取该配置决定传输方式，后端两种均接受） */
        private boolean base64 = false;

        /**
         * 记录头存储：
         * <ul>
         *   <li>{@code memory}（默认）：进程内存记录文件 id / 分片位图</li>
         *   <li>{@code cache}：cache 模块默认 store</li>
         *   <li>其他值：cache 模块中对应名称的 store（如 {@code redis}，需引入 redis-cache 并配置）</li>
         * </ul>
         */
        private String headerStore = "memory";

        /** 分片临时目录，相对路径基于运行目录，默认 temp */
        private String tempDir = "temp";

        /** 上传完成文件的保存目录，默认 uploads */
        private String saveDir = "uploads";

        /**
         * 落盘使用的 storage 磁盘名（需引入 storage 模块）。
         * <p>
         * 为空时（默认）直接写本地文件系统的 {@code save-dir}，不依赖 storage 模块；
         * 配置后，上传完成的文件会通过 {@code Storage.disk(disk)} 落盘，
         * {@code save-dir} 则作为磁盘内的相对子目录，从而支持 S3/OSS 等任意驱动。
         */
        private String disk;

        /** 未完成上传记录头过期秒数（断点续传有效期），默认 24 小时 */
        private long headerTtlSeconds = 86400;

        /** 是否允许前端在 prepare 时指定分片大小 */
        private boolean allowClientChunkSize = true;

        /** 该组专属中间件别名列表 */
        private List<String> middleware = new ArrayList<>();

        public long getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(long chunkSize) {
            this.chunkSize = chunkSize;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }

        public List<String> getAllowedMimeTypes() {
            return allowedMimeTypes;
        }

        public void setAllowedMimeTypes(List<String> allowedMimeTypes) {
            this.allowedMimeTypes = allowedMimeTypes;
        }

        public boolean isBase64() {
            return base64;
        }

        public void setBase64(boolean base64) {
            this.base64 = base64;
        }

        public String getHeaderStore() {
            return headerStore;
        }

        public void setHeaderStore(String headerStore) {
            this.headerStore = headerStore;
        }

        public String getTempDir() {
            return tempDir;
        }

        public void setTempDir(String tempDir) {
            this.tempDir = tempDir;
        }

        public String getSaveDir() {
            return saveDir;
        }

        public void setSaveDir(String saveDir) {
            this.saveDir = saveDir;
        }

        public String getDisk() {
            return disk;
        }

        public void setDisk(String disk) {
            this.disk = disk;
        }

        public long getHeaderTtlSeconds() {
            return headerTtlSeconds;
        }

        public void setHeaderTtlSeconds(long headerTtlSeconds) {
            this.headerTtlSeconds = headerTtlSeconds;
        }

        public boolean isAllowClientChunkSize() {
            return allowClientChunkSize;
        }

        public void setAllowClientChunkSize(boolean allowClientChunkSize) {
            this.allowClientChunkSize = allowClientChunkSize;
        }

        public List<String> getMiddleware() {
            return middleware;
        }

        public void setMiddleware(List<String> middleware) {
            this.middleware = middleware;
        }
    }
}
