package com.weacsoft.jaravel.vendor.aetherupload;

import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadProperties;
import com.weacsoft.jaravel.vendor.storage.StorageManager;
import com.weacsoft.jaravel.vendor.storage.contract.DiskDefinition;
import com.weacsoft.jaravel.vendor.storage.contract.FileInfo;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.Visibility;
import com.weacsoft.jaravel.vendor.storage.local.LocalFilesystem;
import com.weacsoft.jaravel.vendor.storage.local.LocalFilesystemDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * aether-upload 与 storage 模块的集成测试：
 * 组配置 {@code disk} 后，上传完成的文件应落到对应磁盘，临时文件被清理；
 * 未配置 {@code disk} 时行为保持不变（写本地 save-dir）。
 */
class UploadStorageIntegrationTest {

    @TempDir
    Path tempRoot;

    private String originalUserDir;
    private StorageManager storage;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempRoot.toAbsolutePath().toString());

        storage = new StorageManager();
        storage.registerDriver(new LocalFilesystemDriver());
    }

    @AfterEach
    void tearDown() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    /**
     * 构造一个组名为 {@code file} 的上传管理器。
     *
     * @param diskName 组配置的磁盘名，null 表示不使用 storage
     */
    private AetherUploadManager manager(String diskName) {
        AetherUploadProperties props = new AetherUploadProperties();
        props.setDefaultGroup("file");

        AetherUploadProperties.GroupConfig file = new AetherUploadProperties.GroupConfig();
        file.setChunkSize(4);
        file.setSaveDir("uploads");
        file.setDisk(diskName);
        props.getGroups().put("file", file);

        return new AetherUploadManager(props, null, storage);
    }

    /**
     * 完整分片上传，返回最后一次分片写入的结果。
     */
    private UploadResult uploadAll(AetherUploadManager manager, String filename,
                                   byte[] content, int chunkSize, String identifier) {
        UploadResult prepared = manager.prepare("file", filename, content.length,
                "application/octet-stream", identifier, (long) chunkSize);
        UploadResult last = prepared;
        for (int i = 0; i < prepared.totalChunks; i++) {
            int from = i * chunkSize;
            int to = Math.min(from + chunkSize, content.length);
            byte[] chunk = new byte[to - from];
            System.arraycopy(content, from, chunk, 0, chunk.length);
            last = manager.writeChunk("file", prepared.resourceId, i, chunk);
        }
        return last;
    }

    @Test
    @DisplayName("配置 disk 后分片上传的文件落到本地磁盘内")
    void chunkedUploadLandsOnLocalDisk() {
        Path diskRoot = tempRoot.resolve("diskroot");
        storage.registerDisk("media", DiskDefinition.local(diskRoot.toString()));

        byte[] content = "hello aether upload via storage".getBytes(StandardCharsets.UTF_8);
        UploadResult result = uploadAll(manager("media"), "note.txt", content, 4, "id-disk-1");

        assertTrue(result.completed);

        Path saved = Path.of(result.savedPath);
        assertTrue(saved.startsWith(diskRoot), "落盘路径应在磁盘根目录内: " + saved);
        assertTrue(Files.exists(saved));
        assertArrayEquals(content, readAll(saved));

        List<FileInfo> files = storage.disk("media").allFiles("uploads");
        assertEquals(1, files.size());
        assertEquals(content.length, files.get(0).size());
    }

    @Test
    @DisplayName("落盘后临时分片文件被清理")
    void tempFileRemovedAfterCompletion() {
        storage.registerDisk("media", DiskDefinition.local(tempRoot.resolve("d2").toString()));
        AetherUploadManager manager = manager("media");

        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        UploadResult prepared = manager.prepare("file", "a.bin", content.length,
                null, "id-temp", 4L);

        UploadResult last = prepared;
        for (int i = 0; i < prepared.totalChunks; i++) {
            int from = i * 4;
            int to = Math.min(from + 4, content.length);
            byte[] chunk = new byte[to - from];
            System.arraycopy(content, from, chunk, 0, chunk.length);
            last = manager.writeChunk("file", prepared.resourceId, i, chunk);
        }

        assertTrue(last.completed);
        // 临时目录下不应残留该资源的分片文件
        Path tempDir = tempRoot.resolve("temp");
        if (Files.exists(tempDir)) {
            assertFalse(containsResource(tempDir, prepared.resourceId),
                    "完成后临时文件应被清理");
        }
    }

    @Test
    @DisplayName("同步上传同样落到配置的磁盘")
    void syncUploadLandsOnDisk() {
        Path diskRoot = tempRoot.resolve("syncroot");
        storage.registerDisk("media", DiskDefinition.local(diskRoot.toString()));

        byte[] content = "sync payload".getBytes(StandardCharsets.UTF_8);
        UploadResult result = manager("media").uploadSync("file", "s.txt", "text/plain", content);

        assertTrue(result.completed);
        Path saved = Path.of(result.savedPath);
        assertTrue(saved.startsWith(diskRoot), "同步上传应落到磁盘内: " + saved);
        assertArrayEquals(content, readAll(saved));
    }

    @Test
    @DisplayName("远程磁盘（不支持本地路径）走流式转存，savedPath 为 disk:// 定位")
    void remoteDiskUsesStreamTransfer() {
        Path backing = tempRoot.resolve("remote");
        storage.registerDisk("s3", new RemoteLikeFilesystem(
                new LocalFilesystem("s3", backing.toString(), null, Visibility.PRIVATE)));

        byte[] content = "remote stream content".getBytes(StandardCharsets.UTF_8);
        UploadResult result = uploadAll(manager("s3"), "r.txt", content, 4, "id-remote");

        assertTrue(result.completed);
        assertTrue(result.savedPath.startsWith("disk://s3/"),
                "远程磁盘应返回 disk:// 定位，实际: " + result.savedPath);

        String relative = result.savedPath.substring("disk://s3/".length());
        assertArrayEquals(content, storage.disk("s3").read(relative));
    }

    @Test
    @DisplayName("未配置 disk 时降级为本地 save-dir，行为不变")
    void withoutDiskFallsBackToLocalSaveDir() {
        byte[] content = "plain local save".getBytes(StandardCharsets.UTF_8);
        UploadResult result = uploadAll(manager(null), "p.txt", content, 4, "id-plain");

        assertTrue(result.completed);
        Path saved = Path.of(result.savedPath);
        assertTrue(saved.startsWith(tempRoot.resolve("uploads")),
                "应落在运行目录下的 uploads 中: " + saved);
        assertArrayEquals(content, readAll(saved));
    }

    /**
     * 递归判断目录下是否存在包含指定 resourceId 的文件。
     */
    private boolean containsResource(Path dir, String resourceId) {
        try (var walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().contains(resourceId));
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] readAll(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 测试替身：委托本地文件系统，但声明不支持本地路径，
     * 以模拟 S3/OSS 等远程驱动，覆盖流式转存分支。
     */
    private static class RemoteLikeFilesystem implements Filesystem {
        private final Filesystem delegate;

        RemoteLikeFilesystem(Filesystem delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean supportsLocalPath() {
            return false;
        }

        @Override
        public String path(String path) {
            throw new UnsupportedOperationException("远程磁盘不支持本地路径");
        }

        @Override public boolean exists(String path) { return delegate.exists(path); }
        @Override public byte[] read(String path) { return delegate.read(path); }
        @Override public InputStream readStream(String path) { return delegate.readStream(path); }
        @Override public void put(String path, byte[] contents) { delegate.put(path, contents); }
        @Override public long putStream(String path, InputStream in) { return delegate.putStream(path, in); }
        @Override public void append(String path, byte[] contents) { delegate.append(path, contents); }
        @Override public long writeTo(String path, OutputStream out) { return delegate.writeTo(path, out); }
        @Override public boolean delete(String path) { return delegate.delete(path); }
        @Override public void copy(String from, String to) { delegate.copy(from, to); }
        @Override public void move(String from, String to) { delegate.move(from, to); }
        @Override public long size(String path) { return delegate.size(path); }
        @Override public Instant lastModified(String path) { return delegate.lastModified(path); }
        @Override public String mimeType(String path) { return delegate.mimeType(path); }
        @Override public FileInfo info(String path) { return delegate.info(path); }
        @Override public Visibility visibility(String path) { return delegate.visibility(path); }
        @Override public void setVisibility(String p, Visibility v) { delegate.setVisibility(p, v); }
        @Override public List<FileInfo> files(String directory) { return delegate.files(directory); }
        @Override public List<FileInfo> allFiles(String directory) { return delegate.allFiles(directory); }
        @Override public List<FileInfo> directories(String d) { return delegate.directories(d); }
        @Override public void makeDirectory(String directory) { delegate.makeDirectory(directory); }
        @Override public boolean deleteDirectory(String d) { return delegate.deleteDirectory(d); }
        @Override public String url(String path) { return delegate.url(path); }
        @Override public String name() { return delegate.name(); }
    }
}
