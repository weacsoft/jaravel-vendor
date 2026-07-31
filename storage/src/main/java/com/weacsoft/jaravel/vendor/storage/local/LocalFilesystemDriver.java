package com.weacsoft.jaravel.vendor.storage.local;

import com.weacsoft.jaravel.vendor.storage.contract.DiskDefinition;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.FilesystemDriver;
import com.weacsoft.jaravel.vendor.storage.contract.Visibility;

import java.util.Map;

/**
 * 本地文件系统驱动（工厂），对齐 Laravel {@code local} 驱动。
 * <p>
 * 支持的 driver 名称：{@code local}、{@code public}（两者行为一致，
 * {@code public} 只是语义上的别名，通常额外配置 {@code url} 与 {@code visibility: public}）。
 *
 * <h3>支持的配置键</h3>
 * <ul>
 *   <li>{@code root} — 根目录，相对运行目录或绝对路径，默认 {@code storage/app}</li>
 *   <li>{@code url} — 公开访问 URL 前缀，如 {@code /storage}，不配置则 {@code url()} 抛异常</li>
 *   <li>{@code visibility} — 默认可见性 {@code public}/{@code private}，默认 {@code private}</li>
 * </ul>
 *
 * 本驱动由 {@code StorageAutoConfiguration} 自动注册为 Bean，
 * 并被 {@code StorageManager} 自动收集，业务方无需手动注册。
 */
public class LocalFilesystemDriver implements FilesystemDriver {

    /** 未配置 root 时的默认根目录，对齐 Laravel {@code storage/app} */
    public static final String DEFAULT_ROOT = "storage/app";

    @Override
    public boolean support(String driver) {
        return "local".equalsIgnoreCase(driver) || "public".equalsIgnoreCase(driver);
    }

    @Override
    public Filesystem create(String name, Map<String, Object> config) {
        Map<String, Object> cfg = config == null ? Map.of() : config;
        String root = string(cfg.get(DiskDefinition.ROOT), DEFAULT_ROOT);
        String url = string(cfg.get(DiskDefinition.URL), null);
        Visibility visibility = Visibility.from(string(cfg.get(DiskDefinition.VISIBILITY), null));
        return new LocalFilesystem(name, root, url, visibility);
    }

    private static String string(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }
}
