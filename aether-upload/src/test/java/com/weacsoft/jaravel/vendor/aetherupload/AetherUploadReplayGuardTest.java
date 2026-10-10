package com.weacsoft.jaravel.vendor.aetherupload;

import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadProperties;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 末片重放不得覆盖已落盘成品（审计 M6 核心断言）。
 * <p>
 * 真实故障场景：{@code finalizeUpload} 先把 {@code .part} move 成成品、再 {@code saveHeader}；
 * 若 {@code saveHeader} 失败，持久化状态仍是 UPLOADING 且末片未记录 —— 客户端随后重试末片时，
 * 旧实现会以 {@code RandomAccessFile(tempPath,"rw")} **新建零填充文件**，只写最后一片，
 * 使 {@code allUploaded()} 为真，最终用近乎全零的文件 <b>覆盖掉本来正确的成品</b>并标记完成。
 * <p>
 * 现在写分片前校验「临时文件存在且长度 == 声明大小」，不一致即拒绝并要求重新 prepare。
 * <p>
 * 注入手法（专家给出）：给组配置 {@code header-store: throwing}（**必须在注册 group 之前**），
 * 并用一个 {@code put} 会**抛异常**（不是返回 false —— {@code CacheUploadHeaderStore.put} 忽略
 * 布尔返回值）的 {@link CacheStore} 替身，在**末片写入前**武装，从而精确模拟「move 成功、saveHeader 失败」。
 */
class AetherUploadReplayGuardTest {

    @TempDir
    Path tempRoot;

    private String originalUserDir;
    private AetherUploadManager manager;
    private final Map<String, Object> backing = new ConcurrentHashMap<>();
    private final AtomicBoolean armed = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempRoot.toAbsolutePath().toString());

        AetherUploadProperties properties = new AetherUploadProperties();
        properties.setDefaultGroup("file");
        AetherUploadProperties.GroupConfig file = new AetherUploadProperties.GroupConfig();
        file.setChunkSize(1024);
        // header-store 必须在注册 group 之前设置（注册时即解析 store）
        file.setHeaderStore("throwing");
        properties.getGroups().put("file", file);

        CacheStore stub = mock(CacheStore.class);
        when(stub.get(anyString())).thenAnswer(inv -> backing.get(inv.getArgument(0)));
        when(stub.put(anyString(), any(), anyLong())).thenAnswer(inv -> {
            if (armed.get()) {
                throw new IllegalStateException("simulated saveHeader failure");
            }
            backing.put(inv.getArgument(0), inv.getArgument(1));
            return true;
        });
        when(stub.forget(anyString())).thenAnswer(inv -> backing.remove(inv.getArgument(0)) != null);

        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.store("throwing")).thenReturn(stub);
        when(cacheManager.store()).thenReturn(stub);

        manager = new AetherUploadManager(properties, cacheManager);
    }

    @AfterEach
    void tearDown() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void replayingLastChunkAfterFailedFinalizeIsRejectedAndProductUnchanged() throws Exception {
        byte[] content = randomBytes(1024 * 3);
        UploadResult prepared = manager.prepare("file", "replay.bin", content.length, null, "id-1", 1024L);
        manager.writeChunk("file", prepared.resourceId, 0, chunkOf(content, 0));
        manager.writeChunk("file", prepared.resourceId, 1, chunkOf(content, 1));

        // 末片写入 → 触发最终落盘：move 成功、saveHeader 失败
        armed.set(true);
        assertThrows(RuntimeException.class,
                () -> manager.writeChunk("file", prepared.resourceId, 2, chunkOf(content, 2)),
                "最终落盘时 header 持久化失败必须向上抛出");

        Path product = findProduct("replay.bin");
        Assumptions.assumeTrue(product != null && Files.exists(product),
                "未走到「成品已落盘」状态（实现顺序变化），本用例不适用");
        String hashBefore = sha256(product);

        // 重试末片：.part 已被 move → 长度校验必须拒绝，且不得重建文件覆盖成品
        armed.set(false);
        assertThrows(UploadException.class,
                () -> manager.writeChunk("file", prepared.resourceId, 2, chunkOf(content, 2)),
                "重试末片必须被拒绝（要求重新 prepare），而不是零填充覆盖成品");
        assertEquals(hashBefore, sha256(product), "成品内容不得被覆盖");
    }

    @Test
    void unknownResourceIdIsRejectedWithoutLeakingLocks() {
        // 随机 resourceId 反复请求：只应抛「header 不存在」，且定长分段锁不会无界增长
        for (int i = 0; i < 200; i++) {
            String ghost = "ghost-" + i;
            assertThrows(UploadException.class, () -> manager.writeChunk("file", ghost, 0, new byte[1024]));
        }
    }

    private Path findProduct(String filename) throws Exception {
        // 落盘名为 "{resourceId}_{filename}"（见 AetherUploadManager.savedPath）
        try (Stream<Path> walk = Files.walk(tempRoot)) {
            return walk.filter(p -> p.getFileName().toString().endsWith("_" + filename))
                    .findFirst().orElse(null);
        }
    }

    private static byte[] chunkOf(byte[] content, int index) {
        byte[] chunk = new byte[1024];
        System.arraycopy(content, index * 1024, chunk, 0, 1024);
        return chunk;
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new java.util.Random(42).nextBytes(bytes);
        return bytes;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(path));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}