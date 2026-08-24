package com.weacsoft.jaravel.vendor.storage.contract;

import java.time.Instant;

/**
 * 文件/目录元信息，由 {@link Filesystem#info(String)} 与列举方法返回。
 *
 * @param path         相对磁盘根目录的路径（始终使用 {@code /} 分隔，不以 {@code /} 开头）
 * @param name         文件名（含扩展名）
 * @param directory    是否为目录
 * @param size         字节大小（目录为 0）
 * @param lastModified 最后修改时间
 * @param mimeType     MIME 类型（目录或无法探测时为 {@code null}）
 * @param visibility   可见性
 */
public record FileInfo(
        String path,
        String name,
        boolean directory,
        long size,
        Instant lastModified,
        String mimeType,
        Visibility visibility
) {

    /**
     * 扩展名（小写，不含点）。无扩展名时返回空串。
     *
     * @return 扩展名
     */
    public String extension() {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    /**
     * 是否为普通文件（非目录）。
     *
     * @return 是文件返回 true
     */
    public boolean file() {
        return !directory;
    }
}
