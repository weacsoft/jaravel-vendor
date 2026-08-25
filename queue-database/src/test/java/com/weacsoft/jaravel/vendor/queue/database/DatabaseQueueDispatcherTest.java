package com.weacsoft.jaravel.vendor.queue.database;

import com.weacsoft.jaravel.vendor.core.queue.QueueDriver;
import com.weacsoft.jaravel.vendor.core.queue.QueuedJob;


import com.weacsoft.jaravel.vendor.core.lookup.BeanLookup;
import com.weacsoft.jaravel.vendor.event.Event;
import com.weacsoft.jaravel.vendor.event.Listener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatabaseQueueDispatcher} 任务分发单元测试（使用伪造驱动，不依赖真实数据库）。
 * <p>
 * D3 起监听器 bean 解析经 {@link BeanLookup} SPI（零 Spring），测试用空 Map 版适配器。
 */
class DatabaseQueueDispatcherTest {

    /** 空容器版 {@link BeanLookup} 适配器（本测试断言不要求 bean 名存在） */
    private static final class EmptyLookup implements BeanLookup {
        @Override
        public Object bean(Class<?> type) {
            throw new IllegalStateException("测试空容器无此 Bean: " + type);
        }

        @Override
        public Object bean(String name) {
            throw new IllegalStateException("测试空容器无此 Bean: " + name);
        }

        @Override
        public Object bean(String name, Class<?> type) {
            throw new IllegalStateException("测试空容器无此 Bean: " + name);
        }

        @Override
        public boolean contains(String name) {
            return false;
        }

        @Override
        public List<String> beanNames() {
            return List.of();
        }
    }

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
                driver, new EmptyLookup());

        assertTrue(dispatcher.isAvailable());
        assertSame(driver, dispatcher.getDriver());

        DatabaseQueueDispatcher empty = new DatabaseQueueDispatcher(
                null, new EmptyLookup());
        assertFalse(empty.isAvailable());
    }

    private static void assertSame(Object expected, Object actual) {
        assertNotNull(actual);
        assertTrue(expected == actual);
    }

    @Test
    void dispatchSerializesListenerAndEventAndPushes() {
        FakeDriver driver = new FakeDriver();
        DatabaseQueueDispatcher dispatcher = new DatabaseQueueDispatcher(
                driver, new EmptyLookup());

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
                driver, new EmptyLookup());

        dispatcher.dispatch("users", new UserRegisteredListener(), new UserRegistered(1L), 1000);

        assertEquals(1, driver.pushed.size());
        // 延迟推送记录格式：queueName|delayMs|payloadJson
        String record = driver.pushed.get(0);
        assertTrue(record.startsWith("users|1000|"));
    }
}
