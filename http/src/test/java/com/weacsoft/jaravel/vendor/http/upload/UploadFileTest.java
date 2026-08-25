package com.weacsoft.jaravel.vendor.http.upload;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UploadFile} 上传落盘助手测试（http 模块承担 MultipartFile 处理，
 * 写入目标通过 {@link UploadFile.Target} 解耦，不依赖 storage）。
 */
class UploadFileTest {

    /** 记录型 Target：捕获 (path, bytes) 调用序列 */
    private static final class RecordingTarget implements UploadFile.Target {
        final List<String> paths = new ArrayList<>();
        final List<byte[]> contents = new ArrayList<>();

        @Override
        public void store(String path, byte[] contents) {
            paths.add(path);
            this.contents.add(contents);
        }
    }

    /** 手写的 MultipartFile 假实现（http 测试 classpath 无 Mockito） */
    private static final class FakeMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String originalFilename;
        private final String contentType;

        FakeMultipartFile(String originalFilename, byte[] content) {
            this(originalFilename, content, "application/octet-stream");
        }

        FakeMultipartFile(String originalFilename, byte[] content, String contentType) {
            this.originalFilename = originalFilename;
            this.content = content;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            try (FileOutputStream out = new FileOutputStream(dest)) {
                out.write(content);
            }
        }
    }

    @Test
    void baseNameFallsBackToDefault() {
        assertEquals("file", UploadFile.baseName(null));
        assertEquals("file", UploadFile.baseName(new FakeMultipartFile(null, new byte[0])));
        assertEquals("file", UploadFile.baseName(new FakeMultipartFile("  ", new byte[0])));
    }

    @Test
    void baseNameTakesLastPathSegment() {
        assertEquals("logo.png", UploadFile.baseName(new FakeMultipartFile("logo.png", new byte[1])));
        assertEquals("logo.png", UploadFile.baseName(new FakeMultipartFile("a/b/logo.png", new byte[1])));
        assertEquals("logo.png", UploadFile.baseName(new FakeMultipartFile("C:\\tmp\\a\\logo.png", new byte[1])));
    }

    @Test
    void storeWithRootDirUsesBasename() throws IOException {
        RecordingTarget target = new RecordingTarget();
        byte[] data = "你好，世界".getBytes(StandardCharsets.UTF_8);
        FakeMultipartFile file = new FakeMultipartFile("hello.txt", data);

        String path = UploadFile.store(file, null, target);

        assertEquals("hello.txt", path);
        assertEquals(1, target.paths.size());
        assertArrayEquals(data, target.contents.get(0));
    }

    @Test
    void storeWithDirPrefixesBasename() throws IOException {
        RecordingTarget target = new RecordingTarget();
        String path = UploadFile.store(new FakeMultipartFile("a.png", new byte[]{1, 2, 3}), "avatars", target);
        assertEquals("avatars/a.png", path);
    }

    @Test
    void storeNormalizesDirSeparatorsAndTrailingSlash() throws IOException {
        RecordingTarget target = new RecordingTarget();
        String path = UploadFile.store(new FakeMultipartFile("\\b.png", new byte[1]), "deep\\dir\\", target);
        assertEquals("deep/dir/b.png", path);
    }

    @Test
    void storeAsUsesGivenName() throws IOException {
        RecordingTarget target = new RecordingTarget();
        byte[] data = "report".getBytes(StandardCharsets.UTF_8);
        String path = UploadFile.storeAs(new FakeMultipartFile("原始名.bin", data), "reports", "a-2026.pdf", target);
        assertEquals("reports/a-2026.pdf", path);
        assertArrayEquals(data, target.contents.get(0));
    }

    @Test
    void storeBlankDirAndBlankNameFallBack() throws IOException {
        RecordingTarget target = new RecordingTarget();
        String path = UploadFile.storeAs(new FakeMultipartFile(null, new byte[1]), "  ", "  ", target);
        assertEquals("file", path);
    }

    @Test
    void storeNullFileThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> UploadFile.store(null, "dir", (path, bytes) -> {
                }));
    }

    @Test
    void storeNullTargetThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> UploadFile.store(new FakeMultipartFile("a", new byte[1]), "dir", null));
    }

    @Test
    void targetCanComposeWithAnyWritable() throws IOException {
        // 模拟 storage Filesystem::put 的目标适配（这里用 RecordingTarget 直接验证）
        RecordingTarget sink = new RecordingTarget();
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
        UploadFile.store(new FakeMultipartFile("x.txt", data), "dir", sink::store);
        assertEquals(1, sink.paths.size());
        assertEquals("dir/x.txt", sink.paths.get(0));
        assertArrayEquals(data, sink.contents.get(0));
    }
}
