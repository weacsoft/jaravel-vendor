package com.weacsoft.jaravel.vendor.database;

import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import gaarason.database.contract.eloquent.Model;
import gaarason.database.contract.eloquent.Record;
import gaarason.database.query.QueryBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EloquentUserProvider} 用户提供者单元测试（gaarason Model 链以 Mockito 模拟）。
 */
class EloquentUserProviderTest {

    /** 测试用户实体 */
    static class TestUser implements Authenticatable {
        private final Long id;

        TestUser(Long id) {
            this.id = id;
        }

        @Override
        public Object getAuthIdentifier() {
            return id;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private EloquentUserProvider<TestUser, Long> provider(Model model, String field) {
        return new EloquentUserProvider<>(model, field);
    }

    @Test
    void retrieveByCredentialsMissingCredentialReturnsNullWithoutQuerying() {
        Model model = mock(Model.class);
        EloquentUserProvider<TestUser, Long> p = provider(model, "number");

        // 凭证中不包含配置的字段 -> 直接返回 null，不触发查询
        assertNull(p.retrieveByCredentials(Map.of("other", "x")));
        verify(model, never()).newQuery();
    }

    @Test
    void retrieveByIdReturnsNullWhenRecordMissing() {
        Model model = mock(Model.class);
        when(model.find(any())).thenReturn(null);

        assertNull(provider(model, "number").retrieveById(1L));
    }

    @Test
    void retrieveByIdReturnsUserWhenRecordFound() {
        Model model = mock(Model.class);
        Record record = mock(Record.class);
        TestUser user = new TestUser(1L);

        when(model.find(any())).thenReturn(record);
        when(record.toObject()).thenReturn(user);

        assertSame(user, provider(model, "number").retrieveById(1L));
    }

    @Test
    void retrieveByCredentialsReturnsUserViaQueryChain() {
        Model model = mock(Model.class);
        QueryBuilder qb = mock(QueryBuilder.class);
        Record record = mock(Record.class);
        TestUser user = new TestUser(7L);

        when(model.newQuery()).thenReturn(qb);
        when(qb.where(anyString(), any())).thenReturn(qb);
        when(qb.first()).thenReturn(record);
        when(record.toObject()).thenReturn(user);

        assertSame(user, provider(model, "number")
                .retrieveByCredentials(Map.of("number", "1001")));
    }

    @Test
    void retrieveByCredentialsReturnsNullWhenQueryFindsNothing() {
        Model model = mock(Model.class);
        QueryBuilder qb = mock(QueryBuilder.class);

        when(model.newQuery()).thenReturn(qb);
        when(qb.where(anyString(), any())).thenReturn(qb);
        when(qb.first()).thenReturn(null);

        assertNull(provider(model, "number")
                .retrieveByCredentials(Map.of("number", "9999")));
    }

    @Test
    void retrieveByCredentialsUsesConfiguredFieldNotHardcoded() {
        Model model = mock(Model.class);
        QueryBuilder qb = mock(QueryBuilder.class);
        Record record = mock(Record.class);
        TestUser user = new TestUser(2L);

        when(model.newQuery()).thenReturn(qb);
        when(qb.where(anyString(), any())).thenReturn(qb);
        when(qb.first()).thenReturn(record);
        when(record.toObject()).thenReturn(user);

        // 配置凭证字段为 email，验证不写死 "number"
        assertSame(user, provider(model, "email")
                .retrieveByCredentials(Map.of("email", "a@b.com")));
    }
}
