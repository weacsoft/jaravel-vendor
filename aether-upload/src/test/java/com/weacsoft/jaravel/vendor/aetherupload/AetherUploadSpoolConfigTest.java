package com.weacsoft.jaravel.vendor.aetherupload;

import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 临时文件暂存开关（PHP {@code upload_tmp_dir} 风格）。
 * <p>
 * 分片上传把未完成内容写到 {@code .part} 临时文件。默认关闭时沿用各组 {@code temp-dir}；
 * 开启 {@code jaravel.aether-upload.spool.enabled} 后统一切换到 {@code spool.dir}
 * （为空则用系统临时目录下的 {@code jaravel-aether-uploads}），便于把大文件临时数据放到
 * 缓存盘/独立数据盘，避免写满应用目录或系统盘。
 */
class AetherUploadSpoolConfigTest {

    @TempDir
    Path tempRoot;

    @TempDir
    Path spoolDir;

    private String originalUserDir;
    private AetherUploadProperties properties;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempRoot.toAbsolutePath().toString());

        properties = new AetherUploadProperties();
        properties.setDefaultGroup("file");
        AetherUploadProperties.GroupConfig file = new AetherUploadProperties.GroupConfig();
        file.setChunkSize(1024);
        properties.getGroups().put("file", file);
    }

    @AfterEach
    void tearDown() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    private boolean hasPartFile(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.anyMatch(p -> p.getFileName().toString().endsWith(".part"));
        }
    }

    @Test
    void spoolDisabledKeepsPerGroupTempDir() throws Exception {
        AetherUploadManager manager = new AetherUploadManager(properties, null);

        UploadResult result = manager.prepare("file", "a.bin", 2048, null, "id-default", 1024L);
        manager.writeChunk("file", result.resourceId, 0, new byte[1024]);

        assertTrue(hasPartFile(tempRoot), "默认应在组 temp-dir（相对运行目录）下产生 .part");
        assertFalse(hasPartFile(spoolDir), "未开启开关时不得写到 spool 目录");
    }

    @Test
    void spoolEnabledRedirectsTempFilesToConfiguredDir() throws Exception {
        properties.getSpool().setEnabled(true);
        properties.getSpool().setDir(spoolDir.toAbsolutePath().toString());

        AetherUploadManager manager = new AetherUploadManager(properties, null);
        UploadResult result = manager.prepare("file", "b.bin", 2048, null, "id-spool", 1024L);
        manager.writeChunk("file", result.resourceId, 0, new byte[1024]);

        assertTrue(hasPartFile(spoolDir), "开启开关后临时文件必须落在 spool.dir");
    }

    @Test
    void spoolEnabledWithoutDirFallsBackToSystemTemp() throws Exception {
        properties.getSpool().setEnabled(true);
        properties.getSpool().setDir(null);

        AetherUploadManager manager = new AetherUploadManager(properties, null);
        UploadResult result = manager.prepare("file", "c.bin", 2048, null, "id-sys", 1024L);

        Path fallback = Path.of(System.getProperty("java.io.tmpdir"), "jaravel-aether-uploads");
        try {
            assertTrue(hasPartFile(fallback), "spool.dir 为空时应落到系统临时目录下的 jaravel-aether-uploads");
        } finally {
            // 清理本次产生的临时数据，避免污染系统临时目录
            if (Files.exists(fallback)) {
                try (Stream<Path> walk = Files.walk(fallback)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // 清理失败不影响断言
                        }
                    });
                }
            }
        }
    }
}