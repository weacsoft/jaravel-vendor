package com.weacsoft.jaravel.vendor.storage;

/**
 * 存储操作异常，对齐 Laravel {@code FilesystemException}。
 * <p>
 * 所有 {@link com.weacsoft.jaravel.vendor.storage.contract.Filesystem} 实现在遇到
 * 底层 IO 错误、路径越界、磁盘未注册等情况时抛出本异常（非受检），
 * 使业务代码无需在每处 {@code try/catch IOException}。
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>写入失败（磁盘满、权限不足）</li>
 *   <li>路径穿越（{@code ../} 逃逸出磁盘根目录）</li>
 *   <li>读取不存在的文件</li>
 *   <li>解析未注册的磁盘名或未知驱动</li>
 * </ul>
 */
public class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造"读取失败"异常。
     *
     * @param path  路径
     * @param cause 原始异常
     * @return 异常实例
     */
    public static StorageException readFailed(String path, Throwable cause) {
        return new StorageException("读取文件失败: " + path, cause);
    }

    /**
     * 构造"写入失败"异常。
     *
     * @param path  路径
     * @param cause 原始异常
     * @return 异常实例
     */
    public static StorageException writeFailed(String path, Throwable cause) {
        return new StorageException("写入文件失败: " + path, cause);
    }

    /**
     * 构造"路径非法（越界）"异常。
     *
     * @param path 路径
     * @return 异常实例
     */
    public static StorageException invalidPath(String path) {
        return new StorageException("非法路径（试图逃逸磁盘根目录）: " + path);
    }

    /**
     * 构造"文件不存在"异常。
     *
     * @param path 路径
     * @return 异常实例
     */
    public static StorageException notFound(String path) {
        return new StorageException("文件不存在: " + path);
    }
}
