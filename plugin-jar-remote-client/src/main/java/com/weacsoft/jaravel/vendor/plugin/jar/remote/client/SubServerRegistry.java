package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子服务器注册表。
 * <p>
 * 管理所有已注册的远程子运算服务器。提供注册、注销、查询等方法。
 * <p>
 * <b>安全设计</b>：本类仅提供 Java 方法，不暴露 HTTP 接口。
 * 调用方（如管理后台）通过方法调用操作子服务器列表，
 * 可自行决定是否包装为 HTTP 接口（不推荐预先暴露）。
 * <p>
 * 线程安全：使用 {@link ConcurrentHashMap}。
 */
public class SubServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(SubServerRegistry.class);

    private final Map<String, SubServerInfo> servers = new ConcurrentHashMap<>();

    /**
     * 注册子服务器。
     * <p>
     * 若 ID 已存在，更新连接信息。
     *
     * @param id        子服务器唯一标识
     * @param host      主机地址
     * @param port      TCP 端口
     * @param authToken 认证令牌（可选，null 表示不认证）
     * @return 注册的子服务器信息
     */
    public SubServerInfo registerSubServer(String id, String host, int port, String authToken) {
        SubServerInfo info = new SubServerInfo(id, host, port, authToken);
        servers.put(id, info);
        log.info("注册子服务器: id={}, host={}, port={}", id, host, port);
        return info;
    }

    /**
     * 注册子服务器（无认证）。
     *
     * @param id   子服务器唯一标识
     * @param host 主机地址
     * @param port TCP 端口
     * @return 注册的子服务器信息
     */
    public SubServerInfo registerSubServer(String id, String host, int port) {
        return registerSubServer(id, host, port, null);
    }

    /**
     * 注销子服务器。
     *
     * @param id 子服务器唯一标识
     * @return 注销成功返回 true，不存在返回 false
     */
    public boolean unregisterSubServer(String id) {
        SubServerInfo removed = servers.remove(id);
        if (removed != null) {
            log.info("注销子服务器: id={}", id);
            return true;
        }
        return false;
    }

    /**
     * 获取子服务器信息。
     *
     * @param id 子服务器唯一标识
     * @return 子服务器信息，不存在返回 null
     */
    public SubServerInfo getSubServer(String id) {
        return servers.get(id);
    }

    /**
     * 获取所有已注册的子服务器。
     *
     * @return 子服务器列表
     */
    public List<SubServerInfo> getSubServers() {
        return new ArrayList<>(servers.values());
    }

    /**
     * 获取所有在线的子服务器。
     *
     * @return 在线子服务器列表
     */
    public List<SubServerInfo> getOnlineSubServers() {
        List<SubServerInfo> result = new ArrayList<>();
        for (SubServerInfo info : servers.values()) {
            if (info.isOnline()) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 更新子服务器在线状态。
     *
     * @param id     子服务器唯一标识
     * @param online 是否在线
     */
    public void updateOnlineStatus(String id, boolean online) {
        SubServerInfo info = servers.get(id);
        if (info != null) {
            info.setOnline(online);
            if (online) {
                info.setLastHeartbeat(System.currentTimeMillis());
            }
        }
    }

    /**
     * 返回已注册的子服务器数量。
     */
    public int size() {
        return servers.size();
    }

    /**
     * 清空所有子服务器。
     */
    public void clear() {
        servers.clear();
        log.info("清空子服务器注册表");
    }
}
