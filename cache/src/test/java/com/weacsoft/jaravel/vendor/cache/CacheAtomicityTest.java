package com.weacsoft.jaravel.vendor.cache;

import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriver;
import com.weacsoft.jaravel.vendor.cache.store.DefaultCacheStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存原语的<b>并发原子性</b>回归测试（`AtomicCacheDriver` 能力）。
 * <p>
 * 这些语义此前都是「两步实现」，并发下会静默失效：
 * <ul>
 *   <li>{@code add} 是 has→put：多线程可同时成功 → 「单次执行/互斥门闩」失效；</li>
 *   <li>{@code pull} 是 get→forget：一次性令牌可被两个线程同时取到（captcha 消费方依赖它）；</li>
 *   <li>{@code increment} 是读改写：丢失更新 → 计数偏小、限流判断失真。</li>
 * </ul>
 * 内存驱动（{@code ConcurrentHashMap.compute}/{@code remove}）与 Redis 驱动
 * （{@code SET NX} / Lua {@code GET+DEL} / {@code INCRBY}）现在都实现了原子路径。
 */
class CacheAtomicityTest {

    private static final int THREADS = 16;

    private static DefaultCacheStore store() {
        return new DefaultCacheStore(new ArrayCacheDriver(), "t");
    }

    /** 并发执行若干任务并等待全部结束 */
    private static void runConcurrently(int threads, java.util.function.IntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        task.accept(index);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发任务应在超时前完成");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentAddHasExactlyOneWinner() throws Exception {
        DefaultCacheStore store = store();
        AtomicInteger winners = new AtomicInteger();
        runConcurrently(THREADS, i -> {
            if (store.add("once-only", "v" + i, 60)) {
                winners.incrementAndGet();
            }
        });
        assertEquals(1, winners.get(), "并发 add 只能有一个成功（互斥门闩语义）");
    }

    @Test
    void concurrentPullHandsValueToExactlyOneThread() throws Exception {
        DefaultCacheStore store = store();
        store.put("one-shot-token", "secret", 60);
        AtomicInteger receivers = new AtomicInteger();
        runConcurrently(THREADS, i -> {
            if (store.pull("one-shot-token") != null) {
                receivers.incrementAndGet();
            }
        });
        assertEquals(1, receivers.get(), "一次性令牌只能被一个线程取到");
    }

    @Test
    void concurrentIncrementDoesNotLoseUpdates() throws Exception {
        DefaultCacheStore store = store();
        int perThread = 100;
        runConcurrently(THREADS, i -> {
            for (int n = 0; n < perThread; n++) {
                store.increment("counter");
            }
        });
        assertEquals((long) THREADS * perThread, store.get("counter"),
                "并发自增不得丢失更新（原读改写实现会偏小）");
    }

    @Test
    void concurrentDecrementDoesNotLoseUpdates() throws Exception {
        DefaultCacheStore store = store();
        store.put("quota", 1000L, 0);
        int perThread = 50;
        runConcurrently(THREADS, i -> {
            for (int n = 0; n < perThread; n++) {
                store.decrement("quota");
            }
        });
        assertEquals(1000L - (long) THREADS * perThread, store.get("quota"));
    }

    @Test
    void atomicIncrementStillPreservesTtl() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        DefaultCacheStore store = new DefaultCacheStore(driver, "t");
        store.put("throttle", 0L, 30);

        store.increment("throttle");

        java.util.OptionalLong ttl = driver.remainingTtlSeconds("t:throttle");
        assertTrue(ttl.isPresent() && ttl.getAsLong() > 0 && ttl.getAsLong() <= 30,
                "原子自增同样必须保留 TTL，实际=" + ttl.orElse(-1));
    }

    @Test
    void atomicPrimitivesAreExposedByArrayDriver() {
        ArrayCacheDriver driver = new ArrayCacheDriver();
        assertNotNull(driver);
        assertTrue(driver.addIfAbsent("k", "v", 10), "首次写入应成功");
        assertEquals(false, driver.addIfAbsent("k", "v2", 10), "重复写入应失败");
        assertEquals("v", driver.pullValue("k"), "应原子取走原值");
        assertEquals(null, driver.pullValue("k"), "取走后不应再拿到");
        assertEquals(5L, driver.incrementAndGet("n", 5L, 0L));
        assertEquals(2L, driver.incrementAndGet("n", -3L, 0L));
    }
}