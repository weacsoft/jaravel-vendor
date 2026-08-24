package com.weacsoft.jaravel.vendor.database;

import com.weacsoft.jaravel.vendor.core.SpringContext;
import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import gaarason.database.contract.eloquent.Record;
import gaarason.database.query.QueryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BaseModel.TrashedScope} 软删除感知写入操作单元测试。
 * <p>
 * 覆盖核心诉求：{@code withTrash().updateOrCreate(condition, complement).restore()} 能够
 * 在<b>包含软删除</b>的范围内匹配记录（命中已删除记录时走 UPDATE 并可 restore，未命中时走 INSERT），
 * 以及 {@code onlyTrash()} 使用 {@code onlyTrashed()} 作用域。gaarason 查询链以 Mockito 模拟。
 */
class BaseModelTrashedScopeTest {

    /** 测试用软删除模型 */
    public static class TrashUser extends BaseModel<TrashUser, Long> {
        @Primary
        @Column(name = "id")
        private Long id;

        @Column(name = "username")
        private String username;

        @Column(name = "nickname")
        private String nickname;

        public void setUsername(String username) {
            this.username = username;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
    }

    private TrashUser spyModel;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // 用 spy 包装模型，使 gaarason 的 withTrashed()/onlyTrashed()/newQuery()/newRecord()/getEntityClass() 可打桩
        spyModel = spy(new TrashUser());
        doReturn(TrashUser.class).when(spyModel).getEntityClass();

        // 将 spy 注册进 Spring 上下文：TrashedScope 内部通过 SpringContext.bean(getClass()) 取回该实例
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("trashUser", spyModel);
        ctx.refresh();
        new SpringContext().setApplicationContext(ctx);
    }

    @AfterEach
    void tearDown() {
        new SpringContext().setApplicationContext(null);
    }

    /**
     * 构造一个 select(...).where(...).first() 返回指定 Record 的 QueryBuilder mock。
     * <p>
     * 注意必须 mock {@link QueryBuilder}（而非其父接口 {@link Builder}）：gaarason 的
     * {@code withTrashed()}/{@code onlyTrashed()}/{@code newQuery()} 静态返回类型为 {@code QueryBuilder}，
     * BaseModel 内部会隐式向 {@code QueryBuilder} 转换，若 mock 父接口会触发 ClassCastException。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private QueryBuilder<TrashUser, Long> builderReturning(Record<TrashUser, Long> first) {
        QueryBuilder builder = mock(QueryBuilder.class);
        when(builder.select(any(Class.class))).thenReturn(builder);
        when(builder.where(any(TrashUser.class))).thenReturn(builder);
        when(builder.first()).thenReturn(first);
        return builder;
    }

    private TrashUser condition() {
        TrashUser c = new TrashUser();
        c.setUsername("root");
        return c;
    }

    private TrashUser complement() {
        TrashUser c = new TrashUser();
        c.setNickname("超级管理员");
        return c;
    }

    @Test
    @SuppressWarnings("unchecked")
    void withTrashUpdateOrCreateHitsSoftDeletedRecordThenUpdatesAndCanRestore() {
        // 命中一条（已软删除的）记录：应使用 withTrashed() 作用域查询，命中后 fillEntity + save，并可继续 restore()
        Record<TrashUser, Long> hit = mock(Record.class);
        doReturn(builderReturning(hit)).when(spyModel).withTrashed();

        Record<TrashUser, Long> result = spyModel.withTrash().updateOrCreate(condition(), complement());

        assertSame(hit, result, "命中时应返回查询到的记录");
        verify(spyModel).withTrashed();              // 走的是包含软删除的作用域
        verify(spyModel, never()).newQuery();        // 不应走默认(仅未删除)作用域
        verify(hit).fillEntity(any(TrashUser.class)); // 用 complement 补全
        verify(hit).save();                          // 执行更新
        verify(spyModel, never()).newRecord();       // 命中则不新建

        // 链式 restore()：Record.restore() 被调用（软删除恢复）
        result.restore();
        verify(hit).restore();
    }

    @Test
    @SuppressWarnings("unchecked")
    void withTrashUpdateOrCreateCreatesWhenNotFound() {
        // 未命中：应 newRecord + getEntity(condition) + fillEntity(complement) + save
        doReturn(builderReturning(null)).when(spyModel).withTrashed();
        Record<TrashUser, Long> created = mock(Record.class);
        doReturn(created).when(spyModel).newRecord();

        Record<TrashUser, Long> result = spyModel.withTrash().updateOrCreate(condition(), complement());

        assertSame(created, result, "未命中时应返回新建记录");
        verify(spyModel).newRecord();
        verify(created).getEntity(any(TrashUser.class));
        verify(created).fillEntity(any(TrashUser.class));
        verify(created).save();
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyTrashUsesOnlyTrashedScope() {
        // onlyTrash() 应使用 onlyTrashed() 作用域
        Record<TrashUser, Long> hit = mock(Record.class);
        doReturn(builderReturning(hit)).when(spyModel).onlyTrashed();

        Record<TrashUser, Long> result = spyModel.onlyTrash().first(condition());

        assertSame(hit, result);
        verify(spyModel).onlyTrashed();
        verify(spyModel, never()).withTrashed();
        verify(spyModel, never()).newQuery();
    }

    @Test
    @SuppressWarnings("unchecked")
    void withoutTrashUsesDefaultNewQueryScope() {
        // withoutTrash() 应使用默认 newQuery() 作用域（仅未删除）
        doReturn(builderReturning(null)).when(spyModel).newQuery();

        Record<TrashUser, Long> result = spyModel.withoutTrash().first(condition());

        assertSame(null, result);
        verify(spyModel).newQuery();
        verify(spyModel, never()).withTrashed();
        verify(spyModel, never()).onlyTrashed();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOrCreateSavesOnlyWhenNotBound() {
        // findOrNew 命中已存在记录（isHasBind=true）时，findOrCreate 不应再 save
        Record<TrashUser, Long> hit = mock(Record.class);
        when(hit.isHasBind()).thenReturn(true);
        doReturn(builderReturning(hit)).when(spyModel).withTrashed();

        Record<TrashUser, Long> result = spyModel.withTrash().findOrCreate(condition());

        assertSame(hit, result);
        assertTrue(hit.isHasBind());
        verify(hit, never()).save();

        // 未命中：findOrNew 返回未绑定的新记录，findOrCreate 应 save
        doReturn(builderReturning(null)).when(spyModel).withTrashed();
        Record<TrashUser, Long> fresh = mock(Record.class);
        when(fresh.isHasBind()).thenReturn(false);
        doReturn(fresh).when(spyModel).newRecord();

        Record<TrashUser, Long> created = spyModel.withTrash().findOrCreate(condition());
        assertSame(fresh, created);
        assertFalse(fresh.isHasBind());
        verify(fresh).save();
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryReturnsScopedBuilder() {
        // query() 应返回对应作用域的 Builder（withTrash -> withTrashed）
        QueryBuilder<TrashUser, Long> b = builderReturning(null);
        doReturn(b).when(spyModel).withTrashed();

        assertSame(b, spyModel.withTrash().query());
        verify(spyModel, times(1)).withTrashed();
    }
}
