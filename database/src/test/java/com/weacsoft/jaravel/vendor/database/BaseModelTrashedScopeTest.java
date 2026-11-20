package com.weacsoft.jaravel.vendor.database;

import com.weacsoft.jaravel.vendor.core.lookup.GlobalLookup;
import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import gaarason.database.contract.eloquent.Record;
import gaarason.database.query.QueryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BaseModel.TrashedScope} 软删除作用域与委托测试。
 * <p>
 * <b>定位变化</b>：gaarason 7.1.2 起 {@code Builder} 自带 {@code ModelOp}
 * （{@code updateOrCreate} / {@code findOrNew} / {@code findOrCreate} …），
 * 因此 {@code TrashedScope} 不再<b>手写复刻</b>这些逻辑，而是：
 * <ol>
 *   <li>按 {@code withTrash()} / {@code onlyTrash()} / {@code withoutTrash()} 选出正确作用域的 builder；</li>
 *   <li>把方法调用<b>直接转交</b>给该 builder 上的官方实现。</li>
 * </ol>
 * 于是本测试只需要锁定两件事：
 * <ul>
 *   <li><b>作用域正确</b>：{@code withTrash()} 必须走 {@code withTrashed()}，
 *       {@code onlyTrash()} 必须走 {@code onlyTrashed()}，且都不应落到默认 {@code newQuery()}；</li>
 *   <li><b>确实是委托</b>：调 {@code TrashedScope.updateOrCreate(...)} 时，
 *       builder 上的同名方法被调用且结果被原样返回——不再有自己发查询、自己
 *       {@code newRecord()} / {@code fillEntity()} 的第二套实现。</li>
 * </ul>
 * 旧版本断言的是「内部手工步骤」（{@code newRecord} + {@code getEntity} + {@code fillEntity} +
 * {@code save} 的执行序列）。那些步骤现在由框架内部完成，从外部看已不可见，
 * 断言它们只会把测试绑死在实现细节上，故改为断言委托契约。
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
        // 用 spy 包装模型，使 gaarason 的 withTrashed()/onlyTrashed()/newQuery() 可打桩
        spyModel = spy(new TrashUser());
        doReturn(TrashUser.class).when(spyModel).getEntityClass();

        // 将 spy 注册进 Spring 上下文：部分路径内部通过 SpringContext.bean(getClass()) 取回该实例
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("trashUser", spyModel);
        ctx.refresh();
        GlobalLookup.install(new CtxProvider(ctx));
    }

    @AfterEach
    void tearDown() {
        GlobalLookup.uninstall();
    }

    /**
     * 构造一个 {@link QueryBuilder} mock，用于验证作用域选择与委托。
     * <p>
     * 注意必须 mock {@link QueryBuilder}（而非其父接口 {@code Builder}）：gaarason 的
     * {@code withTrashed()}/{@code onlyTrashed()}/{@code newQuery()} 静态返回类型为
     * {@code QueryBuilder}，BaseModel 内部会隐式转换，mock 父接口会触发 ClassCastException。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private QueryBuilder<TrashUser, Long> scopedBuilder() {
        return mock(QueryBuilder.class);
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

    /**
     * {@code withTrash().updateOrCreate(...)} 必须委托给
     * {@code withTrashed()} 那个 builder 的官方 {@code updateOrCreate}，并原样返回其结果。
     * <p>
     * 这同时覆盖了「作用域正确」与「确实是委托」两点：
     * 若退回手写实现，builder 上的 {@code updateOrCreate} 不会被调用（测试失败）。
     */
    @Test
    @SuppressWarnings("unchecked")
    void withTrashUpdateOrCreateDelegatesToWithTrashedScope() {
        QueryBuilder<TrashUser, Long> builder = scopedBuilder();
        Record<TrashUser, Long> hit = mock(Record.class);
        when(builder.updateOrCreate(any(), any())).thenReturn(hit);
        doReturn(builder).when(spyModel).withTrashed();

        Record<TrashUser, Long> result = spyModel.withTrash().updateOrCreate(condition(), complement());

        assertSame(hit, result, "应原样返回 builder 官方 updateOrCreate 的结果");
        verify(spyModel).withTrashed();                 // 走包含软删除的作用域
        verify(spyModel, never()).newQuery();           // 不应走默认（仅未删除）作用域
        verify(builder, times(1)).updateOrCreate(any(), any()); // 委托给框架实现
        verify(spyModel, never()).newRecord();          // 不再自己新建记录（旧实现的痕迹）
    }

    /** {@code findOrNew} 同样委托，且不再自己 {@code newRecord()}。 */
    @Test
    @SuppressWarnings("unchecked")
    void findOrNewDelegatesToScopedBuilder() {
        QueryBuilder<TrashUser, Long> builder = scopedBuilder();
        Record<TrashUser, Long> hit = mock(Record.class);
        when(builder.findOrNew(any())).thenReturn(hit);
        doReturn(builder).when(spyModel).withTrashed();

        Record<TrashUser, Long> result = spyModel.withTrash().findOrNew(condition());

        assertSame(hit, result);
        verify(builder).findOrNew(any());
        verify(spyModel, never()).newRecord();
    }

    /** {@code findOrCreate} 委托给官方实现（「未命中是否 save」由框架负责）。 */
    @Test
    @SuppressWarnings("unchecked")
    void findOrCreateDelegatesToScopedBuilder() {
        QueryBuilder<TrashUser, Long> builder = scopedBuilder();
        Record<TrashUser, Long> hit = mock(Record.class);
        when(builder.findOrCreate(any())).thenReturn(hit);
        doReturn(builder).when(spyModel).withTrashed();

        Record<TrashUser, Long> result = spyModel.withTrash().findOrCreate(condition());

        assertSame(hit, result);
        verify(builder).findOrCreate(any());
    }

    /** 条件与补充分离的 {@code findOrCreate(condition, complement)} 也委托。 */
    @Test
    @SuppressWarnings("unchecked")
    void findOrCreateWithComplementDelegatesToScopedBuilder() {
        QueryBuilder<TrashUser, Long> builder = scopedBuilder();
        Record<TrashUser, Long> hit = mock(Record.class);
        when(builder.findOrCreate(any(), any())).thenReturn(hit);
        doReturn(builder).when(spyModel).withTrashed();

        Record<TrashUser, Long> result = spyModel.withTrash().findOrCreate(condition(), complement());

        assertSame(hit, result);
        verify(builder).findOrCreate(any(), any());
    }

    /** {@code first(condition)} 在正确作用域上拼接 where 后取首条。 */
    @Test
    @SuppressWarnings("unchecked")
    void firstUsesScopedBuilderWhere() {
        QueryBuilder<TrashUser, Long> builder = scopedBuilder();
        Record<TrashUser, Long> hit = mock(Record.class);
        when(builder.where(any(TrashUser.class))).thenReturn(builder);
        when(builder.first()).thenReturn(hit);
        doReturn(builder).when(spyModel).onlyTrashed();

        Record<TrashUser, Long> result = spyModel.onlyTrash().first(condition());

        assertSame(hit, result);
        verify(spyModel).onlyTrashed();
        verify(spyModel, never()).withTrashed();
        verify(spyModel, never()).newQuery();
    }

    /** {@code onlyTrash()} 必须使用 {@code onlyTrashed()} 作用域。 */
    @Test
    @SuppressWarnings("unchecked")
    void onlyTrashUsesOnlyTrashedScope() {
        QueryBuilder<TrashUser, Long> builder = scopedBuilder();
        doReturn(builder).when(spyModel).onlyTrashed();

        assertSame(builder, spyModel.onlyTrash().query());

        verify(spyModel).onlyTrashed();
        verify(spyModel, never()).withTrashed();
        verify(spyModel, never()).newQuery();
    }

    /** {@code withoutTrash()} 应使用默认 {@code newQuery()} 作用域（仅未删除）。 */
    @Test
    @SuppressWarnings("unchecked")
    void withoutTrashUsesDefaultNewQueryScope() {
        QueryBuilder<TrashUser, Long> builder = scopedBuilder();
        doReturn(builder).when(spyModel).newQuery();

        assertSame(builder, spyModel.withoutTrash().query());

        verify(spyModel).newQuery();
        verify(spyModel, never()).withTrashed();
        verify(spyModel, never()).onlyTrashed();
    }

    /** {@code query()} 返回对应作用域的 Builder。 */
    @Test
    @SuppressWarnings("unchecked")
    void queryReturnsScopedBuilder() {
        QueryBuilder<TrashUser, Long> builder = scopedBuilder();
        doReturn(builder).when(spyModel).withTrashed();

        assertSame(builder, spyModel.withTrash().query());
        verify(spyModel, times(1)).withTrashed();
    }
}
