package com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol;

import java.util.List;

/**
 * 远程方法执行请求。
 * <p>
 * 由客户端发送给服务端，请求在服务端本地执行指定的插件方法。
 * <p>
 * 隔离度：方法级。每次执行都是独立的，不共享调用间状态。
 */
public class ExecuteRequest {

    /** 请求 ID（用于匹配响应） */
    private String requestId;

    /** 插件 ID */
    private String pluginId;

    /** Bean 名称（在插件 Spring 容器中的名称） */
    private String beanName;

    /** 方法名 */
    private String methodName;

    /** 参数值列表（JSON 序列化） */
    private List<String> args;

    /** 参数类型列表（全限定类名，用于反射查找方法） */
    private List<String> argTypes;

    public ExecuteRequest() {
    }

    public ExecuteRequest(String requestId, String pluginId, String beanName,
                          String methodName, List<String> args, List<String> argTypes) {
        this.requestId = requestId;
        this.pluginId = pluginId;
        this.beanName = beanName;
        this.methodName = methodName;
        this.args = args;
        this.argTypes = argTypes;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }
    public String getBeanName() { return beanName; }
    public void setBeanName(String beanName) { this.beanName = beanName; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public List<String> getArgs() { return args; }
    public void setArgs(List<String> args) { this.args = args; }
    public List<String> getArgTypes() { return argTypes; }
    public void setArgTypes(List<String> argTypes) { this.argTypes = argTypes; }
}
