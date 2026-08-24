package com.weacsoft.jaravel.vendor.artisan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Artisan 命令抽象类，对齐 Laravel {@code Illuminate\Console\Command}。
 * <p>
 * 每个命令需定义 {@link #signature()}（命令签名）和 {@link #description()}（描述），
 * 并实现 {@link #handle()} 执行具体逻辑。
 *
 * <h3>命令签名格式</h3>
 * 对齐 Laravel signature 语法：
 * <pre>
 * "user:score:cacheScore"                    // 无参数
 * "migrate {--force}"                        // 带选项
 * "user:score:cacheOne {studentId}"          // 带位置参数
 * "user:score:cacheOne {studentId} {--sync}" // 混合
 * </pre>
 *
 * <h3>参数与选项访问</h3>
 * 子类在 {@link #handle()} 中通过 {@link #argument(String)} / {@link #option(String)} 读取。
 */
public abstract class ArtisanCommand {

    /** 命令签名，如 {@code user:score:cacheScore} */
    private String signature;

    /** 命令描述 */
    private String description;

    /** 解析后的位置参数：参数名 -> 值 */
    protected Map<String, String> arguments = new LinkedHashMap<>();

    /** 解析后的选项：选项名 -> 值（布尔选项值为 "true"） */
    protected Map<String, String> options = new LinkedHashMap<>();

    /**
     * @return 命令签名，如 {@code user:score:cacheScore}
     */
    public abstract String signature();

    /**
     * @return 命令描述
     */
    public String description() {
        return "";
    }

    /**
     * 执行命令，返回退出码。
     *
     * @return 0 表示成功，非 0 表示失败
     */
    public abstract int handle();

    /**
     * 获取位置参数值。
     *
     * @param name 参数名
     * @return 参数值，不存在返回 null
     */
    protected String argument(String name) {
        return arguments.get(name);
    }

    /**
     * 获取位置参数值，带默认值。
     */
    protected String argument(String name, String defaultValue) {
        return arguments.getOrDefault(name, defaultValue);
    }

    /**
     * 获取选项值。
     *
     * @param name 选项名（不含 -- 前缀）
     * @return 选项值，布尔选项返回 "true"，不存在返回 null
     */
    protected String option(String name) {
        return options.get(name);
    }

    /**
     * 判断选项是否存在。
     */
    protected boolean hasOption(String name) {
        return options.containsKey(name);
    }

    /**
     * 获取选项值，带默认值。
     */
    protected String option(String name, String defaultValue) {
        return options.getOrDefault(name, defaultValue);
    }

    /**
     * 输出信息到控制台。
     */
    protected void info(String message) {
        System.out.println(message);
    }

    /**
     * 输出错误信息到控制台。
     */
    protected void error(String message) {
        System.err.println("[ERROR] " + message);
    }

    /**
     * 输出警告信息到控制台。
     */
    protected void warn(String message) {
        System.err.println("[WARN] " + message);
    }

    // ---- 以下方法由 ArtisanApplication 在调度前调用 ----

    /**
     * 设置解析后的参数和选项（由 {@link ArtisanApplication} 调用）。
     */
    void setParsed(Map<String, String> arguments, Map<String, String> options) {
        this.arguments = arguments != null ? arguments : new LinkedHashMap<>();
        this.options = options != null ? options : new LinkedHashMap<>();
    }

    /**
     * @return 命令名（签名中第一个空格前的部分）
     */
    public String commandName() {
        String sig = signature();
        int space = sig.indexOf(' ');
        return space > 0 ? sig.substring(0, space) : sig;
    }
}
