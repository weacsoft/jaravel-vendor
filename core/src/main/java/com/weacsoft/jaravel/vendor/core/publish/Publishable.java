package com.weacsoft.jaravel.vendor.core.publish;

/**
 * 可发布项统一契约（标记接口），对齐 Laravel {@code ServiceProvider::publishes()}。
 * <p>
 * core 模块只定义本契约，<b>不依赖 artisan</b>。各模块声明可发布项时只需实现
 * {@link PublishableConfig}（配置类源码）或 {@link PublishableStatic}（静态资源），
 * 两者均继承本接口。artisan 的 {@code vendor:publish} 命令<b>统一收集所有
 * {@link Publishable} bean，一次扫描、按需发布</b>，不再区分两条命令。
 *
 * <h3>对齐 Laravel 的用法</h3>
 * <pre>
 * artisan vendor:publish                       # 列出所有可发布项（配置 + 资源）
 * artisan vendor:publish --all                 # 发布全部
 * artisan vendor:publish --tag=cache           # 只发布 cache 模块（其配置与资源）
 * artisan vendor:publish --tag=resources       # 只发布全部静态前端资源
 * artisan vendor:publish --tag=config          # 只发布全部 Java 配置类
 * artisan vendor:publish --tag=captcha --force # 覆盖已存在文件
 * </pre>
 *
 * <h3>为什么是一个接口而不是两个命令</h3>
 * 早期实现里配置与静态资源分别由 {@code vendor:publish} 与 {@code vendor:publish:static}
 * 两条命令处理，每个模块要各自声明、容易出现「文档承诺了发布但代码没实现」的割裂。
 * 统一为单一 {@link Publishable} 后，命令在<b>执行时扫描全部 bean</b>（与 Laravel 一致），
 * 模块只管实现接口，静态资源不再需要单独的发布通道。
 */
public interface Publishable {

    /**
     * 发布标签，通常等于模块名（如 {@code cache} / {@code wire} / {@code captcha}）。
     * <p>
     * 命令层另支持两个保留标签：{@code resources}（全部静态资源）、{@code config}（全部配置类）。
     *
     * @return 标签名，不可为空
     */
    String tag();

    /**
     * 可发布项类型：{@link PublishType#CONFIG} 发布 Java 配置类源码，
     * {@link PublishType#RESOURCE} 发布静态前端资源。
     */
    PublishType type();

    /**
     * 用途描述，供 {@code vendor:publish --list} 展示。
     *
     * @return 描述文本，默认空串
     */
    default String description() {
        return "";
    }
}
