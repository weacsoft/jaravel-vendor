package com.weacsoft.jaravel.vendor.core.publish;

/**
 * 可发布项类型，对齐 Laravel {@code vendor:publish} 中「配置 / 资源 / 迁移」三类产物。
 */
public enum PublishType {

    /** 发布 Java 配置类源码到业务工程 {@code config/} 包 */
    CONFIG,

    /** 发布静态前端资源（js / css / html）到业务工程 {@code resources/static/} */
    RESOURCE,

    /** 发布模块自带的迁移 Java 源文件到业务工程迁移源代码目录（对齐 Laravel {@code vendor:publish --tag=migrations}） */
    MIGRATION
}
