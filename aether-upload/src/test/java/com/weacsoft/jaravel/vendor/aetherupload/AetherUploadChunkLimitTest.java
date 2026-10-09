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
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分片上限 / 溢出 / 位图性能 / 临时文件清理的回归测试（审计 S8 与 M6 相关面）。
 * <p>
 * 背景（已实测的缺陷）：
 * <ul>
 *   <li>{@code uploadedChunkList()} 曾在循环内 {@code hasChunk(i)}，而位图在为空时<b>每次</b>
 *       重新分配 {@code (totalChunks+7)/8} 字节 → {@code totalChunks=1e6} 实测 21.4 秒、1e8 超过 120 秒
 *       未返回（单请求 CPU/GC 长阻塞 DoS）；</li>
 *   <li>{@code totalChunks = (int)((size+chunkSize-1)/chunkSize)} 无溢出检查：{@code size=8e9&chunkSize=1}
 *       得到负数，分片状态机失效、上传永不完成；</li>
 *   <li>客户端 {@code chunkSize} 无区间约束、{@code size} 在校验前就被用于预分配临时文件；</li>
 *   <li>{@code .part} 临时文件没有任何清理，匿名反复 prepare 可无界堆积。</li>
 * </ul>
 */
class AetherUploadChunkLimitTest {

    @TempDir
    Path tempRoot;

    private AetherUploadProperties properties;
    private AetherUploadManager manager;
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
        manager = new AetherUploadManager(properties, null);
    }

    @AfterEach
    void tearDown() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void uploadedChunkListIsLinearNotQuadratic() {
        UploadHeader header = new UploadHeader();
        header.setTotalChunks(1_000_000);

        long start = System.nanoTime();
        List<Integer> list = header.uploadedChunkList();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(list.isEmpty(), "未标记任何分片时应返回空表");
        // 修复前该调用实测 21,395ms；位图移到循环外后应是毫秒级
        assertTrue(elapsedMs < 500,
                "空位图必须 O(位图长度) 返回（修复前约 21 秒），实际 " + elapsedMs + "ms");
    }

    @Test
    void uploadedChunkListReturnsOnlyMarkedChunks() {
        UploadHeader header = new UploadHeader();
        header.setTotalChunks(1_000_000);
        header.markChunk(0);
        header.markChunk(5);
        header.markChunk(999_999);
        assertEquals(List.of(0, 5, 999_999), header.uploadedChunkList());
    }

    @Test
    void tinyClientChunkSizeIsBoundedByMaxChunksInsteadOfRejected() {
        // 客户端声明 1 字节分片（等价于审计里的 size=1e8&chunkSize=1 场景，只是规模更小）：
        // 默认不设下限（保持兼容），由 max-chunks 反推最小分片 → 分片数被钳到上限内
        UploadResult result = manager.prepare("file", "a.bin", 10_000_000L,
                "application/octet-stream", "id-clamp", 1L);
        assertTrue(result.chunkSize > 1L, "极小分片应被自适应放大: " + result.chunkSize);
        assertTrue(result.totalChunks <= 100_000,
                "分片数必须落在上限内: " + result.totalChunks);
        assertEquals(100_000, result.totalChunks, "10MB/1e5 分片恰好落在上限边界");
    }

    @Test
    void hugeSizeIsRejectedBeforeCreatingAnyFile() throws IOException {
        long before = countPartFiles();
        // Long.MAX_VALUE/2 用 int 收窄会变成负数（等价于审计里的 8e9 溢出）：
        // 修复后必须在创建任何临时文件之前拒绝
        assertThrows(UploadException.class,
                () -> manager.prepare("file", "huge.bin", Long.MAX_VALUE / 2,
                        "application/octet-stream", "id-huge", 10L * 1024 * 1024),
                "分片数超上限必须拒绝");
        assertEquals(before, countPartFiles(), "被拒绝的 prepare 不得留下 .part 文件");
    }

    @Test
    void largeFileGrowsChunkSizeInsteadOfBeingRejected() {
        properties.getGroups().get("file").setMaxChunks(10);
        long size = 10L * 4096 + 1;
        UploadResult result = manager.prepare("file", "grow.bin", size,
                "application/octet-stream", "id-grow", 4096L);
        assertTrue(result.chunkSize > 4096L, "大文件应自适应放大分片: " + result.chunkSize);
        assertTrue(result.totalChunks <= 10, "分片数不得超过上限: " + result.totalChunks);
    }

    @Test
    void stalePartFilesAreSweptOnPrepare() throws IOException {
        Path tempDir = Paths.get(tempRoot.toString(), "temp");
        Files.createDirectories(tempDir);
        Path stale = tempDir.resolve("stale.part");
        Files.write(stale, new byte[16]);
        Files.setLastModifiedTime(stale,
                FileTime.fromMillis(System.currentTimeMillis() - 3L * 86400 * 1000));

        manager.prepare("file", "new.bin", 1024, "application/octet-stream", "id-sweep", null);

        assertFalse(Files.exists(stale), "超过记录头 TTL 的 .part 应被机会式清理");
    }

    private long countPartFiles() throws IOException {
        if (!Files.isDirectory(tempRoot)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(tempRoot)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".part")).count();
        }
    }
}