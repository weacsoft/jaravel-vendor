package com.weacsoft.jaravel.vendor.storage.database;

import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.Visibility;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DatabaseFilesystem 测试：
 * <ul>
 *   <li>内容列名可自定义（默认 content，这里测试自定义 my_data）</li>
 *   <li>列类型由 binary 开关决定：true -> BLOB(LONGBLOB)，false -> LONGTEXT(base64)</li>
 *   <li>写入并读取内容一致（含分片与中文）</li>
 * </ul>
 */
public class DatabaseFilesystemTest {

    private DataSource h2(String name) {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    /**
     * 手动建表（模拟 artisan migrate 执行后的效果）。
     * 由于 DatabaseFilesystem 不再自动建表，测试需自行创建表结构。
     */
    private void createTables(DataSource ds, String tablePrefix, String contentColumn, boolean binary) {
        String filesTable = tablePrefix + "file";
        String chunksTable = tablePrefix + "file_chunk";
        String contentType = binary ? "LONGBLOB" : "LONGTEXT";
        try (Connection c = ds.getConnection()) {
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS " + filesTable + " (" +
                    " disk VARCHAR(64) NOT NULL," +
                    " path VARCHAR(1024) NOT NULL," +
                    " visibility VARCHAR(16) NOT NULL DEFAULT 'private'," +
                    " mime_type VARCHAR(255)," +
                    " size BIGINT NOT NULL DEFAULT 0," +
                    " chunk_count INTEGER NOT NULL DEFAULT 0," +
                    " created_at BIGINT," +
                    " updated_at BIGINT," +
                    " PRIMARY KEY (disk, path))");
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS " + chunksTable + " (" +
                    " disk VARCHAR(64) NOT NULL," +
                    " path VARCHAR(1024) NOT NULL," +
                    " chunk_index INTEGER NOT NULL," +
                    " " + contentColumn + " " + contentType + "," +
                    " size INTEGER NOT NULL DEFAULT 0," +
                    " created_at BIGINT," +
                    " updated_at BIGINT," +
                    " PRIMARY KEY (disk, path, chunk_index))");
        } catch (Exception e) {
            throw new RuntimeException("测试建表失败", e);
        }
    }

    private Set<String> columnsOf(DataSource ds, String table) throws Exception {
        Set<String> cols = new HashSet<>();
        try (Connection c = ds.getConnection();
             ResultSet rs = c.getMetaData().getColumns(null, null, table, null)) {
            while (rs.next()) {
                cols.add(rs.getString("COLUMN_NAME"));
            }
        }
        return cols;
    }

    private void roundTrip(DatabaseFilesystem fs, String path, byte[] data) throws Exception {
        fs.put(path, data);
        assertTrue(fs.exists(path), "文件应存在: " + path);
        byte[] got = fs.read(path);
        assertArrayEquals(data, got, "读取内容应与写入一致: " + path);
        assertEquals(Visibility.PRIVATE, fs.visibility(path));
        fs.delete(path);
        assertTrue(!fs.exists(path), "删除后文件应不存在: " + path);
    }

    @Test
    public void testCustomColumnNameBinary() throws Exception {
        DataSource ds = h2("storage_binary");
        createTables(ds, "storage_", "my_data", true);
        DatabaseFilesystem fs = new DatabaseFilesystem("db", ds,
                true, "my_data", 1024L * 1024L, "storage_", "private");

        // 验证使用了自定义列名 my_data，且为单列（不再有 content_binary/content_text）
        Set<String> cols = columnsOf(ds, "storage_file_chunk");
        assertTrue(cols.contains("my_data"), "分片表应包含自定义列 my_data，实际: " + cols);
        assertTrue(!cols.contains("content_binary"), "不应存在 content_binary 列");
        assertTrue(!cols.contains("content_text"), "不应存在 content_text 列");

        // 二进制写入读取（含中文与分片边界）
        roundTrip(fs, "a/hello.txt", "你好，世界 hello world".getBytes(StandardCharsets.UTF_8));
        roundTrip(fs, "b/big.bin", new byte[]{0, 1, 2, (byte) 0xFF, (byte) 0x80, 33, 34, 35});
    }

    @Test
    public void testCustomColumnNameBase64Text() throws Exception {
        DataSource ds = h2("storage_text");
        createTables(ds, "storage_", "my_data", false);
        DatabaseFilesystem fs = new DatabaseFilesystem("db", ds,
                false, "my_data", 1024L * 1024L, "storage_", "private");

        Set<String> cols = columnsOf(ds, "storage_file_chunk");
        assertTrue(cols.contains("my_data"), "分片表应包含自定义列 my_data，实际: " + cols);

        // base64 文本模式写入读取
        roundTrip(fs, "c/text.txt", "中文内容 base64 模式".getBytes(StandardCharsets.UTF_8));
        roundTrip(fs, "d/bin.bin", new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF});
    }

    @Test
    public void testDefaultColumnName() throws Exception {
        DataSource ds = h2("storage_def");
        // 不传 contentColumn 时默认 content
        createTables(ds, "storage_", "content", true);
        DatabaseFilesystem fs = new DatabaseFilesystem("db", ds,
                true, null, 1024L * 1024L, "storage_", "private");

        Set<String> cols = columnsOf(ds, "storage_file_chunk");
        assertTrue(cols.contains("content"), "默认应包含 content 列，实际: " + cols);
        roundTrip(fs, "e/default.txt", "默认列名 content".getBytes(StandardCharsets.UTF_8));
    }
}
