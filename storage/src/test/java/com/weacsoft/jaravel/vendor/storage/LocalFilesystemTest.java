package com.weacsoft.jaravel.vendor.storage;

import com.weacsoft.jaravel.vendor.storage.contract.DiskDefinition;
import com.weacsoft.jaravel.vendor.storage.contract.FileInfo;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.Visibility;
import com.weacsoft.jaravel.vendor.storage.local.LocalFilesystem;
import com.weacsoft.jaravel.vendor.storage.local.LocalFilesystemDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalFilesystem} 与 {@link StorageManager} 的单元测试。
 */
class LocalFilesystemTest {

    @TempDir
    Path tempDir;

    private Filesystem fs;

    @BeforeEach
    void setUp() {
        fs = new LocalFilesystem("test", tempDir.toString(), "/storage", Visibility.PRIVATE);
    }

    // ==================== 基础读写 ====================

    @Test
    @DisplayName("写入并读取文本")
    void putAndGetText() {
        fs.put("notes/todo.txt", "hello world");

        assertTrue(fs.exists("notes/todo.txt"));
        assertFalse(fs.missing("notes/todo.txt"));
        assertEquals("hello world", fs.get("notes/todo.txt"));
    }

    @Test
    @DisplayName("写入自动创建多级父目录")
    void putCreatesParentDirectories() {
        fs.put("a/b/c/d.txt", "deep");

        assertTrue(fs.exists("a/b/c/d.txt"));
        assertEquals("deep", fs.get("a/b/c/d.txt"));
    }

    @Test
    @DisplayName("写入字节并按字节读取")
    void putAndReadBytes() {
        byte[] data = {1, 2, 3, 4, 5};
        fs.put("bin/data.bin", data);

        assertArrayEquals(data, fs.read("bin/data.bin"));
        assertEquals(5, fs.size("bin/data.bin"));
    }

    @Test
    @DisplayName("重复写入覆盖原内容")
    void putOverwrites() {
        fs.put("f.txt", "first");
        fs.put("f.txt", "second");

        assertEquals("second", fs.get("f.txt"));
    }

    @Test
    @DisplayName("追加内容到文件末尾")
    void appendToFile() {
        fs.put("log.txt", "line1\n");
        fs.append("log.txt", "line2\n");

        assertEquals("line1\nline2\n", fs.get("log.txt"));
    }

    @Test
    @DisplayName("追加到不存在的文件时自动创建")
    void appendCreatesFile() {
        fs.append("new.txt", "content");

        assertEquals("content", fs.get("new.txt"));
    }

    // ==================== 流式读写（大文件） ====================

    @Test
    @DisplayName("流式写入并流式读取，内容一致")
    void putStreamAndReadStream() throws Exception {
        // 构造 1MB 数据，验证跨缓冲区（64KB）拷贝正确
        byte[] payload = new byte[1024 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }

        long written = fs.putStream("big/file.bin", new ByteArrayInputStream(payload));
        assertEquals(payload.length, written);

        try (InputStream in = fs.readStream("big/file.bin")) {
            assertArrayEquals(payload, in.readAllBytes());
        }
    }

    @Test
    @DisplayName("writeTo 将文件写出到输出流")
    void writeToOutputStream() {
        fs.put("out.txt", "stream me");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long count = fs.writeTo("out.txt", out);

        assertEquals(9, count);
        assertEquals("stream me", out.toString(StandardCharsets.UTF_8));
    }

    // ==================== 删除 / 复制 / 移动 ====================

    @Test
    @DisplayName("删除文件，重复删除返回 false")
    void deleteFile() {
        fs.put("x.txt", "x");

        assertTrue(fs.delete("x.txt"));
        assertFalse(fs.exists("x.txt"));
        assertFalse(fs.delete("x.txt"));
    }

    @Test
    @DisplayName("批量删除返回实际删除数量")
    void deleteMany() {
        fs.put("a.txt", "a");
        fs.put("b.txt", "b");

        int deleted = fs.delete(List.of("a.txt", "b.txt", "missing.txt"));

        assertEquals(2, deleted);
    }

    @Test
    @DisplayName("复制文件，源文件保留")
    void copyFile() {
        fs.put("src.txt", "data");

        fs.copy("src.txt", "dir/dst.txt");

        assertEquals("data", fs.get("src.txt"));
        assertEquals("data", fs.get("dir/dst.txt"));
    }

    @Test
    @DisplayName("移动文件，源文件消失")
    void moveFile() {
        fs.put("src.txt", "data");

        fs.move("src.txt", "dir/moved.txt");

        assertFalse(fs.exists("src.txt"));
        assertEquals("data", fs.get("dir/moved.txt"));
    }

    @Test
    @DisplayName("复制不存在的文件抛异常")
    void copyMissingThrows() {
        assertThrows(StorageException.class, () -> fs.copy("nope.txt", "dst.txt"));
    }

    // ==================== 元信息 ====================

    @Test
    @DisplayName("读取文件元信息")
    void fileInfo() {
        fs.put("docs/readme.md", "# hi");

        FileInfo info = fs.info("docs/readme.md");

        assertEquals("docs/readme.md", info.path());
        assertEquals("readme.md", info.name());
        assertFalse(info.directory());
        assertTrue(info.file());
        assertEquals(4, info.size());
        assertEquals("md", info.extension());
        assertNotNull(info.lastModified());
    }

    @Test
    @DisplayName("读取不存在的文件抛 StorageException")
    void readMissingThrows() {
        StorageException ex = assertThrows(StorageException.class, () -> fs.read("ghost.txt"));
        assertTrue(ex.getMessage().contains("ghost.txt"));
    }

    @Test
    @DisplayName("lastModified 返回非空时间")
    void lastModified() {
        fs.put("t.txt", "t");
        assertNotNull(fs.lastModified("t.txt"));
    }

    // ==================== 目录 ====================

    @Test
    @DisplayName("files 只列举当前层文件，不含子目录与其中文件")
    void listFilesNonRecursive() {
        fs.put("root.txt", "1");
        fs.put("sub/nested.txt", "2");

        List<FileInfo> files = fs.files("");

        assertEquals(1, files.size());
        assertEquals("root.txt", files.get(0).path());
    }

    @Test
    @DisplayName("allFiles 递归列举所有文件")
    void listAllFilesRecursive() {
        fs.put("root.txt", "1");
        fs.put("sub/nested.txt", "2");
        fs.put("sub/deep/more.txt", "3");

        List<FileInfo> files = fs.allFiles("");

        assertEquals(3, files.size());
    }

    @Test
    @DisplayName("directories 只列举直接子目录")
    void listDirectories() {
        fs.put("sub1/a.txt", "a");
        fs.put("sub2/b.txt", "b");
        fs.put("top.txt", "t");

        List<FileInfo> dirs = fs.directories("");

        assertEquals(2, dirs.size());
        assertTrue(dirs.stream().allMatch(FileInfo::directory));
    }

    @Test
    @DisplayName("创建与递归删除目录")
    void makeAndDeleteDirectory() {
        fs.makeDirectory("newdir/child");
        assertTrue(fs.exists("newdir/child"));

        fs.put("newdir/child/f.txt", "f");
        assertTrue(fs.deleteDirectory("newdir"));
        assertFalse(fs.exists("newdir"));
    }

    @Test
    @DisplayName("列举不存在的目录返回空列表")
    void listMissingDirectoryReturnsEmpty() {
        assertTrue(fs.files("nope").isEmpty());
    }

    @Test
    @DisplayName("禁止删除磁盘根目录")
    void cannotDeleteRoot() {
        assertThrows(StorageException.class, () -> fs.deleteDirectory(""));
    }

    // ==================== 路径穿越防护 ====================

    @Test
    @DisplayName("路径穿越 ../ 被拦截")
    void pathTraversalBlocked() {
        assertThrows(StorageException.class, () -> fs.put("../evil.txt", "hack"));
        assertThrows(StorageException.class, () -> fs.read("../../etc/passwd"));
        assertThrows(StorageException.class, () -> fs.delete("a/../../evil.txt"));
    }

    @Test
    @DisplayName("路径内部的 ../ 归一化后仍在根目录内则允许")
    void innerTraversalNormalizedIsAllowed() {
        fs.put("a/b/../c.txt", "ok");

        assertTrue(fs.exists("a/c.txt"));
    }

    @Test
    @DisplayName("前导斜杠被规范化为相对根目录")
    void leadingSlashNormalized() {
        fs.put("/abs.txt", "v");

        assertTrue(fs.exists("abs.txt"));
        assertEquals("v", fs.get("/abs.txt"));
    }

    // ==================== URL / 本地路径 ====================

    @Test
    @DisplayName("生成公开访问 URL")
    void generateUrl() {
        assertEquals("/storage/img/a.png", fs.url("img/a.png"));
        assertEquals("/storage/img/a.png", fs.url("/img/a.png"));
    }

    @Test
    @DisplayName("未配置 url 前缀时抛异常")
    void urlWithoutPrefixThrows() {
        Filesystem noUrl = new LocalFilesystem("nourl", tempDir.toString(), null, Visibility.PRIVATE);

        assertThrows(StorageException.class, () -> noUrl.url("a.png"));
    }

    @Test
    @DisplayName("local 驱动支持本地绝对路径")
    void localPathSupported() {
        fs.put("p.txt", "p");

        assertTrue(fs.supportsLocalPath());
        assertTrue(fs.path("p.txt").endsWith("p.txt"));
        assertTrue(java.nio.file.Files.exists(Path.of(fs.path("p.txt"))));
    }

    @Test
    @DisplayName("磁盘名称回填正确")
    void diskName() {
        assertEquals("test", fs.name());
    }

    // ==================== 驱动与管理器 ====================

    @Test
    @DisplayName("local 驱动 support 匹配 local 与 public")
    void driverSupport() {
        LocalFilesystemDriver driver = new LocalFilesystemDriver();

        assertTrue(driver.support("local"));
        assertTrue(driver.support("LOCAL"));
        assertTrue(driver.support("public"));
        assertFalse(driver.support("s3"));
    }

    @Test
    @DisplayName("驱动按配置创建磁盘")
    void driverCreatesDisk() {
        LocalFilesystemDriver driver = new LocalFilesystemDriver();

        Filesystem disk = driver.create("mydisk", Map.of(
                DiskDefinition.ROOT, tempDir.resolve("d1").toString(),
                DiskDefinition.URL, "/files"));

        assertEquals("mydisk", disk.name());
        disk.put("k.txt", "v");
        assertEquals("v", disk.get("k.txt"));
        assertEquals("/files/k.txt", disk.url("k.txt"));
    }

    @Test
    @DisplayName("管理器按定义延迟创建并缓存磁盘实例")
    void managerResolvesAndCachesDisk() {
        StorageManager manager = new StorageManager();
        manager.registerDriver(new LocalFilesystemDriver());
        manager.registerDisk("data", DiskDefinition.local(tempDir.resolve("data").toString()));
        manager.setDefaultDisk("data");

        Filesystem first = manager.disk("data");
        Filesystem second = manager.disk("data");

        assertEquals(first, second, "同名磁盘应返回同一缓存实例");
        assertEquals(first, manager.disk(), "默认磁盘应解析到 data");
        assertTrue(manager.hasDisk("data"));
        assertTrue(manager.diskNames().contains("data"));
    }

    @Test
    @DisplayName("解析未注册磁盘抛异常")
    void unknownDiskThrows() {
        StorageManager manager = new StorageManager();
        manager.registerDriver(new LocalFilesystemDriver());

        assertThrows(StorageException.class, () -> manager.disk("ghost"));
    }

    @Test
    @DisplayName("未知驱动抛异常")
    void unknownDriverThrows() {
        StorageManager manager = new StorageManager();
        manager.registerDisk("s3disk", DiskDefinition.of("s3"));

        StorageException ex = assertThrows(StorageException.class, () -> manager.disk("s3disk"));
        assertTrue(ex.getMessage().contains("s3"));
    }

    @Test
    @DisplayName("registerDisk(Filesystem) 直接注册实例并覆盖定义")
    void registerInstanceOverridesDefinition() {
        StorageManager manager = new StorageManager();
        manager.registerDriver(new LocalFilesystemDriver());
        manager.registerDisk("d", DiskDefinition.local(tempDir.resolve("x").toString()));

        Filesystem custom = new LocalFilesystem("d", tempDir.resolve("y").toString(), null, null);
        manager.registerDisk("d", custom);

        assertEquals(custom, manager.disk("d"));
    }

    @Test
    @DisplayName("flushInstances 后按定义重建实例")
    void flushInstancesRebuilds() {
        StorageManager manager = new StorageManager();
        manager.registerDriver(new LocalFilesystemDriver());
        manager.registerDisk("d", DiskDefinition.local(tempDir.resolve("z").toString()));

        Filesystem before = manager.disk("d");
        manager.flushInstances();
        Filesystem after = manager.disk("d");

        assertTrue(before != after, "flush 后应重建新实例");
    }

    // ==================== DiskDefinition ====================

    @Test
    @DisplayName("DiskDefinition 链式方法返回新实例且不可变")
    void diskDefinitionImmutable() {
        DiskDefinition base = DiskDefinition.local("/data");
        DiskDefinition withUrl = base.url("/files");

        assertFalse(base.config().containsKey(DiskDefinition.URL));
        assertEquals("/files", withUrl.config().get(DiskDefinition.URL));
        assertEquals("/data", withUrl.config().get(DiskDefinition.ROOT));
        assertEquals("local", withUrl.driver());
    }

    @Test
    @DisplayName("Visibility 解析与默认值")
    void visibilityParsing() {
        assertEquals(Visibility.PUBLIC, Visibility.from("public"));
        assertEquals(Visibility.PUBLIC, Visibility.from("PUBLIC"));
        assertEquals(Visibility.PRIVATE, Visibility.from("private"));
        assertEquals(Visibility.PRIVATE, Visibility.from(null));
        assertEquals(Visibility.PRIVATE, Visibility.from("garbage"));
    }

    @Test
    @DisplayName("可见性读写不抛异常（跨平台兼容）")
    void visibilityRoundTrip() {
        fs.put("v.txt", "v");

        fs.setVisibility("v.txt", Visibility.PUBLIC);

        assertNotNull(fs.visibility("v.txt"));
    }
}
