package com.weacsoft.jaravel.vendor.plugin.jar.model;

import java.util.HashSet;
import java.util.Set;

/**
 * 插件元信息。
 * <p>
 * 描述一个插件的 ID、版本、JAR 路径、状态、依赖、注册的 Bean、组件类、路由映射等。
 * 由 {@code HotPluginManager} 维护，并通过 {@code MetadataPersistence} 持久化。
 */
public class PluginInfo {

    /**
     * 插件状态。
     * <p>
     * 注意：原 spring-jar 项目中存在 {@code UPLODED} 拼写错误，此处修正为 {@link #UPLOADED}。
     */
    public enum State {
        /** 已上传但未启用 */
        UPLOADED,
        /** 已启用 */
        ENABLED,
        /** 已禁用 */
        DISABLED
    }

    /** 插件 ID */
    private String pluginId;
    /** 插件版本 */
    private String version;
    /** JAR 路径（磁盘路径或临时文件路径） */
    private String jarPath;
    /** 插件状态 */
    private State state;
    /** 共享类依赖（插件中标注 @SharedService 的类名，需放入共享 ClassLoader） */
    private Set<String> sharedClassDependencies = new HashSet<>();
    /** 已注册的 Bean 名称 */
    private Set<String> registeredBeanNames = new HashSet<>();
    /** 组件类（标注 @PluginComponent 的类名） */
    private Set<String> componentClasses = new HashSet<>();
    /** 路由映射 */
    private Set<RouteInfo> routeMappings = new HashSet<>();
    /** 可注册但未自动注册的路由（manual-register 模式下使用） */
    private Set<RouteInfo> availableRoutes = new HashSet<>();
    /** 错误信息（启用失败时记录） */
    private String errorMessage;
    /** 是否磁盘持久化：true 表示 JAR 已落盘可自动恢复，false 表示仅内存 */
    private boolean persisted;

    public PluginInfo() {
        this.state = State.UPLOADED;
    }

    public PluginInfo(String pluginId, String version, String jarPath) {
        this.pluginId = pluginId;
        this.version = version;
        this.jarPath = jarPath;
        this.state = State.UPLOADED;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getJarPath() {
        return jarPath;
    }

    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Set<String> getSharedClassDependencies() {
        return sharedClassDependencies;
    }

    public void setSharedClassDependencies(Set<String> sharedClassDependencies) {
        this.sharedClassDependencies = sharedClassDependencies;
    }

    public Set<String> getRegisteredBeanNames() {
        return registeredBeanNames;
    }

    public void setRegisteredBeanNames(Set<String> registeredBeanNames) {
        this.registeredBeanNames = registeredBeanNames;
    }

    public Set<String> getComponentClasses() {
        return componentClasses;
    }

    public void setComponentClasses(Set<String> componentClasses) {
        this.componentClasses = componentClasses;
    }

    public Set<RouteInfo> getRouteMappings() {
        return routeMappings;
    }

    public void setRouteMappings(Set<RouteInfo> routeMappings) {
        this.routeMappings = routeMappings;
    }

    public Set<RouteInfo> getAvailableRoutes() {
        return availableRoutes;
    }

    public void setAvailableRoutes(Set<RouteInfo> availableRoutes) {
        this.availableRoutes = availableRoutes;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isPersisted() {
        return persisted;
    }

    public void setPersisted(boolean persisted) {
        this.persisted = persisted;
    }
}
