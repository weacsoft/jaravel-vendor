package com.weacsoft.jaravel.vendor.aetherupload;

import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传归属（owner）校验回归测试 —— 收敛「可预测 identifier 带来的越权」。
 * <p>
 * 背景（专家团安全角色标为 High，且该模块当时尚未被业务使用，故直接改契约）：
 * identifier 由前端按「文件名+大小+mtime」这类<b>可预测</b>规则拼出。原实现把它当作全局键，
 * 于是攻击者只要知道目标文件的三要素，就能：①读到他人的上传进度/位图；
 * ②向他人的 resourceId 写分片；③最终决定受害者拿到什么文件内容（分片写满后 move 成成品）。
 * <p>
 * 现在的约束：identifier 按主体作用域隔离；任务记录归属，跨主体读/写/中止一律拒绝；
 * 匿名默认不启用续传。
 */
class AetherUploadOwnershipTest {

    @TempDir
    Path tempRoot;

    private AetherUploadProperties properties;
    private AetherUploadManager manager;
    private String originalUserDir;

    /** 当前请求主体（测试里手动切换以模拟不同用户） */
    private final String[] currentOwner = {null};

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
        manager.setOwnerResolver(() -> currentOwner[0]);
    }

    @AfterEach
    void tearDown() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    private static byte[] chunk(int size) {
        byte[] data = new byte[size];
        java.util.Arrays.fill(data, (byte) 'x');
        return data;
    }

    @Test
    void differentOwnersDoNotShareResumeTasks() {
        currentOwner[0] = "alice";
        UploadResult alice = manager.prepare("file", "a.bin", 2048, null, "predictable-id", null);

        currentOwner[0] = "bob";
        UploadResult bob = manager.prepare("file", "a.bin", 2048, null, "predictable-id", null);

        assertNotEquals(alice.resourceId, bob.resourceId,
                "不同主体的同名 identifier 不得命中同一个上传任务");
        assertEquals(false, bob.resumed, "bob 不应续传到 alice 的任务");
    }

    @Test
    void sameOwnerCanResume() {
        currentOwner[0] = "alice";
        UploadResult first = manager.prepare("file", "a.bin", 4096, null, "id-x", null);
        manager.writeChunk("file", first.resourceId, 0, chunk(1024));

        UploadResult resumed = manager.prepare("file", "a.bin", 4096, null, "id-x", null);

        assertTrue(resumed.resumed, "同一主体应能断点续传");
        assertEquals(first.resourceId, resumed.resourceId);
    }

    @Test
    void crossOwnerChunkWriteIsRejected() {
        currentOwner[0] = "alice";
        UploadResult alice = manager.prepare("file", "a.bin", 2048, null, "id-x", null);

        currentOwner[0] = "bob";
        UploadException ex = assertThrows(UploadException.class,
                () -> manager.writeChunk("file", alice.resourceId, 0, chunk(1024)),
                "跨主体写分片必须被拒绝（否则可篡改他人成品内容）");
        assertTrue(ex.getMessage().contains("无权"), "错误信息应表明无权访问，实际: " + ex.getMessage());
    }

    @Test
    void crossOwnerProgressAndAbortAreRejected() {
        currentOwner[0] = "alice";
        UploadResult alice = manager.prepare("file", "a.bin", 2048, null, "id-x", null);

        currentOwner[0] = "bob";
        assertThrows(UploadException.class, () -> manager.progress("file", alice.resourceId),
                "跨主体读取进度必须被拒绝（信息泄露）");
        assertThrows(UploadException.class, () -> manager.abort("file", alice.resourceId),
                "跨主体中止上传必须被拒绝");
    }

    @Test
    void sameOwnerOperationsStillWork() {
        currentOwner[0] = "alice";
        UploadResult alice = manager.prepare("file", "a.bin", 2048, null, "id-x", null);

        assertDoesNotThrow(() -> manager.progress("file", alice.resourceId));
        assertDoesNotThrow(() -> manager.writeChunk("file", alice.resourceId, 0, chunk(1024)));
        assertDoesNotThrow(() -> manager.abort("file", alice.resourceId));
    }
}