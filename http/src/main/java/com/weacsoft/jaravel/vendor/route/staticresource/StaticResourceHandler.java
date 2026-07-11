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

    /**
     * 构造静态资源处理器。
     *
     * @param location    资源目录（如 {@code classpath:/static/} 或 {@code file:./public/}）
     * @param cacheMaxAge 缓存时间（秒）
     */
    public StaticResourceHandler(String location, int cacheMaxAge) {
        this.location = normalizeLocation(location);
        this.cacheMaxAge = cacheMaxAge;
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
        Path baseDir = Paths.get(location.substring("file:".length())).toAbsolutePath().normalize();

        // 二次路径安全检查：确保解析后的路径在基础目录内
        if (!path.startsWith(baseDir)) {
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
        // 检查路径穿越
        if (p.contains("..") || p.contains("\\")) {
            return null;
        }
        // 规范化：去除多余的 /
        while (p.contains("//")) {
            p = p.replace("//", "/");
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
}
