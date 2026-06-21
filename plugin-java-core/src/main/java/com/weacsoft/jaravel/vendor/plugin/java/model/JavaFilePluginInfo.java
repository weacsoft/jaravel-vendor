package com.weacsoft.jaravel.vendor.plugin.java.model;

import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Java 文件插件信息模型。
 * <p>
 * 描述一个 .java 文件插件的完整元数据，包括源文件路径、组件类、路由映射、
 * 已注册 Bean 名称、状态和错误信息等。
 * <p>
 * 由 {@link com.weacsoft.jaravel.vendor.plugin.java.manager.JavaFilePluginManager} 在注册、
 * 启用、禁用、重载插件时维护。
 */
public class JavaFilePluginInfo {

    /**
     * 插件状态枚举。
     */
    public enum State {
        /** 已加载（编译完成，但未启用） */
        LOADED,
        /** 已启用（Bean 和路由已注册） */
        ENABLED,
        /** 已禁用（Bean 和路由已注销，ClassLoader 已关闭） */
        DISABLED
    }

    /** 插件 ID（目录名） */
    private String pluginId;

    /** 源目录路径 */
    private String sourceDir;

    /** 插件状态 */
    private State state;

    /** .java 源文件路径集合 */
    private Set<String> sourceFiles = new HashSet<>();

    /** 带 @PluginComponent 注解的类全限定名集合 */
    private Set<String> componentClasses = new HashSet<>();

    /** 路由映射集合（来自 @PluginMapping） */
    private Set<RouteInfo> routeMappings = new HashSet<>();

    /** 可注册但未自动注册的路由（manual-register 模式下使用） */
    private Set<RouteInfo> availableRoutes = new HashSet<>();

    /** 已注册的 Bean 名称集合 */
    private Set<String> registeredBeanNames = new HashSet<>();

    /** 错误信息 */
    private String errorMessage;

    /** 最后修改时间戳（用于变更检测） */
    private long lastModified;

    public JavaFilePluginInfo() {
        this.state = State.LOADED;
    }

    public JavaFilePluginInfo(String pluginId, String sourceDir) {
        this.pluginId = pluginId;
        this.sourceDir = sourceDir;
        this.state = State.LOADED;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Set<String> getSourceFiles() {
        return sourceFiles;
    }

    public void setSourceFiles(Set<String> sourceFiles) {
        this.sourceFiles = sourceFiles != null ? sourceFiles : new HashSet<>();
    }

    public Set<String> getComponentClasses() {
        return componentClasses;
    }

    public void setComponentClasses(Set<String> componentClasses) {
        this.componentClasses = componentClasses != null ? componentClasses : new HashSet<>();
    }

    public Set<RouteInfo> getRouteMappings() {
        return routeMappings;
    }

    public void setRouteMappings(Set<RouteInfo> routeMappings) {
        this.routeMappings = routeMappings != null ? routeMappings : new HashSet<>();
    }

    public Set<RouteInfo> getAvailableRoutes() {
        return availableRoutes;
    }

    public void setAvailableRoutes(Set<RouteInfo> availableRoutes) {
        this.availableRoutes = availableRoutes != null ? availableRoutes : new HashSet<>();
    }

    public Set<String> getRegisteredBeanNames() {
        return registeredBeanNames;
    }

    public void setRegisteredBeanNames(Set<String> registeredBeanNames) {
        this.registeredBeanNames = registeredBeanNames != null ? registeredBeanNames : new HashSet<>();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public String toString() {
        return "JavaFilePluginInfo{" +
                "pluginId='" + pluginId + '\'' +
                ", sourceDir='" + sourceDir + '\'' +
                ", state=" + state +
                ", sourceFiles=" + sourceFiles.size() +
                ", componentClasses=" + componentClasses.size() +
                ", routeMappings=" + routeMappings.size() +
                ", availableRoutes=" + availableRoutes.size() +
                ", registeredBeanNames=" + registeredBeanNames.size() +
                ", errorMessage='" + errorMessage + '\'' +
                ", lastModified=" + lastModified +
                '}';
    }
}
