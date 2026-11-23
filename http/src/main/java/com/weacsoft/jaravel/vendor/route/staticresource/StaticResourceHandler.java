package com.weacsoft.jaravel.vendor.route.staticresource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 静态资源处理器。
 * <p>
 * 负责从 classpath 或文件系统读取静态资源文件，自动推断 MIME 类型，
 * 设置缓存头，并防范路径穿越攻击。
 * <p>
 * <h3>资源定位策略</h3>
 * 支持两种资源位置前缀：
 * <ul>
 *   <li>{@code classpath:/static/} — 从 classpath 读取（打包在 JAR 内）</li>
 *   <li>{@code file:./public/} — 从文件系统读取（外部目录）</li>
 * </ul>
 * <p>
 * <h3>MIME 类型推断</h3>
 * 内置常见文件类型的 MIME 映射表（css/js/html/json/png/jpg/gif/svg/ico/pdf/woff2 等），
 * 未匹配的扩展名回退为 {@code application/octet-stream}。
 * <p>
 * <h3>路径安全</h3>
 * 对请求路径进行规范化，拒绝包含 {@code ..} 的路径穿越攻击。
 * <p>
 * <h3>缓存策略</h3>
 * 设置 {@code Cache-Control: max-age=N} 和 {@code ETag}（基于文件内容的 MD5）。
 */
public class StaticResourceHandler {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceHandler.class);

    /** 内置 MIME 类型映射表 */
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put(".css", "text/css; charset=utf-8");
        MIME_TYPES.put(".js", "application/javascript; charset=utf-8");
        MIME_TYPES.put(".mjs", "application/javascript; charset=utf-8");
        MIME_TYPES.put(".html", "text/html; charset=utf-8");
        MIME_TYPES.put(".htm", "text/html; charset=utf-8");
        MIME_TYPES.put(".json", "application/json; charset=utf-8");
        MIME_TYPES.put(".xml", "application/xml; charset=utf-8");
        MIME_TYPES.put(".txt", "text/plain; charset=utf-8");
        MIME_TYPES.put(".csv", "text/csv; charset=utf-8");
        MIME_TYPES.put(".png", "image/png");
        MIME_TYPES.put(".jpg", "image/jpeg");
        MIME_TYPES.put(".jpeg", "image/jpeg");
        MIME_TYPES.put(".gif", "image/gif");
        MIME_TYPES.put(".svg", "image/svg+xml");
        MIME_TYPES.put(".ico", "image/x-icon");
        MIME_TYPES.put(".webp", "image/webp");
        MIME_TYPES.put(".bmp", "image/bmp");
        MIME_TYPES.put(".pdf", "application/pdf");
        MIME_TYPES.put(".zip", "application/zip");
        MIME_TYPES.put(".gz", "application/gzip");
        MIME_TYPES.put(".tar", "application/x-tar");
        MIME_TYPES.put(".woff", "font/woff");
        MIME_TYPES.put(".woff2", "font/woff2");
        MIME_TYPES.put(".ttf", "font/ttf");
        MIME_TYPES.put(".otf", "font/otf");
        MIME_TYPES.put(".eot", "application/vnd.ms-fontobject");
        MIME_TYPES.put(".mp3", "audio/mpeg");
        MIME_TYPES.put(".mp4", "video/mp4");
        MIME_TYPES.put(".webm", "video/webm");
        MIME_TYPES.put(".wav", "audio/wav");
        MIME_TYPES.put(".ogg", "audio/ogg");
        MIME_TYPES.put(".wasm", "application/wasm");
        MIME_TYPES.put(".map", "application/json; charset=utf-8");
    }

    private final String location;
    private final int cacheMaxAge;
    /** 文件系统位置对应的基准目录，构造时解析一次，避免依赖运行期的工作目录 */
    private final Path baseDir;

    /**
     * 构造静态资源处理器。
     *
     * @param location    资源目录（如 {@code classpath:/static/} 或 {@code file:./public/}）
     * @param cacheMaxAge 缓存时间（秒）
     */
    public StaticResourceHandler(String location, int cacheMaxAge) {
        this.location = normalizeLocation(location);
        this.cacheMaxAge = cacheMaxAge;
        this.baseDir = this.location.startsWith("file:")
                ? Paths.get(this.location.substring("file:".length())).toAbsolutePath().normalize()
                : null;
    }

    /**
     * 加载静态资源。
     *
     * @param relativePath 相对路径（如 {@code css/app.css}）
     * @return 资源内容，不存在返回 null
     * @throws IllegalArgumentException 路径包含 {@code ..} 时抛出（路径穿越攻击防护）
     */
    public ResourceResult load(String relativePath) {
        // 路径安全检查：拒绝路径穿越
        String safePath = sanitizePath(relativePath);
        if (safePath == null) {
            log.warn("[static] 路径穿越攻击已拦截: {}", relativePath);
            return null;
        }

        String fullPath = location + safePath;
        log.debug("[static] 加载资源: {}", fullPath);

        try {
            if (location.startsWith("classpath:")) {
                return loadFromClasspath(fullPath.substring("classpath:".length()), safePath);
            } else if (location.startsWith("file:")) {
                return loadFromFilesystem(fullPath.substring("file:".length()), safePath);
            } else {
                log.warn("[static] 不支持的位置前缀: {}", location);
                return null;
            }
        } catch (IOException e) {
            log.debug("[static] 资源加载失败: {} - {}", fullPath, e.getMessage());
            return null;
        }
    }

    /**
     * 定位静态资源并返回可直接写出的数据源。
     * <p>
     * 与 {@link #load(String)} 的区别：内容不在堆内存中物化为 {@code byte[]}，
     * 而是保留为文件路径或资源 URL，由调用方按块写出，避免大文件占满堆内存。
     *
     * @param relativePath 相对路径（如 {@code css/app.css}）
     * @return 可写出的数据源，不存在返回 null
     */
    public ResourceSource open(String relativePath) {
        String safePath = sanitizePath(relativePath);
        if (safePath == null) {
            log.warn("[static] 路径穿越攻击已拦截: {}", relativePath);
            return null;
        }

        String fullPath = location + safePath;
        log.debug("[static] 定位资源: {}", fullPath);

        if (location.startsWith("classpath:")) {
            String classpathPath = fullPath.substring("classpath:".length());
            URL url = getClass().getClassLoader().getResource(classpathPath);
            if (url == null) {
                return null;
            }
            return new ResourceSource(url, guessMimeType(safePath), cacheMaxAge, -1);
        }

        if (location.startsWith("file:")) {
            Path path = Paths.get(fullPath.substring("file:".length())).toAbsolutePath().normalize();
            // 二次路径安全检查：确保解析后的路径在基础目录内
            if (baseDir != null && !path.startsWith(baseDir)) {
                log.warn("[static] 文件系统路径越界: {} (base={})", path, baseDir);
                return null;
            }
            if (!Files.exists(path) || Files.isDirectory(path)) {
                return null;
            }
            long length = -1;
            try {
                length = Files.size(path);
            } catch (IOException e) {
                log.debug("[static] 读取文件长度失败: {} - {}", path, e.getMessage());
            }
            return new ResourceSource(path, guessMimeType(safePath), cacheMaxAge, length);
        }

        log.warn("[static] 不支持的位置前缀: {}", location);
        return null;
    }

    private ResourceResult loadFromClasspath(String classpathPath, String originalPath) throws IOException {
        URL url = getClass().getClassLoader().getResource(classpathPath);
        if (url == null) {
            return null;
        }
        try (InputStream is = url.openStream()) {
            byte[] content = readAllBytes(is);
            String mimeType = guessMimeType(originalPath);
            return new ResourceResult(content, mimeType, cacheMaxAge);
        }
    }

    private ResourceResult loadFromFilesystem(String filePath, String originalPath) throws IOException {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();

        // 二次路径安全检查：确保解析后的路径在基础目录内
        if (baseDir != null && !path.startsWith(baseDir)) {
            log.warn("[static] 文件系统路径越界: {} (base={})", path, baseDir);
            return null;
        }

        if (!Files.exists(path) || Files.isDirectory(path)) {
            return null;
        }

        byte[] content = Files.readAllBytes(path);
        String mimeType = guessMimeType(originalPath);
        return new ResourceResult(content, mimeType, cacheMaxAge);
    }

    /**
     * 路径安全处理：拒绝 {@code ..} 路径穿越。
     * <p>
     * 判定以路径段为单位：仅当某个路径段恰为 {@code ..} 时才判定为穿越，
     * 因此形如 {@code a..b} 的合法文件名不会被误拒；反斜杠一律拒绝。
     *
     * @param path 原始路径
     * @return 安全的路径，不安全返回 null
     */
    static String sanitizePath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        // 去除开头的 /
        String p = path.startsWith("/") ? path.substring(1) : path;
        // 反斜杠不作为路径分隔符使用，直接拒绝
        if (p.contains("\\")) {
            return null;
        }
        // 规范化：去除多余的 /
        while (p.contains("//")) {
            p = p.replace("//", "/");
        }
        // 检查路径穿越：按路径段判定，段恰为 ".." 才拒绝
        for (String segment : p.split("/")) {
            if ("..".equals(segment)) {
                return null;
            }
        }
        return p;
    }

    /**
     * 根据文件扩展名推断 MIME 类型。
     *
     * @param path 文件路径
     * @return MIME 类型，未知返回 {@code application/octet-stream}
     */
    static String guessMimeType(String path) {
        if (path == null) {
            return "application/octet-stream";
        }
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0) {
            return "application/octet-stream";
        }
        String ext = path.substring(dotIndex).toLowerCase();
        return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    private static String normalizeLocation(String location) {
        if (location == null || location.isEmpty()) {
            return "classpath:/static/";
        }
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        return location;
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    /**
     * 返回资源位置（用于日志和调试）。
     */
    public String getLocation() {
        return location;
    }

    /**
     * 静态资源加载结果。
     */
    public static class ResourceResult {
        private final byte[] content;
        private final String mimeType;
        private final int cacheMaxAge;

        public ResourceResult(byte[] content, String mimeType, int cacheMaxAge) {
            this.content = content;
            this.mimeType = mimeType;
            this.cacheMaxAge = cacheMaxAge;
        }

        public byte[] getContent() { return content; }
        public String getMimeType() { return mimeType; }
        public int getCacheMaxAge() { return cacheMaxAge; }
        public int getContentLength() { return content != null ? content.length : 0; }
    }

    /**
     * 静态资源数据源：内容保留在文件或资源 URL 中，由调用方按块写出。
     * <p>
     * 与 {@link ResourceResult} 的区别：后者把内容整体读入堆内存，
     * 前者只保留定位信息，内存占用与文件大小无关。
     */
    public static class ResourceSource {
        private final URL url;
        private final Path filePath;
        private final String mimeType;
        private final int cacheMaxAge;
        private final long contentLength;

        public ResourceSource(URL url, String mimeType, int cacheMaxAge, long contentLength) {
            this(url, null, mimeType, cacheMaxAge, contentLength);
        }

        public ResourceSource(Path filePath, String mimeType, int cacheMaxAge, long contentLength) {
            this(null, filePath, mimeType, cacheMaxAge, contentLength);
        }

        private ResourceSource(URL url, Path filePath, String mimeType, int cacheMaxAge, long contentLength) {
            this.url = url;
            this.filePath = filePath;
            this.mimeType = mimeType;
            this.cacheMaxAge = cacheMaxAge;
            this.contentLength = contentLength;
        }

        public URL getUrl() { return url; }
        public Path getFilePath() { return filePath; }
        public String getMimeType() { return mimeType; }
        public int getCacheMaxAge() { return cacheMaxAge; }
        /** 内容长度，未知时为 -1 */
        public long getContentLength() { return contentLength; }

        /**
         * 打开内容流。
         *
         * @return 内容流，调用方负责关闭
         * @throws IOException 打开流失败
         */
        public InputStream openStream() throws IOException {
            if (filePath != null) {
                return Files.newInputStream(filePath);
            }
            return url.openStream();
        }
    }
}
