package com.weacsoft.jaravel.vendor.storage.database;

import com.weacsoft.jaravel.vendor.storage.StorageException;
import com.weacsoft.jaravel.vendor.storage.contract.FileInfo;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.Visibility;
import com.weacsoft.jaravel.vendor.storage.util.MimeTypeGuesser;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据库文件存储实现，对齐 Laravel 的磁盘契约，但数据落地到关系型数据库。
 * <p>
 * 设计要点：
 * <ul>
 *   <li><b>内容列名可定制</b>：文件内容统一存放在单列，列名由 {@code contentColumn} 配置决定，
 *       默认 {@code content}。业务可自行指定列名（如 {@code file_data}、{@code blob} 等）。</li>
 *   <li><b>二进制 / base64 开关</b>：{@code binary=true} 时将字节直接写入 {@code content}（BLOB/LONGBLOB 列）；
 *       {@code false} 时改为 base64 编码写入 {@code content}（LONGTEXT 列），
 *       以兼容不支持二进制列的数据库。由该开关决定列类型，不再区分 binary/text 双列。</li>
 *   <li><b>分片组装</b>：单条记录受数据库记录大小限制（常见 4G）。通过 {@code chunkSize} 配置单条上限，
 *       超出则按该大小切分为多条记录存储，读取时按 {@code chunk_index} 顺序拼接还原。</li>
 *   <li><b>目录是虚拟的</b>：数据库模式下目录只是路径前缀，没有真实目录实体，
 *       因此 {@link #makeDirectory} 为无操作（自动「存在」），列举目录由文件路径推导。</li>
 * </ul>
 *
 * 线程安全：本类仅持有不可变配置与无状态 {@link JdbcTemplate}，可被多线程共享。
 */
public class DatabaseFilesystem implements Filesystem {

    /** base64 文本相对二进制的膨胀系数，用于提示。 */
    private static final double BASE64_INFLATION = 4.0 / 3.0;

    private final String name;
    private final JdbcTemplate jdbc;
    private final boolean binary;
    private final String contentColumn;
    private final long chunkSize;
    private final String tablePrefix;
    private final Visibility defaultVisibility;

    private final String filesTable;
    private final String chunksTable;

    public DatabaseFilesystem(String name,
                              DataSource dataSource,
                              boolean binary,
                              String contentColumn,
                              long chunkSize,
                              String tablePrefix,
                              String defaultVisibility) {
        this.name = name;
        this.jdbc = new JdbcTemplate(dataSource);
        this.binary = binary;
        this.contentColumn = (contentColumn == null || contentColumn.isBlank()) ? "content" : contentColumn.trim();
        this.chunkSize = chunkSize;
        this.tablePrefix = (tablePrefix == null || tablePrefix.isBlank()) ? "storage_" : tablePrefix;
        this.defaultVisibility = Visibility.from(defaultVisibility);
        this.filesTable = this.tablePrefix + "file";
        this.chunksTable = this.tablePrefix + "file_chunk";
        ensureTables();
    }

    // ==================== 建表 ====================

    /**
     * 自动建表（幂等）。即使没有迁移脚本，磁盘首次使用时也能直接工作。
     * 对于「单独的数据库」，迁移脚本通常作用于主数据源，故本方法是对独立数据源的兜底。
     * <p>
     * 内容列类型由 {@code binary} 决定：{@code true} 时用 BLOB/LONGBLOB，否则用 LONGTEXT。
     * 列名由 {@code contentColumn} 决定（默认 {@code content}）。
     */
    private void ensureTables() {
        String contentType = binary ? "LONGBLOB" : "LONGTEXT";
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + filesTable + " (" +
                " disk VARCHAR(64) NOT NULL," +
                " path VARCHAR(1024) NOT NULL," +
                " visibility VARCHAR(16) NOT NULL DEFAULT 'private'," +
                " mime_type VARCHAR(255)," +
                " size BIGINT NOT NULL DEFAULT 0," +
                " chunk_count INTEGER NOT NULL DEFAULT 0," +
                " created_at BIGINT," +
                " updated_at BIGINT," +
                " PRIMARY KEY (disk, path))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + chunksTable + " (" +
                " disk VARCHAR(64) NOT NULL," +
                " path VARCHAR(1024) NOT NULL," +
                " chunk_index INTEGER NOT NULL," +
                " " + contentColumn + " " + contentType + "," +
                " size INTEGER NOT NULL DEFAULT 0," +
                " created_at BIGINT," +
                " updated_at BIGINT," +
                " PRIMARY KEY (disk, path, chunk_index))");
    }

    // ==================== 路径规范化 ====================

    private String normalize(String path) {
        String p = path == null ? "" : path.replace('\\', '/').trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, Math.max(0, p.length() - 1));
        }
        if (p.isEmpty()) {
            throw new StorageException("路径不能为空");
        }
        return p;
    }

    private String basename(String path) {
        int idx = path.lastIndexOf('/');
        String name = idx < 0 ? path : path.substring(idx + 1);
        return name.isEmpty() ? path : name;
    }

    /**
     * 查询至多一行的便捷封装。Spring 的 {@code queryForObject} 在无结果时会抛异常，
     * 这里改用 {@code query} 并在为空时返回 {@code null}，便于上层统一判空。
     */
    private <T> T queryOne(String sql, RowMapper<T> mapper, Object... args) {
        List<T> list = jdbc.query(sql, mapper, args);
        return list.isEmpty() ? null : list.get(0);
    }

    // ==================== 读取 ====================

    @Override
    public boolean exists(String path) {
        String norm = normalize(path);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + filesTable + " WHERE disk = ? AND path = ?",
                (rs, i) -> rs.getInt(1), name, norm);
        return count != null && count > 0;
    }

    @Override
    public byte[] read(String path) {
        String norm = normalize(path);
        byte[] data = doRead(norm);
        if (data == null) {
            throw StorageException.notFound(norm);
        }
        return data;
    }

    private byte[] doRead(String norm) {
        // 存放方式由全局 binary 开关决定（单列 content），不再按文件记录 binary_stored
        return assembleChunks(norm, binary);
    }

    private byte[] assembleChunks(String norm, boolean isBinary) {
        List<ChunkRow> rows = jdbc.query(
                "SELECT chunk_index, " + contentColumn + " FROM " + chunksTable +
                        " WHERE disk = ? AND path = ? ORDER BY chunk_index ASC",
                (rs, i) -> new ChunkRow(rs.getInt("chunk_index"),
                        isBinary ? rs.getBytes(contentColumn) : null,
                        isBinary ? null : rs.getString(contentColumn)),
                name, norm);
        if (rows.isEmpty()) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (ChunkRow row : rows) {
                if (isBinary) {
                    if (row.binary != null) {
                        out.write(row.binary);
                    }
                } else {
                    if (row.text != null && !row.text.isEmpty()) {
                        out.write(Base64.getDecoder().decode(row.text));
                    }
                }
            }
        } catch (IOException e) {
            throw new StorageException("拼接文件分片失败: " + norm, e);
        }
        return out.toByteArray();
    }

    @Override
    public InputStream readStream(String path) {
        return new ByteArrayInputStream(read(path));
    }

    // ==================== 写入 ====================

    @Override
    public void put(String path, byte[] contents) {
        String norm = normalize(path);
        long now = System.currentTimeMillis();
        boolean isBinary = binary;
        List<byte[]> chunks = split(contents, chunkSize);
        int count = chunks.size();

        // 先清旧数据（按 disk+path 删除分片与元信息），再写入，保证幂等。
        jdbc.update("DELETE FROM " + chunksTable + " WHERE disk = ? AND path = ?", name, norm);
        jdbc.update("DELETE FROM " + filesTable + " WHERE disk = ? AND path = ?", name, norm);

        if (isBinary) {
            for (int i = 0; i < count; i++) {
                byte[] c = chunks.get(i);
                jdbc.update("INSERT INTO " + chunksTable +
                                " (disk, path, chunk_index, " + contentColumn + ", size, created_at, updated_at)" +
                                " VALUES (?, ?, ?, ?, ?, ?, ?)",
                        name, norm, i, c, c.length, now, now);
            }
        } else {
            for (int i = 0; i < count; i++) {
                byte[] c = chunks.get(i);
                jdbc.update("INSERT INTO " + chunksTable +
                                " (disk, path, chunk_index, " + contentColumn + ", size, created_at, updated_at)" +
                                " VALUES (?, ?, ?, ?, ?, ?, ?)",
                        name, norm, i, Base64.getEncoder().encodeToString(c), c.length, now, now);
            }
        }

        jdbc.update("INSERT INTO " + filesTable +
                        " (disk, path, visibility, mime_type, size, chunk_count, created_at, updated_at)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                name, norm, defaultVisibility.value(), MimeTypeGuesser.guess(norm),
                contents.length, count, now, now);
    }

    @Override
    public long putStream(String path, InputStream input) {
        try {
            byte[] all = input.readAllBytes();
            put(path, all);
            return all.length;
        } catch (IOException e) {
            throw StorageException.writeFailed(path, e);
        }
    }

    @Override
    public void append(String path, byte[] contents) {
        String norm = normalize(path);
        byte[] existing = exists(norm) ? read(norm) : new byte[0];
        byte[] merged = new byte[existing.length + contents.length];
        System.arraycopy(existing, 0, merged, 0, existing.length);
        System.arraycopy(contents, 0, merged, existing.length, contents.length);
        put(norm, merged);
    }

    @Override
    public long writeTo(String path, OutputStream output) {
        byte[] data = read(path);
        try {
            output.write(data);
            return data.length;
        } catch (IOException e) {
            throw StorageException.readFailed(path, e);
        }
    }

    /**
     * 将内容按 chunkSize 切分为多个分片。
     * chunkSize <= 0 表示不切分，整文件作为单条记录存放。
     */
    private List<byte[]> split(byte[] data, long chunkSize) {
        List<byte[]> result = new ArrayList<>();
        if (chunkSize <= 0) {
            result.add(data);
            return result;
        }
        int size = (int) Math.min(chunkSize, Integer.MAX_VALUE);
        if (data.length == 0) {
            result.add(new byte[0]);
            return result;
        }
        for (int i = 0; i < data.length; i += size) {
            int len = Math.min(size, data.length - i);
            byte[] chunk = new byte[len];
            System.arraycopy(data, i, chunk, 0, len);
            result.add(chunk);
        }
        return result;
    }

    // ==================== 删除 / 移动 / 复制 ====================

    @Override
    public boolean delete(String path) {
        String norm = normalize(path);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + filesTable + " WHERE disk = ? AND path = ?",
                (rs, i) -> rs.getInt(1), name, norm);
        if (count == null || count == 0) {
            return false;
        }
        jdbc.update("DELETE FROM " + chunksTable + " WHERE disk = ? AND path = ?", name, norm);
        jdbc.update("DELETE FROM " + filesTable + " WHERE disk = ? AND path = ?", name, norm);
        return true;
    }

    @Override
    public void copy(String from, String to) {
        String nFrom = normalize(from);
        String nTo = normalize(to);
        if (!exists(nFrom)) {
            throw StorageException.notFound(nFrom);
        }
        byte[] data = read(nFrom);
        put(nTo, data);
        // 保留源文件可见性
        try {
            setVisibility(nTo, visibility(nFrom));
        } catch (StorageException ignored) {
            // 元信息缺失则忽略，使用默认可见性
        }
    }

    @Override
    public void move(String from, String to) {
        copy(from, to);
        delete(from);
    }

    // ==================== 元信息 ====================

    @Override
    public long size(String path) {
        String norm = normalize(path);
        Long s = queryOne(
                "SELECT size FROM " + filesTable + " WHERE disk = ? AND path = ?",
                (rs, i) -> rs.getLong("size"), name, norm);
        if (s == null) {
            throw StorageException.notFound(norm);
        }
        return s;
    }

    @Override
    public Instant lastModified(String path) {
        String norm = normalize(path);
        Long t = queryOne(
                "SELECT updated_at FROM " + filesTable + " WHERE disk = ? AND path = ?",
                (rs, i) -> rs.getLong("updated_at"), name, norm);
        if (t == null) {
            throw StorageException.notFound(norm);
        }
        return Instant.ofEpochMilli(t);
    }

    @Override
    public String mimeType(String path) {
        String norm = normalize(path);
        String m = queryOne(
                "SELECT mime_type FROM " + filesTable + " WHERE disk = ? AND path = ?",
                (rs, i) -> rs.getString("mime_type"), name, norm);
        if (m != null && !m.isEmpty()) {
            return m;
        }
        return MimeTypeGuesser.guess(norm);
    }

    @Override
    public FileInfo info(String path) {
        String norm = normalize(path);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + filesTable + " WHERE disk = ? AND path = ?", name, norm);
        if (rows.isEmpty()) {
            throw StorageException.notFound(norm);
        }
        Map<String, Object> meta = rows.get(0);
        long sz = meta.get("size") == null ? 0L : ((Number) meta.get("size")).longValue();
        Long updated = meta.get("updated_at") == null ? null : ((Number) meta.get("updated_at")).longValue();
        Instant lm = updated == null ? Instant.now() : Instant.ofEpochMilli(updated);
        Object mimeObj = meta.get("mime_type");
        String mime = (mimeObj == null || String.valueOf(mimeObj).isEmpty())
                ? MimeTypeGuesser.guess(norm) : String.valueOf(mimeObj);
        Visibility vis = Visibility.from(meta.get("visibility") == null
                ? "private" : String.valueOf(meta.get("visibility")));
        return new FileInfo(norm, basename(norm), false, sz, lm, mime, vis);
    }

    @Override
    public Visibility visibility(String path) {
        String norm = normalize(path);
        String v = queryOne(
                "SELECT visibility FROM " + filesTable + " WHERE disk = ? AND path = ?",
                (rs, i) -> rs.getString("visibility"), name, norm);
        if (v == null) {
            throw StorageException.notFound(norm);
        }
        return Visibility.from(v);
    }

    @Override
    public void setVisibility(String path, Visibility visibility) {
        String norm = normalize(path);
        int updated = jdbc.update(
                "UPDATE " + filesTable + " SET visibility = ?, updated_at = ? WHERE disk = ? AND path = ?",
                visibility.value(), System.currentTimeMillis(), name, norm);
        if (updated == 0) {
            throw StorageException.notFound(norm);
        }
    }

    // ==================== 目录 ====================

    @Override
    public List<FileInfo> files(String directory) {
        return listEntries(directory, false, false);
    }

    @Override
    public List<FileInfo> allFiles(String directory) {
        return listEntries(directory, false, true);
    }

    @Override
    public List<FileInfo> directories(String directory) {
        return listEntries(directory, true, false);
    }

    private List<FileInfo> listEntries(String directory, boolean wantDirectory, boolean recursive) {
        String dir = directory == null || directory.isBlank() ? "" : normalize(directory);
        List<String> paths = queryPaths(dir);
        Set<String> collected = new LinkedHashSet<>();
        String prefix = dir.isEmpty() ? "" : dir + "/";

        for (String p : paths) {
            String rel = p.startsWith(prefix) ? p.substring(prefix.length()) : p;
            int firstSlash = rel.indexOf('/');
            if (firstSlash < 0) {
                // 直接位于 dir 下的文件
                if (!wantDirectory) {
                    collected.add(p);
                }
                continue;
            }
            String firstSeg = rel.substring(0, firstSlash);
            if (wantDirectory) {
                if (recursive) {
                    String[] segs = rel.split("/");
                    StringBuilder cur = new StringBuilder(dir);
                    for (int i = 0; i < segs.length - 1; i++) {
                        if (cur.length() > 0) {
                            cur.append("/");
                        }
                        cur.append(segs[i]);
                        collected.add(cur.toString());
                    }
                } else {
                    collected.add(dir.isEmpty() ? firstSeg : dir + "/" + firstSeg);
                }
            }
        }

        List<FileInfo> result = new ArrayList<>();
        for (String entry : collected) {
            if (wantDirectory) {
                result.add(new FileInfo(entry, basename(entry), true, 0L, Instant.EPOCH, null, defaultVisibility));
            } else {
                result.add(info(entry));
            }
        }
        result.sort(Comparator.comparing(FileInfo::path));
        return result;
    }

    private List<String> queryPaths(String dir) {
        String pattern = dir.isEmpty() ? "%" : dir + "/%";
        return jdbc.queryForList(
                "SELECT path FROM " + filesTable + " WHERE disk = ? AND path LIKE ?",
                String.class, name, pattern);
    }

    @Override
    public void makeDirectory(String directory) {
        // 数据库模式下目录是虚拟的，路径前缀天然「存在」，无需任何操作。
    }

    @Override
    public boolean deleteDirectory(String directory) {
        String norm = normalize(directory);
        boolean existed = exists(norm) || hasAnyUnder(norm);
        jdbc.update("DELETE FROM " + chunksTable + " WHERE disk = ? AND (path = ? OR path LIKE ?)",
                name, norm, norm + "/%");
        jdbc.update("DELETE FROM " + filesTable + " WHERE disk = ? AND (path = ? OR path LIKE ?)",
                name, norm, norm + "/%");
        return existed;
    }

    private boolean hasAnyUnder(String norm) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + filesTable + " WHERE disk = ? AND path LIKE ?",
                (rs, i) -> rs.getInt(1), name, norm + "/%");
        return count != null && count > 0;
    }

    // ==================== URL / 本地路径 ====================

    @Override
    public String url(String path) {
        throw new StorageException("数据库磁盘 [" + name + "] 不支持生成公开 URL，请通过接口（如 Storage.download）提供下载");
    }

    @Override
    public String path(String path) {
        throw new StorageException("数据库磁盘 [" + name + "] 不是本地文件系统，不支持获取本地路径");
    }

    @Override
    public String name() {
        return name;
    }

    // ==================== 内部类型 ====================

    private static final class ChunkRow {
        @SuppressWarnings("unused")
        final int index;
        final byte[] binary;
        final String text;

        ChunkRow(int index, byte[] binary, String text) {
            this.index = index;
            this.binary = binary;
            this.text = text;
        }
    }
}
