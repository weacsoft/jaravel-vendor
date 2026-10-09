package com.weacsoft.jaravel.vendor.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SessionStoreHolder} 回归测试。
 * <p>
 * 重点是 <b>rotate 转发</b>：holder 是守卫实际持有的对象，若它不覆盖 {@code rotate()}，
 * 接口的默认空实现就会生效 —— 表现为「登录时的会话固定防护被静默跳过」，
 * 而且守卫侧因为「holder 覆盖了 rotate」不再告警，风险被完全掩盖。
 */
class SessionStoreHolderTest {

    /** 记录调用顺序的假实现 */
    static class RecordingStore implements SessionStore {
        final List<String> calls = new ArrayList<>();

        @Override
        public Object get(String key) { calls.add("get"); return null; }

        @Override
        public void put(String key, Object value) { calls.add("put"); }

        @Override
        public void remove(String key) { calls.add("remove"); }

        @Override
        public void destroy() { calls.add("destroy"); }

        @Override
        public void rotate() { calls.add("rotate"); }
    }

    @Test
    void delegatesEveryOperationIncludingRotate() {
        SessionStoreHolder holder = new SessionStoreHolder();
        RecordingStore store = new RecordingStore();
        holder.set(store);

        holder.get("k");
        holder.put("k", "v");
        holder.remove("k");
        holder.rotate();
        holder.destroy();

        assertEquals(List.of("get", "put", "remove", "rotate", "destroy"), store.calls,
                "holder 必须转发全部操作，尤其是 rotate（否则会话固定防护被默认空实现吞掉）");
    }

    @Test
    void fallsBackToCookieStoreWhenNothingRegistered() {
        SessionStoreHolder holder = new SessionStoreHolder();
        assertFalse(holder.isPresent(), "未注册实现时应为未设置状态");

        SessionStore resolved = holder.get();

        assertNotNull(resolved);
        assertTrue(holder.isPresent(), "首次 get 后应记住惰性回退结果");
        assertInstanceOf(CookieSessionStore.class, resolved, "未注册时应回退到 CookieSessionStore");
    }

    @Test
    void registeredImplementationOverridesFallback() {
        SessionStoreHolder holder = new SessionStoreHolder();
        RecordingStore store = new RecordingStore();
        holder.set(store);

        holder.put("k", "v");

        assertEquals(List.of("put"), store.calls, "已注册实现时应直接委托，不走回退");
    }
}