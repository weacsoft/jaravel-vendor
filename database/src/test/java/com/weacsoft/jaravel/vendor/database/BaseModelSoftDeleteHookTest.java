package com.weacsoft.jaravel.vendor.database;

import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import gaarason.database.annotation.Table;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 软删除扩展点契约测试（上游 issue gaarason/database-all#63）。
 * <p>
 * <b>为什么需要这几个扩展点</b>：gaarason 7.1.2 起提供
 * {@code updateOrCreateAutoRestore} / {@code updateByPrimaryKeyOrCreateAutoRestore}
 * 等「命中已软删除记录时，在同一条 SQL 内恢复并更新」的官方 API。
 * 但它的判断<b>全部</b>走模型自身的三个扩展点：
 * <ul>
 *   <li>{@code softDeleting()} —— 该模型是否启用软删除（gaarason 默认 <b>false</b>）</li>
 *   <li>{@code softDeletedColumnName()} —— 软删除列名（gaarason 默认 <b>is_deleted</b>）</li>
 *   <li>{@code isSoftDeleted(record)} —— 某记录是否已删除（默认按布尔列判断）</li>
 * </ul>
 * 而 jaravel 的语义是 {@code deleted_at IS NULL}（时间戳列）。
 * <b>不重写这三处，{@code isSoftDeleting()} 恒为 false，AutoRestore 永远不触发</b>——
 * 表现为「恢复了个寂寞」：{@code deleted_at} 仍是旧值，只是普通 UPDATE。
 * <p>
 * 正向验证「命中已删除记录时 deleted_at 真的被置 NULL」需要真实数据库 + Spring 上下文
 * （{@link BaseModel#save()} 经 {@code SpringContext.bean(getClass())} 取单例），
 * 属于集成测试范畴；本类锁住的是<b>更前置、且一旦写错整套逻辑就失效</b>的契约层：
 * 三个扩展点的返回值。这比端到端更快更稳，且失效时指向明确。
 * <p>
 * 此前 jaravel 用 {@code TrashedScope}（复刻 {@code updateOrCreate} 逻辑 + 额外一次 first 查询）
 * 绕过该缺口；扩展点接上后可直接用官方 AutoRestore，少一次查询、语义与 Laravel 一致。
 */
class BaseModelSoftDeleteHookTest {

    /** 测试用软删除模型。 */
    @Table(name = "hook_user")
    public static class HookUser extends BaseModel<HookUser, Long> {
        @Primary
        @Column(name = "id")
        private Long id;

        @Column(name = "username")
        private String username;

        @Column(name = "deleted_at")
        private String deletedAt;
    }

    /** 自定义软删除列名的模型，用于验证列名走 {@code getSoftDeleteColumnName()} 而非硬编码。 */
    @Table(name = "hook_custom")
    public static class CustomColumnUser extends BaseModel<CustomColumnUser, Long> {
        @Primary
        @Column(name = "id")
        private Long id;

        /** 与 {@link #getSoftDeleteColumnName()} 保持一致的自定义列名。 */
        @Column(name = "removed_at")
        private String removedAt;

        @Override
        protected String getSoftDeleteColumnName() {
            return "removed_at";
        }
    }

    /**
     * 核心断言：jaravel 的模型必须报告「启用软删除」。
     * <p>
     * gaarason 默认 {@code softDeleting()} 返回 {@code false}。若这里为 false，
     * {@code updateOrCreateAutoRestore} 会退化成普通 {@code updateOrCreate}
     * ——记录不会恢复，且不报错，是最难排查的一种失效。
     */
    @Test
    void softDeletingIsEnabledForJaravelModels() {
        // 注意：只能断言 isSoftDeleting()。gaarason 的 softDeleting() 声明在包私有的
        // ModelOfSoftDelete 上，protected 且跨包不可见，测试类根本调用不到它
        // ——这正是「给它加 @Override 能编译、但运行时不生效」的根因。
        assertTrue(new HookUser().isSoftDeleting(),
                "BaseModel 必须重写 isSoftDeleting() 返回 true，否则 updateOrCreateAutoRestore "
                        + "永远不会触发恢复（且不报错，只表现为 deleted_at 未被清空）");
    }

    /**
     * 软删除列名必须是 {@code deleted_at}，而不是 gaarason 默认的 {@code is_deleted}。
     * <p>
     * 列名错会导致 WHERE 条件落在不存在的列上，AutoRestore 的「是否已删除」判断完全失效。
     */
    @Test
    void softDeletedColumnNameDefaultsToDeletedAt() {
        assertEquals("deleted_at", new HookUser().softDeletedColumnName(),
                "软删除列名必须是 deleted_at（jaravel 约定），而非 gaarason 默认的 is_deleted");
    }

    /**
     * 业务 Model 可自定义软删除列名，且两个入口（{@code getSoftDeleteColumnName()} /
     * {@code softDeletedColumnName()}）必须**同源**——否则「作用域过滤」与「AutoRestore 判断」
     * 会看不同的列，产生极隐蔽的不一致。
     */
    @Test
    void customSoftDeleteColumnIsHonouredByBothEntryPoints() {
        CustomColumnUser model = new CustomColumnUser();
        assertEquals("removed_at", model.softDeletedColumnName(),
                "softDeletedColumnName() 必须转发 getSoftDeleteColumnName()，不能硬编码 deleted_at");
        assertEquals("removed_at", model.getSoftDeleteColumnName());
    }

    /**
     * {@code isSoftDeleted(null)} 必须安全返回 false，而不是 NPE。
     * <p>
     * AutoRestore 在「未命中任何记录」的分支上会走到这里；抛 NPE 会让「没有记录则创建」
     * 这条最普通的路径直接失败。
     */
    @Test
    void isSoftDeletedToleratesNullRecord() {
        assertFalse(new HookUser().isSoftDeleted(null),
                "isSoftDeleted(null) 应返回 false 而不是抛 NPE");
    }
}
