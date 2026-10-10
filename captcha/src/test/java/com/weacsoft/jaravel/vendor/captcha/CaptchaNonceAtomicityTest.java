package com.weacsoft.jaravel.vendor.captcha;

import com.weacsoft.jaravel.vendor.captcha.generator.NumberCaptcha;
import com.weacsoft.jaravel.vendor.captcha.store.CaptchaStore;
import com.weacsoft.jaravel.vendor.captcha.store.MemoryCaptchaStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证码「一次性」保证的并发回归测试（审计 M5）。
 * <p>
 * 修复前 {@code AbstractCaptcha} 用「先查 {@code isNonceConsumed} 再 {@code consumeNonce}」判断复用，
 * 并发下两个请求可同时通过检查 → 同一验证码可被并行验证两次（一次性保证失效）。
 * 现在改为<b>先原子占用</b>（{@link CaptchaStore#putIfAbsent}）再比对，占用失败即「已被使用」。
 */
class CaptchaNonceAtomicityTest {

    /** none 模式：本测试只关心 nonce 一次性语义，与输入加密无关 */
    private static CaptchaProperties plainProps() {
        CaptchaProperties props = CaptchaProperties.createDefault();
        props.setEncryptionType("none");
        return props;
    }

    @Test
    void memoryStorePutIfAbsentIsAtomic() throws Exception {
        MemoryCaptchaStore store = new MemoryCaptchaStore();
        int threads = 16;
        AtomicInteger winners = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        if (store.putIfAbsent("consumed:n1", "1", 60)) {
                            winners.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, winners.get(), "并发占用同一个键只能有一个成功");
    }

    @Test
    void concurrentVerificationOfSameCaptchaPassesExactlyOnce() throws Exception {
        CaptchaProperties props = plainProps();
        NumberCaptcha captcha = new NumberCaptcha(props);
        CaptchaResult result = captcha.generate();
        String captchaKey = result.getCaptchaKey();
        String answer = answerOf(props, captchaKey);

        int threads = 16;
        AtomicInteger passed = new AtomicInteger();
        AtomicInteger alreadyUsed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        var verifyResult = captcha.verify(captchaKey, answer, props, null);
                        if (verifyResult.isPassed()) {
                            passed.incrementAndGet();
                        } else if (verifyResult.isAlreadyUsed()) {
                            alreadyUsed.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, passed.get(), "同一验证码并发验证必须只有一次通过（一次性保证）");
        assertEquals(threads - 1, alreadyUsed.get(), "其余请求应得到「已被使用」而不是再次通过");
    }

    @Test
    void failedAttemptAlsoBurnsNonce() {
        CaptchaProperties props = plainProps();
        NumberCaptcha captcha = new NumberCaptcha(props);
        CaptchaResult result = captcha.generate();
        String captchaKey = result.getCaptchaKey();

        assertFalse(captcha.verify(captchaKey, "000000"), "错误答案必须失败");
        // 「失败也烧 nonce」是防暴力尝试的关键：第二次即使答案正确也必须被拒
        var second = captcha.verify(captchaKey, answerOf(props, captchaKey), props, null);
        assertTrue(second.isAlreadyUsed(), "失败尝试后 nonce 应已被占用: " + second);
    }

    /** 白盒助手：用服务端密钥解出答案 */
    private static String answerOf(CaptchaProperties props, String captchaKey) {
        var server = com.weacsoft.jaravel.vendor.captcha.crypto.CaptchaCrypto.create(
                props.getEncryptionType(), props.getEncryptionKey());
        String[] parts = server.decrypt(captchaKey).split("\\|", 4);
        return parts.length == 4 ? parts[3] : parts[2];
    }
}