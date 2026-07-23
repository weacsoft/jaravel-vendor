package com.weacsoft.jaravel.vendor.plugin.jar.remote.client;

import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteRequest;
import com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol.ExecuteResponse;
import com.weacsoft.jaravel.vendor.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 远程服务代理工厂。
 * <p>
 * 通过 Java 动态代理实现自动包装/解包：调用方像调用本地方法一样调用远程服务，
 * 代理自动将方法名和参数序列化为 {@link ExecuteRequest}，通过 {@link RemoteExecutionDispatcher}
 * 发送到远程服务器，并将响应解包为返回值。
 * <p>
 * <h3>使用示例</h3>
 * <pre>
 * // 定义服务接口（需在主程序和子服务器上同时存在）
 * public interface BlogService {
 *     String getTitle(long id);
 *     List&lt;String&gt; getPosts(int page, int size);
 * }
 *
 * // 创建远程代理（指定子服务器）
 * BlogService blog = RemoteProxy.create(BlogService.class, "blog", "blogService",
 *         dispatcher, "sub1");
 * // 像本地方法一样调用 — 自动包装参数、远程执行、解包返回值
 * String title = blog.getTitle(42);
 * List&lt;String&gt; posts = blog.getPosts(1, 10);
 *
 * // 创建远程代理（不指定子服务器，由协调器分配）
 * BlogService blog2 = RemoteProxy.create(BlogService.class, "blog", "blogService",
 *         dispatcher, null);
 * String title2 = blog2.getTitle(42);
 * </pre>
 * <p>
 * <h3>自动包装规则</h3>
 * <ul>
 *   <li>方法名 → {@code ExecuteRequest.methodName}</li>
 *   <li>每个参数 → JSON 序列化为字符串，存入 {@code ExecuteRequest.args}</li>
 *   <li>每个参数类型 → 全限定类名，存入 {@code ExecuteRequest.argTypes}</li>
 *   <li>返回值 → 从 {@code ExecuteResponse.result} JSON 反序列化为方法返回类型</li>
 * </ul>
 * <p>
 * <h3>异常处理</h3>
 * 远程执行失败时抛出 {@link RuntimeException}，包含错误信息。
 */
public class RemoteProxy {

    private static final Logger log = LoggerFactory.getLogger(RemoteProxy.class);

    /**
     * 创建远程服务代理。
     *
     * @param <T>            服务接口类型
     * @param interfaceClass 服务接口类
     * @param pluginId       插件 ID
     * @param beanName       Bean 名称
     * @param dispatcher     远程执行调度器
     * @param subServerId    子服务器 ID（null 表示由协调器分配）
     * @return 远程服务代理实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> interfaceClass, String pluginId, String beanName,
                               RemoteExecutionDispatcher dispatcher, String subServerId) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new RemoteInvocationHandler(pluginId, beanName, dispatcher, subServerId));
    }

    /**
     * 创建远程服务代理（不指定子服务器，由协调器分配）。
     *
     * @param <T>            服务接口类型
     * @param interfaceClass 服务接口类
     * @param pluginId       插件 ID
     * @param beanName       Bean 名称
     * @param dispatcher     远程执行调度器
     * @return 远程服务代理实例
     */
    public static <T> T create(Class<T> interfaceClass, String pluginId, String beanName,
                               RemoteExecutionDispatcher dispatcher) {
        return create(interfaceClass, pluginId, beanName, dispatcher, null);
    }

    /**
     * 远程调用处理器。
     * <p>
     * 拦截代理对象上的方法调用，将其转换为远程执行请求。
     */
    private static class RemoteInvocationHandler implements InvocationHandler {

        private final String pluginId;
        private final String beanName;
        private final RemoteExecutionDispatcher dispatcher;
        private final String subServerId;

        RemoteInvocationHandler(String pluginId, String beanName,
                                RemoteExecutionDispatcher dispatcher, String subServerId) {
            this.pluginId = pluginId;
            this.beanName = beanName;
            this.dispatcher = dispatcher;
            this.subServerId = subServerId;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Object 原生方法直接本地调用
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            // 构建执行请求
            String requestId = UUID.randomUUID().toString();
            List<String> argJsonList = new ArrayList<>();
            List<String> argTypeList = new ArrayList<>();
            if (args != null) {
                for (Object arg : args) {
                    if (arg == null) {
                        argJsonList.add("null");
                        argTypeList.add("java.lang.Object");
                    } else {
                        argJsonList.add(Json.stringify(arg));
                        argTypeList.add(arg.getClass().getName());
                    }
                }
            }
            ExecuteRequest request = new ExecuteRequest(
                    requestId, pluginId, beanName, method.getName(), argJsonList, argTypeList);
            // 发送请求
            ExecuteResponse response;
            if (subServerId != null) {
                response = dispatcher.executeOn(subServerId, pluginId, beanName,
                        method.getName(), argJsonList, argTypeList);
            } else {
                response = dispatcher.execute(pluginId, beanName,
                        method.getName(), argJsonList, argTypeList);
            }
            // 处理响应
            if (!response.isSuccess()) {
                throw new RuntimeException("远程执行失败: " + response.getError());
            }
            // 解包返回值
            if (response.getResult() == null) {
                return null;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == void.class || returnType == Void.class) {
                return null;
            }
            if (returnType == String.class) {
                // 如果结果是 JSON 字符串，需要去掉外层引号
                String result = response.getResult();
                if (result.startsWith("\"") && result.endsWith("\"")) {
                    return Json.parse(result, String.class);
                }
                return result;
            }
            return Json.parse(response.getResult(), returnType);
        }
    }
}
