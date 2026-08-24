package com.weacsoft.jaravel.vendor.aetherupload;

/**
 * 上传业务异常：类型不允许 / 超过大小限制 / 记录头不存在或过期 / 分片越界等。
 * <p>
 * 控制器层捕获后转换为 JSON 错误响应。
 */
public class UploadException extends RuntimeException {

    /** 业务错误码 */
    private final String code;

    public UploadException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 组不存在 */
    public static UploadException groupNotFound(String group) {
        return new UploadException("group_not_found", "上传组不存在: " + group);
    }

    /** 记录头不存在或已过期 */
    public static UploadException headerNotFound(String resourceId) {
        return new UploadException("header_not_found", "上传记录不存在或已过期: " + resourceId);
    }

    /** 文件类型不允许 */
    public static UploadException typeNotAllowed(String detail) {
        return new UploadException("type_not_allowed", "文件类型不允许: " + detail);
    }

    /** 超出大小限制 */
    public static UploadException sizeExceeded(long size, long max) {
        return new UploadException("size_exceeded", "文件大小 " + size + " 超过限制 " + max);
    }

    /** 参数非法 */
    public static UploadException invalid(String message) {
        return new UploadException("invalid", message);
    }

    /** IO 失败 */
    public static UploadException io(String message, Throwable cause) {
        UploadException e = new UploadException("io_error", message + ": " + cause.getMessage());
        e.initCause(cause);
        return e;
    }
}
