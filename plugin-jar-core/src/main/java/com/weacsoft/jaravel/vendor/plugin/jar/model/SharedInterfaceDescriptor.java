package com.weacsoft.jaravel.vendor.plugin.jar.model;

/**
 * 共享接口描述符。
 * <p>
 * 描述一个通过全手动指定方式注册的共享接口：指定插件中的某个 Bean 的某个方法
 * 作为可被其他模块反射调用的共享接口。
 * <p>
 * 开发时无需包含目标类，运行时通过反射调用。请求参数和返回参数都用 Map 表示。
 *
 * @see com.weacsoft.jaravel.vendor.plugin.jar.manager.HotPluginManager#registerSharedInterface
 */
public class SharedInterfaceDescriptor {

    /** 共享接口名称（全局唯一，如 "admin.service.list"） */
    private String interfaceName;
    /** 提供方插件 ID（如 "studentA@blog"） */
    private String pluginId;
    /** Bean 名称（如 "blogController"） */
    private String beanName;
    /** 方法名（如 "list"） */
    private String methodName;
    /** 可选描述 */
    private String description;

    public SharedInterfaceDescriptor() {
    }

    public SharedInterfaceDescriptor(String interfaceName, String pluginId,
                                      String beanName, String methodName) {
        this.interfaceName = interfaceName;
        this.pluginId = pluginId;
        this.beanName = beanName;
        this.methodName = methodName;
    }

    public SharedInterfaceDescriptor(String interfaceName, String pluginId,
                                      String beanName, String methodName, String description) {
        this.interfaceName = interfaceName;
        this.pluginId = pluginId;
        this.beanName = beanName;
        this.methodName = methodName;
        this.description = description;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "SharedInterfaceDescriptor{"
                + "interfaceName='" + interfaceName + '\''
                + ", pluginId='" + pluginId + '\''
                + ", beanName='" + beanName + '\''
                + ", methodName='" + methodName + '\''
                + ", description='" + description + '\''
                + '}';
    }
}
