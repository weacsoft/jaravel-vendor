package com.weacsoft.jaravel.vendor.plugin.jar.executor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * 插件执行公共辅助工具。
 * <p>
 * 提取 {@link JarBytesExecutor} 和 {@code JavaSourceExecutor} 中重复的
 * 反射调用逻辑，统一「优先 run() → 其次 main(String[])」的调用约定。
 */
public final class PluginExecutionHelper {

    private PluginExecutionHelper() {
    }

    /**
     * 反射调用指定类的 {@code run()} 或 {@code main(String[])} 方法，并将结果写入 result Map。
     * <p>
     * 调用优先级：
     * <ol>
     *   <li>{@code run()}：静态或实例方法均可，有返回值则记录输出</li>
     *   <li>{@code main(String[])}：仅调用静态方法，无返回值时记录固定输出</li>
     * </ol>
     * 若两者均不存在，result 中写入失败状态。
     *
     * @param result 执行结果 Map（会被修改）
     * @param clazz  要调用的目标类
     * @throws Exception 反射调用过程中可能抛出的异常
     */
    public static void invokeAndSetResult(Map<String, Object> result, Class<?> clazz) throws Exception {
        Object output = null;
        boolean invoked = false;

        // 优先调用 run()
        try {
            Method runMethod = clazz.getMethod("run");
            output = Modifier.isStatic(runMethod.getModifiers())
                    ? runMethod.invoke(null)
                    : runMethod.invoke(clazz.getDeclaredConstructor().newInstance());
            invoked = true;
        } catch (NoSuchMethodException e) {
            // 没有 run() 方法，尝试 main()
        }

        // 其次调用 main()
        if (!invoked) {
            try {
                Method mainMethod = clazz.getMethod("main", String[].class);
                if (Modifier.isStatic(mainMethod.getModifiers())) {
                    mainMethod.invoke(null, (Object) new String[]{});
                    output = "main() 方法已执行";
                    invoked = true;
                }
            } catch (NoSuchMethodException e) {
                // 没有 main() 方法
            }
        }

        if (!invoked) {
            result.put("success", false);
            result.put("error", "未找到 run() 或 main() 方法");
            return;
        }

        result.put("success", true);
        result.put("output", output != null ? output.toString() : "null");
    }
}
