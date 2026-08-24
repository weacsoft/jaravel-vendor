package com.weacsoft.jaravel.vendor.wire;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记被 @WireLocked 注解修饰的 public 属性不可通过 wire:model 等客户端通道更新。
 * <p>
 * 语义对应 Laravel Livewire 的 #[Locked]：客户端尝试修改被锁定的属性时,WireController
 * 在合并 wire_body 参数时自动过滤,不更新该属性的值。属性在后端代码中仍可正常修改。
 * <p>
 * 适用场景：防止客户端通过篡改快照或注入 wire:model 修改受保护字段(如记录 ID、用户 ID 等)。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WireLocked {
}