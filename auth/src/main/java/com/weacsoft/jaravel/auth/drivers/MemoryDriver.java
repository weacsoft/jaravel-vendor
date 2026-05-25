package com.weacsoft.jaravel.auth.drivers;

import com.weacsoft.jaravel.contract.auth.AuthDriver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存认证驱动实现。
 *
 * <p>将用户认证标识存储在内存中，应用重启后数据丢失。
 * 通过 ThreadLocal 存储请求上下文，自动区分不同请求。</p>
 */
public class MemoryDriver implements AuthDriver {

    private final ThreadLocal<Map<String, String>> threadLocal = new ThreadLocal<>();

    //给每个内存驱动设置一个名字
    private final String name;

    public MemoryDriver(String name) {
        this.name = name;
    }

    @Override
    public void init() {
        threadLocal.set(new ConcurrentHashMap<>());
    }

    public Map<String, String> getLocalMemory() {
        if (threadLocal.get() == null) {
            threadLocal.set(new ConcurrentHashMap<>());
        }
        return threadLocal.get();
    }

    @Override
    public void destroy() {
        threadLocal.remove();
    }

    @Override
    public void setId(String id) {
        getLocalMemory().put(name, id);
    }

    @Override
    public String getId() {
        return getLocalMemory().get(name);
    }

    @Override
    public void removeId() {
        getLocalMemory().remove(name);
    }
}
