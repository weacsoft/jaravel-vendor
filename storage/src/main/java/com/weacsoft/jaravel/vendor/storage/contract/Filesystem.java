package com.weacsoft.jaravel.vendor.storage.contract;

import com.weacsoft.jaravel.vendor.storage.StorageException;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * 文件系统契约，对齐 Laravel {@code Illuminate\Contracts\Filesystem\Filesystem}。
 * <p>
 * 一个 {@code Filesystem} 实例代表一个「磁盘」（disk），由
 * {@link com.weacsoft.jaravel.vendor.storage.StorageManager} 按名称解析。
 * 所有路径均为<b>相对磁盘根目录</b>的路径，使用 {@code /} 作为分隔符，
 * 实现类必须防止 {@code ../} 路径穿越（越界时抛
 * {@link StorageException}）。
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 默认磁盘
 * Storage.put("avatars/1.png", bytes);
 * String text = Storage.get("notes/todo.txt");
 *
 * // 指定磁盘
 * Storage.disk("public").put("logo.png", bytes);
 * String url = Storage.disk("public").url("logo.png");
 *
 * // 流式写入（大文件，不占内存）
 * try (InputStream in = request.file("video").getInputStream()) {
 *     Storage.disk("uploads").putStream("videos/a.mp4", in);
 * }
 * </pre>
 *
 * <h3>实现约定</h3>
 * <ul>
 *   <li>写入方法（{@code put*}）在父目录不存在时<b>自动创建</b>父目录</li>
 *   <li>所有 IO 异常统一包装为 {@link StorageException}（非受检）</li>
 *   <li>返回的路径一律规范化为 {@code /} 分隔且不以 {@code /} 开头</li>
 *   <li>实现必须是线程安全的（磁盘实例进程级共享）</li>
 * </ul>
 *
 * @see com.weacsoft.jaravel.vendor.storage.facade.Storage
 * @see FilesystemDriver
 */
public interface Filesystem {

    // ==================== 读取 ====================

    /**
     * 判断文件或目录是否存在。
     *
     * @param path 相对路径
     * @return 存在返回 true
     */
    boolean exists(String path);

    /**
     * 判断文件或目录是否不存在。
     *
     * @param path 相对路径
     * @return 不存在返回 true
     */
    default boolean missing(String path) {
        return !exists(path);
    }

    /**
     * 读取文件全部内容为字节数组。
     * <p>
     * <b>注意</b>：大文件请改用 {@link #readStream(String)} 以免占用过多内存。
     *
     * @param path 相对路径
     * @return 文件字节内容
     * @throws StorageException 文件不存在或读取失败
     */
    byte[] read(String path);

    /**
     * 读取文件全部内容为 UTF-8 字符串。
     *
     * @param path 相对路径
     * @return 文件文本内容
     * @throws StorageException 文件不存在或读取失败
     */
    default String get(String path) {
        return new String(read(path), StandardCharsets.UTF_8);
    }

    /**
     * 以流方式读取文件，调用方负责关闭流。
     * <p>
     * 适用于大文件下载/转发，内存占用恒定。
     *
     * @param path 相对路径
     * @return 输入流
     * @throws StorageException 文件不存在或打开失败
     */
    InputStream readStream(String path);

    // ==================== 写入 ====================

    /**
     * 写入字节内容（覆盖已有文件），自动创建父目录。
     *
     * @param path     相对路径
     * @param contents 字节内容
     * @throws StorageException 写入失败
     */
    void put(String path, byte[] contents);

    /**
     * 写入文本内容（UTF-8，覆盖已有文件），自动创建父目录。
     *
     * @param path     相对路径
     * @param contents 文本内容
     * @throws StorageException 写入失败
     */
    default void put(String path, String contents) {
        put(path, contents.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从输入流写入（覆盖已有文件），自动创建父目录。
     * <p>
     * 实现应流式拷贝，不得将整个流读入内存，以支持任意大小的文件。
     * 本方法<b>不会</b>关闭传入的流，由调用方负责。
     *
     * @param path  相对路径
     * @param input 输入流
     * @return 实际写入的字节数
     * @throws StorageException 写入失败
     */
    long putStream(String path, InputStream input);

    /**
     * 追加内容到文件末尾，文件不存在时创建。
     *
     * @param path     相对路径
     * @param contents 追加的字节内容
     * @throws StorageException 写入失败
     */
    void append(String path, byte[] contents);

    /**
     * 追加文本到文件末尾（UTF-8）。
     *
     * @param path     相对路径
     * @param contents 追加的文本
     * @throws StorageException 写入失败
     */
    default void append(String path, String contents) {
        append(path, contents.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将文件内容写出到指定输出流（流式，适合大文件下载）。
     * <p>
     * 本方法<b>不会</b>关闭传入的输出流。
     *
     * @param path   相对路径
     * @param output 输出流
     * @return 写出的字节数
     * @throws StorageException 文件不存在或读取失败
     */
    long writeTo(String path, OutputStream output);

    // ==================== 删除 / 移动 / 复制 ====================

    /**
     * 删除文件，不存在时静默返回 false。
     *
     * @param path 相对路径
     * @return 实际删除返回 true
     * @throws StorageException 删除失败
     */
    boolean delete(String path);

    /**
     * 批量删除文件。
     *
     * @param paths 相对路径集合
     * @return 实际删除的文件数
     */
    default int delete(List<String> paths) {
        int count = 0;
        for (String path : paths) {
            if (delete(path)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 复制文件（目标已存在时覆盖），自动创建目标父目录。
     *
     * @param from 源相对路径
     * @param to   目标相对路径
     * @throws StorageException 源不存在或复制失败
     */
    void copy(String from, String to);

    /**
     * 移动/重命名文件（目标已存在时覆盖），自动创建目标父目录。
     *
     * @param from 源相对路径
     * @param to   目标相对路径
     * @throws StorageException 源不存在或移动失败
     */
    void move(String from, String to);

    // ==================== 元信息 ====================

    /**
     * 文件字节大小。
     *
     * @param path 相对路径
     * @return 字节数
     * @throws StorageException 文件不存在
     */
    long size(String path);

    /**
     * 最后修改时间。
     *
     * @param path 相对路径
     * @return 修改时间
     * @throws StorageException 文件不存在
     */
    Instant lastModified(String path);

    /**
     * 探测 MIME 类型，无法探测时返回 {@code null}。
     *
     * @param path 相对路径
     * @return MIME 类型
     */
    String mimeType(String path);

    /**
     * 获取文件/目录完整元信息。
     *
     * @param path 相对路径
     * @return 元信息
     * @throws StorageException 路径不存在
     */
    FileInfo info(String path);

    /**
     * 获取文件可见性。
     *
     * @param path 相对路径
     * @return 可见性
     */
    Visibility visibility(String path);

    /**
     * 设置文件可见性。
     *
     * @param path       相对路径
     * @param visibility 可见性
     * @throws StorageException 设置失败
     */
    void setVisibility(String path, Visibility visibility);

    // ==================== 目录 ====================

    /**
     * 列举目录下的文件（不含子目录中的文件）。
     *
     * @param directory 目录相对路径，{@code null} 或空串表示根目录
     * @return 文件元信息列表（不含目录项）
     */
    List<FileInfo> files(String directory);

    /**
     * 递归列举目录下所有文件（含所有子目录中的文件）。
     *
     * @param directory 目录相对路径，{@code null} 或空串表示根目录
     * @return 文件元信息列表（不含目录项）
     */
    List<FileInfo> allFiles(String directory);

    /**
     * 列举目录下的直接子目录。
     *
     * @param directory 目录相对路径，{@code null} 或空串表示根目录
     * @return 目录元信息列表
     */
    List<FileInfo> directories(String directory);

    /**
     * 创建目录（含所有必需的父目录），已存在时静默返回。
     *
     * @param directory 目录相对路径
     * @throws StorageException 创建失败
     */
    void makeDirectory(String directory);

    /**
     * 递归删除目录及其全部内容。
     *
     * @param directory 目录相对路径
     * @return 实际删除返回 true
     * @throws StorageException 删除失败
     */
    boolean deleteDirectory(String directory);

    // ==================== URL / 本地路径 ====================

    /**
     * 生成文件的公开访问 URL。
     * <p>
     * 需要磁盘配置了 {@code url} 前缀；未配置时抛 {@link StorageException}。
     *
     * @param path 相对路径
     * @return 完整 URL
     * @throws StorageException 磁盘不支持生成 URL
     */
    String url(String path);

    /**
     * 获取文件在本地文件系统中的绝对路径。
     * <p>
     * 仅本地驱动支持；对象存储等远程驱动应抛 {@link StorageException}。
     * 大文件上传模块需要随机写入时会用到本方法。
     *
     * @param path 相对路径
     * @return 绝对路径
     * @throws StorageException 驱动不支持本地路径
     */
    String path(String path);

    /**
     * 是否支持 {@link #path(String)}（即是否为本地文件系统驱动）。
     *
     * @return 支持返回 true
     */
    default boolean supportsLocalPath() {
        return false;
    }

    /**
     * 磁盘名称（由 {@link com.weacsoft.jaravel.vendor.storage.StorageManager} 注册时指定）。
     *
     * @return 磁盘名
     */
    String name();
}
