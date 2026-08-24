package com.weacsoft.jaravel.vendor.storage.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 根据文件扩展名猜测 MIME 类型的轻量工具。
 * <p>
 * 数据库驱动没有本地文件可让 {@code Files.probeContentType} 探测，
 * 因此用一份常用扩展名映射表做预测。覆盖常见文档、图片、音视频与压缩包。
 */
public final class MimeTypeGuesser {

    private static final Map<String, String> TYPES = new HashMap<>();

    static {
        // 文本 / 文档
        TYPES.put("txt", "text/plain");
        TYPES.put("html", "text/html");
        TYPES.put("htm", "text/html");
        TYPES.put("css", "text/css");
        TYPES.put("csv", "text/csv");
        TYPES.put("md", "text/markdown");
        TYPES.put("json", "application/json");
        TYPES.put("xml", "application/xml");
        TYPES.put("yaml", "application/x-yaml");
        TYPES.put("yml", "application/x-yaml");
        TYPES.put("pdf", "application/pdf");
        TYPES.put("rtf", "application/rtf");

        // 图片
        TYPES.put("png", "image/png");
        TYPES.put("jpg", "image/jpeg");
        TYPES.put("jpeg", "image/jpeg");
        TYPES.put("gif", "image/gif");
        TYPES.put("bmp", "image/bmp");
        TYPES.put("webp", "image/webp");
        TYPES.put("svg", "image/svg+xml");
        TYPES.put("ico", "image/x-icon");

        // 音视频
        TYPES.put("mp3", "audio/mpeg");
        TYPES.put("wav", "audio/wav");
        TYPES.put("ogg", "audio/ogg");
        TYPES.put("mp4", "video/mp4");
        TYPES.put("webm", "video/webm");
        TYPES.put("avi", "video/x-msvideo");
        TYPES.put("mov", "video/quicktime");

        // 压缩 / 归档
        TYPES.put("zip", "application/zip");
        TYPES.put("tar", "application/x-tar");
        TYPES.put("gz", "application/gzip");
        TYPES.put("7z", "application/x-7z-compressed");
        TYPES.put("rar", "application/vnd.rar");

        // 其它常见二进制
        TYPES.put("bin", "application/octet-stream");
        TYPES.put("exe", "application/octet-stream");
        TYPES.put("doc", "application/msword");
        TYPES.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        TYPES.put("xls", "application/vnd.ms-excel");
        TYPES.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        TYPES.put("ppt", "application/vnd.ms-powerpoint");
        TYPES.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    private MimeTypeGuesser() {
    }

    /**
     * 根据路径（或文件名）猜测 MIME 类型，无法猜测时返回 {@code application/octet-stream}。
     *
     * @param path 文件相对路径或文件名
     * @return MIME 类型
     */
    public static String guess(String path) {
        if (path == null || path.isEmpty()) {
            return "application/octet-stream";
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "application/octet-stream";
        }
        String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return TYPES.getOrDefault(ext, "application/octet-stream");
    }
}
