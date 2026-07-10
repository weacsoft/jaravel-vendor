package com.weacsoft.jaravel.vendor.captcha;

import com.weacsoft.jaravel.vendor.captcha.store.MemoryCaptchaStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MemoryCaptchaStore} 单元测试。
 */
class MemoryCaptchaStoreTest {

    @Test
    void testPutAndGet() {
        MemoryCaptchaStore store = new MemoryCaptchaStore();
        store.put("k1", "answer1", 60);
        assertEquals("answer1", store.get("k1"));
        // get 不删除，可重复读取
        assertEquals("answer1", store.get("k1"));
    }

    @Test
    void testPull() {
        MemoryCaptchaStore store = new MemoryCaptchaStore();
        store.put("k2", "answer2", 60);
        assertEquals("answer2", store.pull("k2"));
        // pull 读取后即删除
        assertNull(store.pull("k2"));
        assertNull(store.get("k2"));
    }

    @Test
    void testExpiredGet() throws InterruptedException {
        MemoryCaptchaStore store = new MemoryCaptchaStore();
        store.put("k3", "answer3", 1);
        // 等待超过 TTL
        Thread.sleep(1100);
        assertNull(store.get("k3"));
    }

    @Test
    void testRemove() {
        MemoryCaptchaStore store = new MemoryCaptchaStore();
        store.put("k4", "answer4", 60);
        store.remove("k4");
        assertNull(store.get("k4"));
        assertNull(store.pull("k4"));
    }
}
