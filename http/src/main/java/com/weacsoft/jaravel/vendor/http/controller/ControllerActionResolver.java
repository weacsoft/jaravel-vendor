package com.weacsoft.jaravel.vendor.http.controller;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 控制器动作解析器，将控制器引用解析为 {@link Controllers.Runner}。
 * <p>
 * 对齐 Laravel 的控制器路由引用机制：
 * <pre>
 * // Laravel
 * Route::get('/users', 'UserController@index');
 * </pre>
 * <p>
 * 支持两种引用方式：
 * <ul>
 *   <li><b>字符串</b>：{@code "UserController::index"} — 类名（简名或全限定名）+ 方法名，
 *       使用 {@code ::} 分隔。解析时从 {@link ControllerRegistry} 查找控制器实例。</li>
 *   <li><b>类对象</b>：{@code UserController.class} + 方法名 — 忽略包名，
 *       通过 {@link ControllerRegistry#resolve(Class)} 查找实例。</li>
 * </ul>
 * <p>
 * <b>延迟解析</b>：控制器引用在路由定义时存储为字符串/类对象，在首次请求时才解析。
 * 这保证了路由注册顺序与控制器扫描顺序无关——即使路由先于控制器注册定义，
 * 只要请求到达时控制器已注册即可正常工作。
 * <p>
 * <b>缓存</b>：解析结果（Method + 控制器实例）会缓存为 {@link Controllers.Runner}，
 * 后续请求直接复用，避免重复反射查找。控制器方法签名要求为
 * {@code Response method(Request request)}。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 字符串形式
 * router.get("/users", "UserController::list");
 * router.get("/users/{id}", "UserController::show");
 *
 * // 类对象形式（忽略包名）
 * router.get("/users", UserController.class, "list");
 * router.get("/users/{id}", UserController.class, "show");
 * }</pre>
 *
 * @see ControllerRegistry
 * @see Controllers.Runner
 */
public class ControllerActionResolver {

    /** 解析结果缓存：key → Runner */
    private static final ConcurrentMap<String, Controllers.Runner> cache = new ConcurrentHashMap<>();

    /**
     * 解析字符串形式的控制器引用为 {@link Controllers.Runner}。
     * <p>
     * 格式：{@code "ControllerName::methodName"} 或 {@code "com.example.ControllerName::methodName"}。
     * 解析后缓存，后续调用直接返回缓存的 Runner。
     *
     * @param controllerAction 控制器引用字符串
     * @return 控制器动作 Runner
     * @throws IllegalArgumentException 格式错误或控制器/方法不存在时抛出
     */
    public static Controllers.Runner resolve(String controllerAction) {
        return cache.computeIfAbsent("str:" + controllerAction, k -> doResolve(controllerAction));
    }

    /**
     * 解析类对象形式的控制器引用为 {@link Controllers.Runner}。
     * <p>
     * 通过 {@link ControllerRegistry} 查找控制器实例，反射查找指定方法。
     * 解析后缓存，后续调用直接返回缓存的 Runner。
     *
     * @param controllerClass 控制器类
     * @param methodName      方法名
     * @return 控制器动作 Runner
     * @throws IllegalArgumentException 控制器/方法不存在时抛出
     */
    public static Controllers.Runner resolve(Class<?> controllerClass, String methodName) {
        return cache.computeIfAbsent(
                "cls:" + controllerClass.getName() + "::" + methodName,
                k -> doResolve(controllerClass, methodName)
        );
    }

    /**
     * 清除缓存（主要用于测试）。
     */
    public static void clearCache() {
        cache.clear();
    }

    // ========== 内部实现 ==========

    private static Controllers.Runner doResolve(String controllerAction) {
        int sep = controllerAction.indexOf("::");
        if (sep < 0) {
            throw new IllegalArgumentException(
                    "控制器引用格式应为 'ControllerName::methodName'，实际: " + controllerAction);
        }
        String controllerName = controllerAction.substring(0, sep).trim();
        String methodName = controllerAction.substring(sep + 2).trim();

        if (controllerName.isEmpty() || methodName.isEmpty()) {
            throw new IllegalArgumentException(
                    "控制器引用的类名和方法名不能为空: " + controllerAction);
        }

        Object controller = ControllerRegistry.getGlobal().resolve(controllerName);
        Method method = findMethod(controller.getClass(), methodName);
        return createRunner(controller, method, controllerAction);
    }

    private static Controllers.Runner doResolve(Class<?> controllerClass, String methodName) {
        Object controller = ControllerRegistry.getGlobal().resolve(controllerClass);
        Method method = findMethod(controllerClass, methodName);
        return createRunner(controller, method, controllerClass.getSimpleName() + "::" + methodName);
    }

    private static Method findMethod(Class<?> clazz, String methodName) {
        try {
            Method method = clazz.getMethod(methodName, Request.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "控制器方法不存在: " + clazz.getSimpleName() + "::" + methodName + "(Request)", e);
        }
    }

    private static Controllers.Runner createRunner(Object controller, Method method, String description) {
        return request -> {
            try {
                return (Response) method.invoke(controller, request);
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new RuntimeException(
                        "控制器方法调用失败: " + description + " — " + cause.getMessage(), cause);
            }
        };
    }
}
