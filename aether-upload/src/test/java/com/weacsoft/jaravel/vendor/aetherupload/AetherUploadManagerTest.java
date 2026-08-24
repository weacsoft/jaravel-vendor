package com.weacsoft.jaravel.vendor.aetherupload;

import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传核心逻辑测试：分片写入、乱序分片、断点续传、类型/大小限制、同步上传。
 */
class AetherUploadManagerTest {

    @TempDir
    Path tempRoot;

    private AetherUploadManager manager;
    private AetherUploadProperties properties;
    private String originalUserDir;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempRoot.toAbsolutePath().toString());

        properties = new AetherUploadProperties();
        properties.setDefaultGroup("file");

        AetherUploadProperties.GroupConfig file = new AetherUploadProperties.GroupConfig();
        file.setChunkSize(1024);
        properties.getGroups().put("file", file);

        AetherUploadProperties.GroupConfig video = new AetherUploadProperties.GroupConfig();
        video.setChunkSize(2048);
        video.setMaxSize(4096);
        video.setAllowedExtensions(Arrays.asList("mp4"));
        video.setTempDir("video-temp");
        video.setSaveDir("video-uploads");
        properties.getGroups().put("video", video);

        manager = new AetherUploadManager(properties, null);
    }

    @AfterEach
    void tearDown() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void 分片上传应完整还原文件内容() throws IOException {
        byte[] content = randomBytes(1024 * 3 + 100); // 4 个分片，末片不足
        UploadResult prepared = manager.prepare("file", "big.bin", content.length,
                "application/octet-stream", "id-1", null);

        assertEquals(4, prepared.totalChunks);
        assertFalse(prepared.completed);

        UploadResult last = null;
        for (int i = 0; i < prepared.totalChunks; i++) {
            last = manager.writeChunk("file", prepared.resourceId, i, chunkOf(content, i, 1024));
        }

        assertNotNull(last);
        assertTrue(last.completed);
        assertEquals(100.0, last.percent);
        assertArrayEquals(content, Files.readAllBytes(Paths.get(last.savedPath)));
    }

    @Test
    void 乱序分片上传应正确写入偏移() throws IOException {
        byte[] content = randomBytes(1024 * 3);
        UploadResult prepared = manager.prepare("file", "shuffle.bin", content.length, null, null, null);

        int[] order = {2, 0, 1};
        UploadResult last = null;
        for (int index : order) {
            last = manager.writeChunk("file", prepared.resourceId, index, chunkOf(content, index, 1024));
        }

        assertNotNull(last);
        assertTrue(last.completed);
        assertArrayEquals(content, Files.readAllBytes(Paths.get(last.savedPath)));
    }

    @Test
    void 相同identifier重新prepare应返回已传分片实现断点续传() {
        byte[] content = randomBytes(1024 * 4);
        UploadResult first = manager.prepare("file", "resume.bin", content.length, null, "same-id", null);
        manager.writeChunk("file", first.resourceId, 0, chunkOf(content, 0, 1024));
        manager.writeChunk("file", first.resourceId, 1, chunkOf(content, 1, 1024));

        // 模拟断线后重新 prepare
        UploadResult resumed = manager.prepare("file", "resume.bin", content.length, null, "same-id", null);
        assertTrue(resumed.resumed);
        assertEquals(first.resourceId, resumed.resourceId);
        assertEquals(2, resumed.uploadedCount);
        assertEquals(Arrays.asList(0, 1), resumed.uploadedChunks);
        assertEquals(50.0, resumed.percent);

        // 续传剩余分片
        manager.writeChunk("file", resumed.resourceId, 2, chunkOf(content, 2, 1024));
        UploadResult done = manager.writeChunk("file", resumed.resourceId, 3, chunkOf(content, 3, 1024));
        assertTrue(done.completed);
    }

    @Test
    void 重复上传同一分片不应重复计数() {
        byte[] content = randomBytes(1024 * 3);
        UploadResult prepared = manager.prepare("file", "dup.bin", content.length, null, null, null);
        manager.writeChunk("file", prepared.resourceId, 0, chunkOf(content, 0, 1024));
        UploadResult again = manager.writeChunk("file", prepared.resourceId, 0, chunkOf(content, 0, 1024));
        assertEquals(1, again.uploadedCount);
    }

    @Test
    void 进度查询应返回后端进度() {
        byte[] content = randomBytes(1024 * 4);
        UploadResult prepared = manager.prepare("file", "p.bin", content.length, null, null, null);
        manager.writeChunk("file", prepared.resourceId, 0, chunkOf(content, 0, 1024));

        UploadResult progress = manager.progress("file", prepared.resourceId);
        assertEquals(25.0, progress.percent);
        assertEquals(Arrays.asList(0), progress.uploadedChunks);
    }

    @Test
    void 超过大小限制应拒绝() {
        UploadException e = assertThrows(UploadException.class,
                () -> manager.prepare("video", "a.mp4", 99999, "video/mp4", null, null));
        assertEquals("size_exceeded", e.getCode());
    }

    @Test
    void 扩展名不允许应拒绝() {
        UploadException e = assertThrows(UploadException.class,
                () -> manager.prepare("video", "a.exe", 1024, null, null, null));
        assertEquals("type_not_allowed", e.getCode());
    }

    @Test
    void 分片大小不符应拒绝() {
        byte[] content = randomBytes(1024 * 2);
        UploadResult prepared = manager.prepare("file", "bad.bin", content.length, null, null, null);
        UploadException e = assertThrows(UploadException.class,
                () -> manager.writeChunk("file", prepared.resourceId, 0, new byte[10]));
        assertEquals("invalid", e.getCode());
    }

    @Test
    void 分片索引越界应拒绝() {
        UploadResult prepared = manager.prepare("file", "oob.bin", 1024, null, null, null);
        UploadException e = assertThrows(UploadException.class,
                () -> manager.writeChunk("file", prepared.resourceId, 5, new byte[1024]));
        assertEquals("invalid", e.getCode());
    }

    @Test
    void 前端自定义分片大小应生效() {
        UploadResult prepared = manager.prepare("file", "c.bin", 4096, null, null, 4096L);
        assertEquals(4096, prepared.chunkSize);
        assertEquals(1, prepared.totalChunks);
    }

    @Test
    void 组禁止前端分片大小时应使用后端配置() {
        properties.getGroups().get("file").setAllowClientChunkSize(false);
        AetherUploadManager m = new AetherUploadManager(properties, null);
        UploadResult prepared = m.prepare("file", "c.bin", 4096, null, null, 4096L);
        assertEquals(1024, prepared.chunkSize);
        assertEquals(4, prepared.totalChunks);
    }

    @Test
    void 同步上传应直接落盘并标记完成() throws IOException {
        byte[] content = randomBytes(500);
        UploadResult result = manager.uploadSync("file", "s.txt", "text/plain", content);
        assertTrue(result.completed);
        assertArrayEquals(content, Files.readAllBytes(Paths.get(result.savedPath)));
    }

    @Test
    void 中止上传应删除临时文件() {
        UploadResult prepared = manager.prepare("file", "abort.bin", 2048, null, "abort-id", null);
        Path temp = Paths.get(prepared.resourceId + ".part");
        manager.abort("file", prepared.resourceId);
        assertThrows(UploadException.class, () -> manager.progress("file", prepared.resourceId));
        assertFalse(Files.exists(temp));
    }

    @Test
    void 未知组应抛出异常() {
        UploadException e = assertThrows(UploadException.class,
                () -> manager.prepare("nope", "a.bin", 10, null, null, null));
        assertEquals("group_not_found", e.getCode());
    }

    @Test
    void 组配置应对外暴露供前端限制() {
        assertEquals(2048L, manager.groupClientConfig("video").get("chunkSize"));
        assertEquals(4096L, manager.groupClientConfig("video").get("maxSize"));
        assertEquals(1024L, manager.groupClientConfig("file").get("chunkSize"));
    }

    @Test
    void 文件名应过滤目录穿越() {
        assertEquals("evil.txt", AetherUploadManager.sanitizeFilename("../../etc/evil.txt"));
        assertEquals("evil.txt", AetherUploadManager.sanitizeFilename("C:\\windows\\evil.txt"));
        assertEquals("unnamed", AetherUploadManager.sanitizeFilename(null));
    }

    // ---- helpers ----

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        return data;
    }

    private static byte[] chunkOf(byte[] content, int index, int chunkSize) {
        int start = index * chunkSize;
        int end = Math.min(start + chunkSize, content.length);
        return Arrays.copyOfRange(content, start, end);
    }
}
