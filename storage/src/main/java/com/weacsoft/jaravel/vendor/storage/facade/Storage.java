package com.weacsoft.jaravel.vendor.storage.facade;

import com.weacsoft.jaravel.vendor.core.Facade;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import com.weacsoft.jaravel.vendor.storage.StorageManager;
import com.weacsoft.jaravel.vendor.storage.contract.FileInfo;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.Visibility;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Storage 门面，对齐 Laravel {@code Storage} Facade。
 * <p>
 * 提供静态方法访问文件存储，所有无 disk 参数的方法作用于<b>默认磁盘</b>。
 * 需要操作指定磁盘时使用 {@link #disk(String)} 获取 {@link Filesystem} 后链式调用。
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 默认磁盘
 * Storage.put("notes/todo.txt", "hello");
 * String text = Storage.get("notes/todo.txt");
 * boolean ok = Storage.exists("notes/todo.txt");
 * Storage.delete("notes/todo.txt");
 *
 * // 指定磁盘
 * Storage.disk("public").put("logo.png", bytes);
 * String url = Storage.disk("public").url("logo.png");
 *
 * // 大文件流式写入（内存占用恒定）
 * try (InputStream in = file.getInputStream()) {
 *     Storage.disk("uploads").putStream("videos/a.mp4", in);
 * }
 *
 * // 列举目录
 * for (FileInfo f : Storage.files("avatars")) {
 *     log.info("{} {} bytes", f.path(), f.size());
 * }
 * </pre>
 *
 * @see Filesystem
 * @see StorageManager
 */
public final class Storage {

    private Storage() {
    }

    /**
     * 获取存储管理器实例。
     *
     * @return StorageManager 实例
     */
    public static StorageManager manager() {
        return Facade.resolve(StorageManager.class);
    }

    /**
     * 获取指定名称的磁盘。
     *
     * @param name 磁盘名称
     * @return 磁盘实例
     */
    public static Filesystem disk(String name) {
        return manager().disk(name);
    }

    /**
     * 获取默认磁盘。
     *
     * @return 磁盘实例
     */
    public static Filesystem disk() {
        return manager().disk();
    }

    /**
     * 获取所有已注册的磁盘名称。
     *
     * @return 磁盘名称集合
     */
    public static Set<String> diskNames() {
        return manager().diskNames();
    }

    // ==================== 默认磁盘的便捷委托 ====================

    /**
     * 判断文件是否存在（默认磁盘）。
     *
     * @param path 相对路径
     * @return 存在返回 true
     */
    public static boolean exists(String path) {
        return disk().exists(path);
    }

    /**
     * 判断文件是否不存在（默认磁盘）。
     *
     * @param path 相对路径
     * @return 不存在返回 true
     */
    public static boolean missing(String path) {
        return disk().missing(path);
    }

    /**
     * 读取文件字节内容（默认磁盘）。
     *
     * @param path 相对路径
     * @return 字节内容
     */
    public static byte[] read(String path) {
        return disk().read(path);
    }

    /**
     * 读取文件文本内容（默认磁盘，UTF-8）。
     *
     * @param path 相对路径
     * @return 文本内容
     */
    public static String get(String path) {
        return disk().get(path);
    }

    /**
     * 以流方式读取文件（默认磁盘），调用方负责关闭。
     *
     * @param path 相对路径
     * @return 输入流
     */
    public static InputStream readStream(String path) {
        return disk().readStream(path);
    }

    /**
     * 写入字节内容（默认磁盘，覆盖）。
     *
     * @param path     相对路径
     * @param contents 字节内容
     */
    public static void put(String path, byte[] contents) {
        disk().put(path, contents);
    }

    /**
     * 写入文本内容（默认磁盘，UTF-8，覆盖）。
     *
     * @param path     相对路径
     * @param contents 文本内容
     */
    public static void put(String path, String contents) {
        disk().put(path, contents);
    }

    /**
     * 从输入流写入（默认磁盘，覆盖），不关闭传入的流。
     *
     * @param path  相对路径
     * @param input 输入流
     * @return 写入字节数
     */
    public static long putStream(String path, InputStream input) {
        return disk().putStream(path, input);
    }

    /**
     * 追加字节内容（默认磁盘）。
     *
     * @param path     相对路径
     * @param contents 字节内容
     */
    public static void append(String path, byte[] contents) {
        disk().append(path, contents);
    }

    /**
     * 追加文本内容（默认磁盘，UTF-8）。
     *
     * @param path     相对路径
     * @param contents 文本内容
     */
    public static void append(String path, String contents) {
        disk().append(path, contents);
    }

    /**
     * 将文件写出到输出流（默认磁盘），不关闭传入的流。
     *
     * @param path   相对路径
     * @param output 输出流
     * @return 写出字节数
     */
    public static long writeTo(String path, OutputStream output) {
        return disk().writeTo(path, output);
    }

    /**
     * 删除文件（默认磁盘）。
     *
     * @param path 相对路径
     * @return 实际删除返回 true
     */
    public static boolean delete(String path) {
        return disk().delete(path);
    }

    /**
     * 批量删除文件（默认磁盘）。
     *
     * @param paths 相对路径集合
     * @return 实际删除数量
     */
    public static int delete(List<String> paths) {
        return disk().delete(paths);
    }

    /**
     * 复制文件（默认磁盘）。
     *
     * @param from 源路径
     * @param to   目标路径
     */
    public static void copy(String from, String to) {
        disk().copy(from, to);
    }

    /**
     * 移动/重命名文件（默认磁盘）。
     *
     * @param from 源路径
     * @param to   目标路径
     */
    public static void move(String from, String to) {
        disk().move(from, to);
    }

    /**
     * 文件字节大小（默认磁盘）。
     *
     * @param path 相对路径
     * @return 字节数
     */
    public static long size(String path) {
        return disk().size(path);
    }

    /**
     * 最后修改时间（默认磁盘）。
     *
     * @param path 相对路径
     * @return 修改时间
     */
    public static Instant lastModified(String path) {
        return disk().lastModified(path);
    }

    /**
     * MIME 类型（默认磁盘）。
     *
     * @param path 相对路径
     * @return MIME 类型，无法探测时为 null
     */
    public static String mimeType(String path) {
        return disk().mimeType(path);
    }

    /**
     * 文件元信息（默认磁盘）。
     *
     * @param path 相对路径
     * @return 元信息
     */
    public static FileInfo info(String path) {
        return disk().info(path);
    }

    /**
     * 获取可见性（默认磁盘）。
     *
     * @param path 相对路径
     * @return 可见性
     */
    public static Visibility visibility(String path) {
        return disk().visibility(path);
    }

    /**
     * 设置可见性（默认磁盘）。
     *
     * @param path       相对路径
     * @param visibility 可见性
     */
    public static void setVisibility(String path, Visibility visibility) {
        disk().setVisibility(path, visibility);
    }

    /**
     * 列举目录下的文件（默认磁盘，不递归）。
     *
     * @param directory 目录路径
     * @return 文件列表
     */
    public static List<FileInfo> files(String directory) {
        return disk().files(directory);
    }

    /**
     * 递归列举目录下所有文件（默认磁盘）。
     *
     * @param directory 目录路径
     * @return 文件列表
     */
    public static List<FileInfo> allFiles(String directory) {
        return disk().allFiles(directory);
    }

    /**
     * 列举子目录（默认磁盘）。
     *
     * @param directory 目录路径
     * @return 目录列表
     */
    public static List<FileInfo> directories(String directory) {
        return disk().directories(directory);
    }

    /**
     * 创建目录（默认磁盘）。
     *
     * @param directory 目录路径
     */
    public static void makeDirectory(String directory) {
        disk().makeDirectory(directory);
    }

    /**
     * 递归删除目录（默认磁盘）。
     *
     * @param directory 目录路径
     * @return 实际删除返回 true
     */
    public static boolean deleteDirectory(String directory) {
        return disk().deleteDirectory(directory);
    }

    /**
     * 生成公开访问 URL（默认磁盘）。
     *
     * @param path 相对路径
     * @return URL
     */
    public static String url(String path) {
        return disk().url(path);
    }

    /**
     * 获取本地绝对路径（默认磁盘，仅本地驱动支持）。
     *
     * @param path 相对路径
     * @return 绝对路径
     */
    public static String path(String path) {
        return disk().path(path);
    }

    // ==================== HTTP 集成：下载 / 预览（Response） ====================

    /**
     * 以附件形式下载文件，返回可直接作为控制器返回值的 {@link Response}。
     *
     * @param disk 磁盘名称
     * @param path 相对路径
     * @return 下载响应（文件不存在时返回 404）
     */
    public static Response download(String disk, String path) {
        Filesystem fs = disk(disk);
        if (!fs.exists(path)) {
            return ResponseBuilder.error(404, "文件不存在: " + path);
        }
        return ResponseBuilder.file(fs.read(path), filenameOf(path));
    }

    /**
     * 以附件形式下载文件（默认磁盘）。
     *
     * @param path 相对路径
     * @return 下载响应
     */
    public static Response download(String path) {
        return download(manager().getDefaultDisk(), path);
    }

    /**
     * 以内联形式返回文件（按 MIME 预览，可用于图片/PDF 等），返回 {@link Response}。
     *
     * @param disk 磁盘名称
     * @param path 相对路径
     * @return 预览响应（文件不存在时返回 404）
     */
    public static Response response(String disk, String path) {
        Filesystem fs = disk(disk);
        if (!fs.exists(path)) {
            return ResponseBuilder.error(404, "文件不存在: " + path);
        }
        String mime = fs.mimeType(path);
        if (mime == null || mime.isEmpty()) {
            mime = "application/octet-stream";
        }
        return ResponseBuilder.staticFile(fs.read(path), mime, 3600);
    }

    /**
     * 以内联形式返回文件（默认磁盘）。
     *
     * @param path 相对路径
     * @return 预览响应
     */
    public static Response response(String path) {
        return response(manager().getDefaultDisk(), path);
    }

    // ==================== 指定磁盘的便捷委托 ====================

    /**
     * 列举目录下文件（指定磁盘，不递归）。
     *
     * @param disk      磁盘名称
     * @param directory 目录路径
     * @return 文件列表
     */
    public static List<FileInfo> files(String disk, String directory) {
        return disk(disk).files(directory);
    }

    /**
     * 递归列举目录下所有文件（指定磁盘）。
     *
     * @param disk      磁盘名称
     * @param directory 目录路径
     * @return 文件列表
     */
    public static List<FileInfo> allFiles(String disk, String directory) {
        return disk(disk).allFiles(directory);
    }

    /**
     * 删除文件（指定磁盘）。
     *
     * @param disk 磁盘名称
     * @param path 相对路径
     * @return 实际删除返回 true
     */
    public static boolean delete(String disk, String path) {
        return disk(disk).delete(path);
    }

    /**
     * 文件元信息（指定磁盘）。
     *
     * @param disk 磁盘名称
     * @param path 相对路径
     * @return 元信息
     */
    public static FileInfo info(String disk, String path) {
        return disk(disk).info(path);
    }

    /**
     * 获取可见性（指定磁盘）。
     *
     * @param disk 磁盘名称
     * @param path 相对路径
     * @return 可见性
     */
    public static Visibility visibility(String disk, String path) {
        return disk(disk).visibility(path);
    }

    private static String filenameOf(String path) {
        int idx = path.lastIndexOf('/');
        String name = idx < 0 ? path : path.substring(idx + 1);
        return name.isEmpty() ? "file" : name;
    }
}
