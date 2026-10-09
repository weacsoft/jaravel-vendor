package com.weacsoft.jaravel.vendor.springboot.aetherupload;

import java.util.function.Supplier;

/**
 * 上传归属主体解析：从认证上下文取「当前登录用户 id」。
 * <p>
 * <b>为什么用反射而不是直接 import auth</b>：{@code auth} 是可选依赖（aether-upload 与
 * springboot 都不强依赖它），直接引用会让「未引入 auth」的应用在类加载/内省阶段就报错。
 * 这里只在<b>首次调用</b>时探测一次认证门面，探测失败即永久按匿名处理。
 * <p>
 * 归属为 {@code null}（匿名）时，框架禁用 identifier 续传 —— 因为 identifier 由前端按
 * 文件名/大小/mtime 可预测拼出，允许匿名续传等于「知道三要素即可劫持他人上传任务」。
 */
final class AetherUploadOwnerResolver {

    /** 认证门面类名（存在则说明引入了 auth） */
    private static final String AUTH_FACADE = "com.weacsoft.jaravel.vendor.auth.facade.Auth";

    private AetherUploadOwnerResolver() {
    }

    /**
     * 构造归属解析器：有认证上下文时返回其用户 id，否则返回 null（匿名）。
     *
     * @return 归属解析器（永不返回 null）
     */
    static Supplier<String> resolve() {
        Class<?> facade;
        try {
            facade = Class.forName(AUTH_FACADE);
        } catch (Throwable ignored) {
            return () -> null;   // 未引入 auth：一律匿名
        }
        return () -> {
            try {
                Object id = facade.getMethod("id").invoke(null);
                return id == null ? null : String.valueOf(id);
            } catch (Throwable t) {
                // 无认证上下文/解析异常：按匿名处理（宁可禁用续传，也不能把归属判错）
                return null;
            }
        };
    }
}