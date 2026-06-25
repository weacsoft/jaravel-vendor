package com.weacsoft.jaravel.vendor.plugin.jar.remote.server;

import com.weacsoft.jaravel.vendor.plugin.jar.annotation.Application;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteRequest;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP RPC 请求处理工具类。
 * <p>
 * 不注册任何 HTTP 端点，仅提供静态方法供用户在自己的控制器中调用。
 * 用户自行决定端点路径、HTTP 方法、认证方式等，只需将请求体传入本类的静态方法即可。
 * <p>
 * <h3>使用方式</h3>
 * 用户在自己的 Spring MVC 控制器中调用：
 * <pre>
 * {@literal @RestController}
 * public class MyRpcController {
 *     {@literal @Autowired} private HotPluginManager manager;
 *
 *     {@literal @PostMapping("/my-custom-rpc-endpoint")}
 *     public String handleRpc({@literal @RequestBody} String body,
 *                              {@literal @RequestHeader(value = "X-Auth-Token", required = false)} String token) {
 *         return HttpRpcHandler.processRequest(manager, "my-secret", token, body);
 *     }
 * }
 * </pre>
 * <p>
 * <h3>请求格式（输入）</h3>
 * 请求体为 {@link ExecuteRequest} 的 JSON 序列化，格式如下：
 * <pre>
 * {
 *   "requestId": "uuid-string",       // 请求 ID（用于匹配响应）
 *   "pluginId": "blog",               // 插件 ID
 *   "beanName": "blogController",     // Bean 名称
 *   "methodName": "list",             // 方法名
 *   "args": ["{\"page\":1}"],         // 参数值列表（每个参数 JSON 序列化为字符串）
 *   "argTypes": ["java.lang.Integer"] // 参数类型列表（全限定类名）
 * }
 * </pre>
 * <p>
 * <h3>响应格式（输出）</h3>
 * 返回 {@link ExecuteResponse} 的 JSON 序列化，格式如下：
 * <pre>
 * {
 *   "requestId": "uuid-string",       // 对应请求的 ID
 *   "success": true,                  // 是否执行成功
 *   "result": "{\"title\":\"hello\"}",// 执行结果（JSON 序列化，成功时）
 *   "resultType": "java.lang.String", // 结果类型（全限定类名，成功时）
 *   "error": null                     // 错误信息（失败时）
 * }
 * </pre>
 * <p>
 * <h3>认证</h3>
 * 认证逻辑由用户自行实现（在控制器中检查 header 等）。
 * 本类的 {@link #processRequest} 方法支持可选的 authToken 校验：
 * 若 authToken 非空且与传入的 token 不匹配，返回认证失败响应。
 */
public final class HttpRpcHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpRpcHandler.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private HttpRpcHandler() {
    }

    /**
     * 处理 RPC 请求（带认证）。
     * <p>
     * 用户在自己的控制器中调用此方法，传入请求体 JSON 字符串。
     * 方法内部解析请求、本地执行插件方法、返回响应 JSON 字符串。
     *
     * @param managerRef 插件管理器引用
     * @param authToken  预期的认证令牌（null 或空表示不认证）
     * @param token      请求中携带的令牌（从 HTTP Header 获取）
     * @param requestBody 请求体 JSON 字符串（{@link ExecuteRequest} 的序列化）
     * @return 响应 JSON 字符串（{@link ExecuteResponse} 的序列化）
     */
    public static String processRequest(Application.HotPluginManagerRef managerRef,
                                        String authToken, String token, String requestBody) {
        // 认证
        if (authToken != null && !authToken.isEmpty()) {
            if (!authToken.equals(token)) {
                return toJson(ExecuteResponse.error(null, "认证失败"));
            }
        }
        try {
            ExecuteRequest execRequest = objectMapper.readValue(requestBody, ExecuteRequest.class);
            ExecuteResponse execResponse = executeLocally(managerRef, execRequest);
            return toJson(execResponse);
        } catch (Exception e) {
            log.error("处理 RPC 请求失败", e);
            return toJson(ExecuteResponse.error(null, "服务端错误: " + e.getMessage()));
        }
    }

    /**
     * 处理 RPC 请求（不认证）。
     *
     * @param managerRef 插件管理器引用
     * @param requestBody 请求体 JSON 字符串
     * @return 响应 JSON 字符串
     */
    public static String processRequest(Application.HotPluginManagerRef managerRef, String requestBody) {
        return processRequest(managerRef, null, null, requestBody);
    }

    /**
     * 在本地执行插件方法。
     */
    private static ExecuteResponse executeLocally(Application.HotPluginManagerRef managerRef,
                                                   ExecuteRequest request) {
        try {
            Object bean = managerRef.getServiceFromPlugin(request.getPluginId(), request.getBeanName());
            if (bean == null) {
                return ExecuteResponse.error(request.getRequestId(),
                        "Bean 未找到: pluginId=" + request.getPluginId() + ", beanName=" + request.getBeanName());
            }
            java.lang.reflect.Method targetMethod = findMethod(bean.getClass(),
                    request.getMethodName(), request.getArgTypes());
            if (targetMethod == null) {
                return ExecuteResponse.error(request.getRequestId(),
                        "方法未找到: " + request.getMethodName());
            }
            Object[] args = resolveArguments(request.getArgs(), request.getArgTypes(), targetMethod);
            Object result = targetMethod.invoke(bean, args);
            String resultJson = result != null ? objectMapper.writeValueAsString(result) : null;
            String resultType = result != null ? result.getClass().getName() : null;
            return ExecuteResponse.ok(request.getRequestId(), resultJson, resultType);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("本地执行失败: pluginId={}, beanName={}, method={}",
                    request.getPluginId(), request.getBeanName(), request.getMethodName(), cause);
            return ExecuteResponse.error(request.getRequestId(), cause.getMessage());
        }
    }

    private static java.lang.reflect.Method findMethod(Class<?> beanClass, String methodName,
                                                         java.util.List<String> argTypes) {
        for (java.lang.reflect.Method method : beanClass.getMethods()) {
            if (!method.getName().equals(methodName)) continue;
            if (argTypes == null || argTypes.isEmpty()) return method;
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != argTypes.size()) continue;
            boolean match = true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!paramTypes[i].getName().equals(argTypes.get(i))
                        && !paramTypes[i].getSimpleName().equals(argTypes.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) return method;
        }
        for (java.lang.reflect.Method method : beanClass.getMethods()) {
            if (method.getName().equals(methodName)) return method;
        }
        return null;
    }

    private static Object[] resolveArguments(java.util.List<String> args, java.util.List<String> argTypes,
                                              java.lang.reflect.Method method) {
        if (args == null || args.isEmpty()) return new Object[0];
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] result = new Object[Math.min(args.size(), paramTypes.length)];
        for (int i = 0; i < result.length; i++) {
            try {
                result[i] = objectMapper.readValue(args.get(i), paramTypes[i]);
            } catch (Exception e) {
                result[i] = args.get(i);
            }
        }
        return result;
    }

    private static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"序列化错误\"}";
        }
    }
}
