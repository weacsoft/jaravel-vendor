package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.event.Event;
import com.weacsoft.jaravel.vendor.event.Listener;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatabaseQueueDispatcher} 任务分发单元测试（使用伪造驱动，不依赖真实数据库）。
 */
class DatabaseQueueDispatcherTest {

    /** 记录推送内容的伪造驱动 */
    static class FakeDriver implements QueueDriver {
        final List<String> pushed = new ArrayList<>();
        long nextId = 1;

        @Override
        public long push(String queueName, String payload) {
            pushed.add(queueName + "|" + payload);
            return nextId++;
        }

        @Override
        public long push(String queueName, String payload, long delayMs) {
            pushed.add(queueName + "|" + delayMs + "|" + payload);
            return nextId++;
        }

        @Override
        public QueuedJob pop(String queueName) { return null; }

        @Override
        public void delete(long jobId) { }

        @Override
        public void release(long jobId) { }

        @Override
        public void release(long jobId, long delayMs) { }

        @Override
        public int size(String queueName) { return 0; }

        @Override
        public void clear(String queueName) { }

        @Override
        public void fail(long jobId, String queue, String payload, int attempts, String exception) { }

        @Override
        public List<QueuedJob> getFailedJobs() { return List.of(); }

        @Override
        public void retryFailedJob(long failedJobId) { }

        @Override
        public void deleteFailedJob(long failedJobId) { }

        @Override
        public void clearFailedJobs() { }
    }

    /** 测试事件 */
    static class UserRegistered implements Event {
        public final Long userId;

        public UserRegistered(Long userId) {
            this.userId = userId;
        }

        public Long getUserId() {
            return userId;
        }
    }

    /** 测试监听器 */
    static class UserRegisteredListener implements Listener<UserRegistered> {
        @Override
        public void handle(UserRegistered event) {
        }
    }

    @Test
    void isAvailableReflectsDriverPresence() {
        FakeDriver driver = new FakeDriver();
        DatabaseQueueDispatcher dispatcher = new DatabaseQueueDispatcher(
                driver, freshCtx());

        assertTrue(dispatcher.isAvailable());
        assertSame(driver, dispatcher.getDriver());

        DatabaseQueueDispatcher empty = new DatabaseQueueDispatcher(
                null, freshCtx());
        assertFalse(empty.isAvailable());
    }

    private static void assertSame(Object expected, Object actual) {
        assertNotNull(actual);
        assertTrue(expected == actual);
    }

    /** 创建并刷新一个空上下文（getBeanNamesForType 需要 context 处于 active 状态） */
    private static GenericApplicationContext freshCtx() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.refresh();
        return ctx;
    }

    @Test
    void dispatchSerializesListenerAndEventAndPushes() {
        FakeDriver driver = new FakeDriver();
        DatabaseQueueDispatcher dispatcher = new DatabaseQueueDispatcher(
                driver, freshCtx());

        dispatcher.dispatch("users", new UserRegisteredListener(), new UserRegistered(42L), 0);

        assertEquals(1, driver.pushed.size());
        String payload = driver.pushed.get(0);
        // payload 结构：queueName|payloadJson
        assertTrue(payload.startsWith("users|"));
        String json = payload.substring("users|".length());
        assertTrue(json.contains("listenerClass"));
        assertTrue(json.contains(UserRegisteredListener.class.getName()));
        assertTrue(json.contains("eventClass"));
        assertTrue(json.contains(UserRegistered.class.getName()));
        assertTrue(json.contains("eventData"));
        assertTrue(json.contains("\"userId\":42"), "事件数据应被序列化");
    }

    @Test
    void dispatchWithDelayIncludesDelayMs() {
        FakeDriver driver = new FakeDriver();
        DatabaseQueueDispatcher dispatcher = new DatabaseQueueDispatcher(
                driver, freshCtx());

        dispatcher.dispatch("users", new UserRegisteredListener(), new UserRegistered(1L), 1000);

        assertEquals(1, driver.pushed.size());
        // 延迟推送记录格式：queueName|delayMs|payloadJson
        String record = driver.pushed.get(0);
        assertTrue(record.startsWith("users|1000|"));
    }
}
