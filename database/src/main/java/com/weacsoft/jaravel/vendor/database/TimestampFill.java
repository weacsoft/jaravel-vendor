package com.weacsoft.jaravel.vendor.database;

import gaarason.database.contract.support.FieldFill;
import gaarason.database.lang.Nullable;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间戳自动填充器，将当前时间以 {@code "yyyy-MM-dd HH:mm:ss"} 格式的 String 填充到字段。
 * <p>
 * 解决 gaarason 内置 {@link gaarason.database.contract.support.FieldFill.CreatedTimeFill} /
 * {@link gaarason.database.contract.support.FieldFill.UpdatedTimeFill} 不支持 String 类型的问题
 * （{@code DateTimeUtils.currentDateTime()} 仅支持 Date/LocalDateTime 等 8 种类型，String 会抛出
 * {@code TypeNotSupportedException}）。
 * <p>
 * <b>填充规则</b>：
 * <ul>
 *   <li>{@link CreatedTimeStringFill} — 创建时间：仅插入时填充，更新时保留原值</li>
 *   <li>{@link UpdatedTimeStringFill} — 更新时间：插入和更新时均填充</li>
 * </ul>
 * <p>
 * <b>使用方式</b>：
 * <pre>
 * &#64;Column(name = "created_at", fill = CreatedTimeStringFill.class)
 * private String createdAt;
 *
 * &#64;Column(name = "updated_at", fill = UpdatedTimeStringFill.class)
 * private String updatedAt;
 * </pre>
 * <p>
 * 时间格式为本地时间 {@code "yyyy-MM-dd HH:mm:ss"}，与 Laravel 迁移的 {@code timestamps()}
 * 生成的列类型（DATETIME/VARCHAR）兼容。
 *
 * @see CreatedTimeStringFill
 * @see UpdatedTimeStringFill
 */
public final class TimestampFill {

    /** 标准日期时间格式：{@code yyyy-MM-dd HH:mm:ss} */
    public static final String DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** 日期时间格式化器（线程安全） */
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_FORMAT);

    private TimestampFill() {
    }

    /**
     * 返回当前本地时间的格式化字符串。
     *
     * @return 如 {@code "2023-04-28 16:06:33"}
     */
    public static String nowString() {
        return LocalDateTime.now().format(FORMATTER);
    }

    /**
     * 创建时间填充器：仅插入时填充当前时间，更新时保留原值。
     * <p>
     * 对齐 Laravel Eloquent 中 {@code created_at} 的行为 — 仅在 INSERT 时设置，UPDATE 时不变。
     */
    public static class CreatedTimeStringFill implements FieldFill {

        @Nullable
        @Override
        public <W> W inserting(Object entity, Field field, @Nullable W originalValue) {
            return (W) nowString();
        }

        @Nullable
        @Override
        public <W> W updating(Object entity, Field field, @Nullable W originalValue) {
            return originalValue;
        }

        @Nullable
        @Override
        public <W> W condition(Object entity, Field field, @Nullable W originalValue) {
            return originalValue;
        }
    }

    /**
     * 更新时间填充器：插入和更新时均填充当前时间。
     * <p>
     * 对齐 Laravel Eloquent 中 {@code updated_at} 的行为 — INSERT 时设置初始值，
     * UPDATE 时刷新为当前时间。
     * <p>
     * 与 gaarason 内置 {@link gaarason.database.contract.support.FieldFill.UpdatedTimeFill} 不同，
     * 本实现也在插入时填充（内置的 UpdatedTimeFill 插入时返回原值）。
     */
    public static class UpdatedTimeStringFill implements FieldFill {

        @Nullable
        @Override
        public <W> W inserting(Object entity, Field field, @Nullable W originalValue) {
            return (W) nowString();
        }

        @Nullable
        @Override
        public <W> W updating(Object entity, Field field, @Nullable W originalValue) {
            return (W) nowString();
        }

        @Nullable
        @Override
        public <W> W condition(Object entity, Field field, @Nullable W originalValue) {
            return originalValue;
        }
    }
}
