package com.weacsoft.jaravel.vendor.jblade;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发渲染测试。
 * <p>
 * 模板实例不再跨请求共享（每次渲染新建实例），因此并发渲染同一模板时，
 * 各请求的上下文互不干扰，不应出现 {@link java.util.ConcurrentModificationException}，
 * 也不应出现变量串扰（一个请求的变量泄漏到另一个请求）。
 */
class BladeConcurrentRenderTest {

    /**
     * 并发渲染同一模板：校验无异常、无变量串扰。
     */
    @Test
    void concurrentRenderSameTemplateDoesNotLeakVariables() throws Exception {
        BladeEngine engine = new BladeEngine("templates");
        int threads = 16;
        int roundsPerThread = 20;
        AtomicInteger failures = new AtomicInteger();
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                final int id = t;
                pool.execute(() -> {
                    try {
                        start.await();
                        for (int r = 0; r < roundsPerThread; r++) {
                            Map<String, Object> vars = new HashMap<>();
                            vars.put("name", "user-" + id);
                            vars.put("count", id);
                            String html = engine.render("hello", vars);
                            if (html == null || !html.contains("Hello, user-" + id + "!") || !html.contains(id + " messages")) {
                                failures.incrementAndGet();
                            }
                        }
                    } catch (Throwable e) {
                        firstError.compareAndSet(null, e);
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(120, TimeUnit.SECONDS), "并发渲染未在超时前完成");
        } finally {
            pool.shutdownNow();
        }

        assertNull(firstError.get(), "并发渲染出现异常: " + firstError.get());
        assertEquals(0, failures.get(), "并发渲染出现结果错误");
    }

    /**
     * 并发渲染不同模板：不同模板之间不应互相阻塞或串扰。
     */
    @Test
    void concurrentRenderDifferentTemplates() throws Exception {
        BladeEngine engine = new BladeEngine("templates");
        String[] templates = {"hello", "page", "inherit.child"};
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(templates.length * 8);
        ExecutorService pool = Executors.newFixedThreadPool(8);

        try {
            for (String template : templates) {
                for (int i = 0; i < 8; i++) {
                    pool.execute(() -> {
                        try {
                            Map<String, Object> vars = new HashMap<>();
                            vars.put("name", "n");
                            vars.put("count", 1);
                            engine.render(template, vars);
                        } catch (Throwable e) {
                            firstError.compareAndSet(null, e);
                        } finally {
                            done.countDown();
                        }
                    });
                }
            }
            assertTrue(done.await(120, TimeUnit.SECONDS), "并发渲染未在超时前完成");
        } finally {
            pool.shutdownNow();
        }

        assertNull(firstError.get(), "并发渲染出现异常: " + firstError.get());
    }

    /**
     * 并发局部渲染：Wire 分段渲染同样不应出现上下文竞态。
     */
    @Test
    void concurrentRenderSectionDoesNotLeakVariables() throws Exception {
        BladeEngine engine = new BladeEngine("templates");
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(12);
        ExecutorService pool = Executors.newFixedThreadPool(12);

        try {
            for (int i = 0; i < 12; i++) {
                final int id = i;
                pool.execute(() -> {
                    try {
                        Map<String, Object> vars = new HashMap<>();
                        vars.put("name", "user-" + id);
                        String section = engine.renderSection("inherit.child", "content", vars);
                        if (section == null || !section.contains("Hello user-" + id)) {
                            firstError.compareAndSet(null, new AssertionError("局部渲染结果异常: " + section));
                        }
                    } catch (Throwable e) {
                        firstError.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(120, TimeUnit.SECONDS), "并发局部渲染未在超时前完成");
        } finally {
            pool.shutdownNow();
        }

        assertNull(firstError.get(), "并发局部渲染出现异常: " + firstError.get());
    }
}
