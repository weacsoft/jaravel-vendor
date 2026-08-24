package com.weacsoft.jaravel.vendor.storage.contract;

/**
 * 文件可见性，对齐 Laravel {@code Filesystem::VISIBILITY_PUBLIC / VISIBILITY_PRIVATE}。
 * <p>
 * 对于 {@code local} 驱动，可见性映射为 POSIX 文件权限（public 为 0644/0755，private 为 0600/0700）；
 * 在不支持 POSIX 的文件系统（如 Windows NTFS）上，可见性会被记录但不强制生效。
 * 对于对象存储驱动（如 S3、OSS），可见性映射为对象 ACL。
 */
public enum Visibility {

    /** 公开可读 */
    PUBLIC("public"),

    /** 仅所有者可读 */
    PRIVATE("private");

    private final String value;

    Visibility(String value) {
        this.value = value;
    }

    /**
     * 获取字符串值（{@code "public"} / {@code "private"}）。
     *
     * @return 字符串值
     */
    public String value() {
        return value;
    }

    /**
     * 从字符串解析可见性，不区分大小写，无法识别时返回 {@link #PRIVATE}。
     *
     * @param value 字符串值
     * @return 可见性枚举
     */
    public static Visibility from(String value) {
        if (value == null) {
            return PRIVATE;
        }
        return PUBLIC.value.equalsIgnoreCase(value.trim()) ? PUBLIC : PRIVATE;
    }
}
