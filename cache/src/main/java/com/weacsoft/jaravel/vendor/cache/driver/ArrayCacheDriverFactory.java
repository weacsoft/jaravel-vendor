package com.weacsoft.jaravel.vendor.cache.driver;

import com.weacsoft.jaravel.vendor.cache.CacheDriver;
import com.weacsoft.jaravel.vendor.cache.CacheDriverFactory;

import java.util.Map;

/**
 * 内存缓存驱动工厂，支持 {@code "array"} 驱动名。
 * <p>
 * 由 {@code CacheAutoConfiguration} 注册为 Bean，{@code CacheManager} 在创建 array store 时
 * 通过 {@link #support(String)} 匹配并调用 {@link #create} 创建 {@link ArrayCacheDriver}。
 */
public class ArrayCacheDriverFactory implements CacheDriverFactory {

    @Override
    public boolean support(String driver) {
        return "array".equalsIgnoreCase(driver);
    }

    @Override
    public CacheDriver create(Map<String, Object> config) {
        return new ArrayCacheDriver();
    }
}
