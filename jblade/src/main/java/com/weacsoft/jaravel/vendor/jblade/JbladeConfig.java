package com.weacsoft.jaravel.vendor.jblade;

/**
 * jblade 模板引擎静态配置类。
 * <p>
 * 此类<b>不</b>需要注入 Spring，直接作为静态字段使用。
 * 发布后在业务工程的 {@code ViewConfig} 中设置这些字段即可生效。
 * </p>
 */
public class JbladeConfig {

    /**
     * 严格模式：开启后，模板访问未定义变量或数组 key 时会输出 warning。
     * <p>
     * 默认 {@code false}，开启后在开发阶段有助于发现模板 bug。
     * </p>
     */
    public static boolean strictMode = false;
}
