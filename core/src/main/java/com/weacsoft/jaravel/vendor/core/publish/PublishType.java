package com.weacsoft.jaravel.vendor.core.publish;

/**
 * 可发布项类型，对齐 Laravel {@code vendor:publish} 中「配置」与「资源」两类产物。
 */
public enum PublishType {

    /** 发布 Java 配置类源码到业务工程 {@code config/} 包 */
    CONFIG,

    /** 发布静态前端资源（js / css / html）到业务工程 {@code resources/static/} */
    RESOURCE
}
