package com.weacsoft.jaravel.vendor.storage.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库磁盘的流式读写与并发追加（审计 M8 / O1 / O2）。
 * <p>
 * 修复前三处「名不副实」的实现：
 * <ul>
 *   <li>{@code putStream} 用 {@code readAllBytes()} —— 上传大文件整份进堆；</li>
 *   <li>{@code readStream} 用 {@code new ByteArrayInputStream(read(path))} —— 下载大文件整份进堆；</li>
 *   <li>{@code writeTo} 全量 {@code read}；{@code append} 是「全量读 → 拼接 → 全量写」的跨事务读改写
 *       （并发追加丢更新）。</li>
 * </ul>
 * 现在：写入按分片读取、读取按分片惰性拉取、写出逐片转发、追加只重写最后一片并用大小 CAS 重试。
 */
class DatabaseFilesystemStreamingTest {

    private static final int CHUNK = 64;
    private DataSource dataSource;
    private DatabaseFilesystem fs;

    @BeforeEach
    void setUp() throws Exception {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:streamfs;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        createTables(ds, CHUNK);
        fs = new DatabaseFilesystem("db", ds, true, null, CHUNK, "s_", "private");
    }

    private static void createTables(DataSource ds, int chunkSize) throws Exception {
        try (Connection c = ds.getConnection(); java.sql.Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS s_file ("
                    + " disk VARCHAR(64) NOT NULL, path VARCHAR(1024) NOT NULL,"
                    + " visibility VARCHAR(16) NOT NULL DEFAULT 'private', mime_type VARCHAR(255),"
                    + " size BIGINT NOT NULL DEFAULT 0, chunk_count INT NOT NULL DEFAULT 0,"
                    + " created_at BIGINT, updated_at BIGINT)");
            st.execute("CREATE TABLE IF NOT EXISTS s_file_chunk ("
                    + " disk VARCHAR(64) NOT NULL, path VARCHAR(1024) NOT NULL, chunk_index INT NOT NULL,"
                    + " content LONGBLOB, size INT, created_at BIGINT, updated_at BIGINT)");
        }
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(7).nextBytes(bytes);
        return bytes;
    }

    @Test
    void putStreamReadsAtMostOneChunkAtATime() {
        byte[] data = randomBytes(CHUNK * 50);
        AtomicInteger maxRequested = new AtomicInteger();
        InputStream probe = new InputStream() {
            private int pos = 0;

            @Override
            public int read(byte[] b, int off, int len) {
                maxRequested.set(Math.max(maxRequested.get(), len));
                if (pos >= data.length) {
                    return -1;
                }
                int n = Math.min(len, data.length - pos);
                System.arraycopy(data, pos, b, off, n);
                pos += n;
                return n;
            }

            @Override
            public int read() {
                return pos >= data.length ? -1 : (data[pos++] & 0xFF);
            }
        };

        long written = fs.putStream("big.bin", probe);

        assertEquals(data.length, written);
        assertTrue(maxRequested.get() <= CHUNK,
                "写入必须按分片读取（旧实现 readAllBytes 会请求 " + data.length + " 字节），实际请求 "
                        + maxRequested.get());
        assertArrayEquals(data, fs.read("big.bin"));
    }

    @Test
    void readStreamIsLazyPerChunk() throws Exception {
        byte[] data = randomBytes(CHUNK * 50);
        fs.putStream("lazy.bin", new ByteArrayInputStream(data));

        try (InputStream in = fs.readStream("lazy.bin")) {
            assertTrue(in.read() >= 0, "应能读到首字节");
            assertTrue(in.available() <= CHUNK,
                    "惰性流一次只应驻留一个分片；旧实现（ByteArrayInputStream 包全量 byte[]）会报告约 "
                            + data.length + "，实际 " + in.available());
            byte[] rest = in.readAllBytes();
            byte[] all = new byte[data.length];
            all[0] = data[0];
            System.arraycopy(rest, 0, all, 1, rest.length);
            assertArrayEquals(data, all, "分片拼接结果必须与原文件一致");
        }
    }

    @Test
    void writeToStreamsWithoutFullMaterialization() throws Exception {
        byte[] data = randomBytes(CHUNK * 30);
        fs.putStream("out.bin", new ByteArrayInputStream(data));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long written = fs.writeTo("out.bin", out);

        assertEquals(data.length, written);
        assertArrayEquals(data, out.toByteArray());
    }

    @Test
    void appendWritesTailChunkCorrectly() {
        byte[] first = randomBytes(CHUNK + 10);      // 跨两个分片，末片不满
        fs.put("log.bin", first);
        byte[] extra = randomBytes(CHUNK * 2 + 5);

        fs.append("log.bin", extra);

        byte[] expected = new byte[first.length + extra.length];
        System.arraycopy(first, 0, expected, 0, first.length);
        System.arraycopy(extra, 0, expected, first.length, extra.length);
        assertArrayEquals(expected, fs.read("log.bin"), "追加后的内容必须与顺序拼接一致");
        assertEquals(expected.length, fs.size("log.bin"));
    }

    @Test
    void appendCreatesFileWhenAbsent() {
        byte[] data = randomBytes(CHUNK + 3);
        fs.append("new.bin", data);
        assertArrayEquals(data, fs.read("new.bin"));
        assertEquals(data.length, fs.size("new.bin"));
    }

    @Test
    void concurrentAppendDoesNotLoseUpdates() throws Exception {
        int threads = 8;
        int perThread = 10;
        int blockSize = 100;
        fs.put("concurrent.bin", new byte[0]);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int seed = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        byte[] block = new byte[blockSize];
                        java.util.Arrays.fill(block, (byte) (seed + 1));
                        for (int n = 0; n < perThread; n++) {
                            fs.append("concurrent.bin", block);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "并发追加应在超时前完成");
        } finally {
            pool.shutdownNow();
        }

        long expected = (long) threads * perThread * blockSize;
        assertEquals(expected, fs.size("concurrent.bin"),
                "并发追加不得丢更新（旧实现是跨事务读改写）");
        assertEquals(expected, fs.read("concurrent.bin").length);
    }
}