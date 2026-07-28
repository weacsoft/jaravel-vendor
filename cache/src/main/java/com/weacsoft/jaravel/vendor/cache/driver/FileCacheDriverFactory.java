package com.weacsoft.jaravel.vendor.cache.driver;

import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.weacsoft.jaravel.vendor.cache.CacheDriverFactory;

import java.util.Map;

/**
 * 文件缓存驱动工厂，支持 {@code "file"} 驱动名。
 * <p>
 * 从配置中读取 {@code dir}（缓存目录），创建 {@link FileCacheDriver}。
 * 若 {@code dir} 为空则使用系统临时目录下的 {@code jaravel-cache} 子目录。
 */
public class FileCacheDriverFactory implements CacheDriverFactory {

    @Override
    public boolean support(String driver) {
        return "file".equalsIgnoreCase(driver);
    }

    @Override
    public CacheDriver create(Map<String, Object> config) {
        Object dir = config.get("dir");
        if (dir != null && !dir.toString().isEmpty()) {
            return new FileCacheDriver(dir.toString());
        }
        return new FileCacheDriver();
    }
}
