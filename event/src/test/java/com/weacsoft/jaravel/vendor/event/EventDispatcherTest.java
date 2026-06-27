package com.weacsoft.jaravel.vendor.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EventDispatcher} 事件调度器测试。
 * <p>
 * 覆盖监听器注册、事件同步分发、多监听器、getListeners、清理，
 * 以及 {@link ShouldQueue} 异步队列分发。
 */
class EventDispatcherTest {

    private QueueManager queueManager;
    private EventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        queueManager = new QueueManager(new EventProperties());
        dispatcher = new EventDispatcher(queueManager);
    }

    @AfterEach
    void tearDown() {
        dispatcher.clearAllListeners();
        queueManager.shutdown();
    }

    /** 简单测试事件 */
    static class LoginEvent implements Event {
        final String username;
        LoginEvent(String username) { this.username = username; }
    }

    @Test
    void testRegisterAndDispatchSynchronously() {
        AtomicReference<String> received = new AtomicReference<>();
        dispatcher.listen(LoginEvent.class, (Listener<LoginEvent>) event -> received.set(event.username));

        dispatcher.dispatch(new LoginEvent("alice"));

        assertEquals("alice", received.get(), "同步监听器应在 dispatch 返回前执行");
    }

    @Test
    void testMultipleListenersAllInvoked() {
        AtomicInteger counter = new AtomicInteger();
        dispatcher.listen(LoginEvent.class, (Listener<LoginEvent>) event -> counter.incrementAndGet());
        dispatcher.listen(LoginEvent.class, (Listener<LoginEvent>) event -> counter.addAndGet(10));

        dispatcher.dispatch(new LoginEvent("bob"));

        assertEquals(11, counter.get(), "同一事件的多个监听器都应被触发");
    }

    @Test
    void testGetListenersAndClear() {
        Listener<LoginEvent> l1 = event -> {};
        Listener<LoginEvent> l2 = event -> {};
        dispatcher.listen(LoginEvent.class, l1);
        dispatcher.listen(LoginEvent.class, l2);

        List<Listener<LoginEvent>> listeners = dispatcher.getListeners(LoginEvent.class);
        assertEquals(2, listeners.size());

        // 未注册的事件类型返回空列表
        assertTrue(dispatcher.getListeners(Event.class).isEmpty());

        dispatcher.clearListeners(LoginEvent.class);
        assertTrue(dispatcher.getListeners(LoginEvent.class).isEmpty());
    }

    @Test
    void testClearAllListeners() {
        dispatcher.listen(LoginEvent.class, event -> {});
        dispatcher.listen(LoginEvent.class, event -> {});
        assertEquals(2, dispatcher.getListeners(LoginEvent.class).size());

        dispatcher.clearAllListeners();
        assertTrue(dispatcher.getListeners(LoginEvent.class).isEmpty());
    }

    @Test
    void testDispatchWithNoListenersIsNoop() {
        // 无监听器时分发不应抛异常
        assertDoesNotThrow(() -> dispatcher.dispatch(new LoginEvent("nobody")));
    }

    /**
     * 验证实现 {@link ShouldQueue} 的监听器被异步执行：
     * dispatch 立即返回，监听器在后台线程执行。
     */
    @Test
    void testShouldQueueListenerExecutedAsynchronously() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        dispatcher.listen(LoginEvent.class, new AsyncLoginListener(latch, threadName));

        long start = System.currentTimeMillis();
        dispatcher.dispatch(new LoginEvent("async-user"));
        long elapsed = System.currentTimeMillis() - start;

        // dispatch 应立即返回（异步提交，不等监听器执行完）
        assertTrue(elapsed < 1000, "ShouldQueue 监听器应异步提交，dispatch 不应阻塞");

        // 等待异步监听器执行完毕
        assertTrue(latch.await(5, TimeUnit.SECONDS), "异步监听器应在 5 秒内执行");
        assertEquals("async-user", ((LoginEvent) AsyncLoginListener.lastEvent.get()).username);
        assertNotNull(threadName.get());
        // 执行线程应是队列工作线程（守护线程，名称以 jaravel-event 开头）
        assertTrue(threadName.get().startsWith("jaravel-event"),
                "应在队列线程池中执行，实际线程: " + threadName.get());
    }

    /** 异步登录监听器，实现 ShouldQueue 标记为队列化执行 */
    static class AsyncLoginListener implements Listener<LoginEvent>, ShouldQueue {
        private final CountDownLatch latch;
        private final AtomicReference<String> threadName;
        static final AtomicReference<Object> lastEvent = new AtomicReference<>();

        AsyncLoginListener(CountDownLatch latch, AtomicReference<String> threadName) {
            this.latch = latch;
            this.threadName = threadName;
        }

        @Override
        public void handle(LoginEvent event) {
            threadName.set(Thread.currentThread().getName());
            lastEvent.set(event);
            latch.countDown();
        }
    }
}
