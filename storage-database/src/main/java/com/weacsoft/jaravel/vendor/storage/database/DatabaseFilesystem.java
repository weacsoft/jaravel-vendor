package com.weacsoft.jaravel.vendor.storage.database;

import com.weacsoft.jaravel.vendor.storage.StorageException;
import com.weacsoft.jaravel.vendor.storage.contract.FileInfo;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.Visibility;
import com.weacsoft.jaravel.vendor.storage.util.MimeTypeGuesser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据库文件存储实现，对齐 Laravel 的磁盘契约，但数据落地到关系型数据库。
 * <p>
 * 连接<b>走 jaravel database 模块</b>或任意 {@link DataSource}，
 * SQL 操作用<b>原生 JDBC</b> 执行——<b>不依赖 spring-jdbc</b>。
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
 * <b>不会自动建表</b>：需通过 {@code artisan storage:table} 命令（生成迁移文件后执行
 * {@code artisan migrate}）或手动建 SQL 创建表，表结构为
 * {@code <prefix>file}（元信息）与 {@code <prefix>file_chunk}（内容分片）两张表。
 *
 * <p>
 * 线程安全：本类仅持有不可变配置与无状态的 {@link DataSource}，可被多线程共享。
 */
public class DatabaseFilesystem implements Filesystem {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseFilesystem.class);

    /** base64 文本相对二进制的膨胀系数，用于提示。 */
    private static final double BASE64_INFLATION = 4.0 / 3.0;

    private final String name;
    private final DataSource dataSource;
    private final boolean binary;
    private final String contentColumn;
    private final long chunkSize;
    private final Visibility defaultVisibility;

    private final String filesTable;
    private final String chunksTable;

    /**
     * 便捷构造器：仅指定名称和数据源，其余参数使用默认值
     *（binary=true, contentColumn=null→"content", chunkSize=1MB, tablePrefix=null→"storage_", defaultVisibility=null→PRIVATE）。
     *
     * @param name       磁盘名称
     * @param dataSource 数据源
     */
    public DatabaseFilesystem(String name, DataSource dataSource) {
        this(name, dataSource, true, null, 1048576L, null, null);
    }

    public DatabaseFilesystem(String name,
                              DataSource dataSource,
                              boolean binary,
                              String contentColumn,
                              long chunkSize,
                              String tablePrefix,
                              String defaultVisibility) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource 不能为 null（请通过 database 模块注册连接或使用现有数据源）");
        }
        this.name = name;
        this.dataSource = dataSource;
        this.binary = binary;
        this.contentColumn = (contentColumn == null || contentColumn.isBlank()) ? "content" : contentColumn.trim();
        this.chunkSize = chunkSize;
        String prefix = (tablePrefix == null || tablePrefix.isBlank()) ? "storage_" : tablePrefix;
        this.defaultVisibility = Visibility.from(defaultVisibility);
        this.filesTable = prefix + "file";
        this.chunksTable = prefix + "file_chunk";
        // 不自动建表：需通过 artisan storage:table 生成迁移并执行 artisan migrate，或手动建表
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

    // ==================== 读取 ====================

    @Override
    public boolean exists(String path) {
        String norm = normalize(path);
        List<Long> counts = queryRows(
                "SELECT COUNT(*) FROM " + filesTable + " WHERE disk = ? AND path = ?",
                rs -> (long) rs.getInt(1), name, norm);
        long count = counts.isEmpty() ? 0L : counts.get(0);
        return count > 0;
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
        String col = contentColumn;
        List<ChunkRow> rows = queryRows(
                "SELECT chunk_index, " + col + " FROM " + chunksTable +
                        " WHERE disk = ? AND path = ? ORDER BY chunk_index ASC",
                rs -> new ChunkRow(rs.getInt("chunk_index"),
                        isBinary ? rs.getBytes(col) : null,
                        isBinary ? null : rs.getString(col)),
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
        executeUpdate("DELETE FROM " + chunksTable + " WHERE disk = ? AND path = ?", name, norm);
        executeUpdate("DELETE FROM " + filesTable + " WHERE disk = ? AND path = ?", name, norm);

        String col = contentColumn;
        if (isBinary) {
            for (int i = 0; i < count; i++) {
                byte[] c = chunks.get(i);
                executeUpdate("INSERT INTO " + chunksTable +
                                " (disk, path, chunk_index, " + col + ", size, created_at, updated_at)" +
                                " VALUES (?, ?, ?, ?, ?, ?, ?)",
                        name, norm, i, c, c.length, now, now);
            }
        } else {
            for (int i = 0; i < count; i++) {
                byte[] c = chunks.get(i);
                executeUpdate("INSERT INTO " + chunksTable +
                                " (disk, path, chunk_index, " + col + ", size, created_at, updated_at)" +
                                " VALUES (?, ?, ?, ?, ?, ?, ?)",
                        name, norm, i, Base64.getEncoder().encodeToString(c), c.length, now, now);
            }
        }

        executeUpdate("INSERT INTO " + filesTable +
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
        List<Long> counts = queryRows(
                "SELECT COUNT(*) FROM " + filesTable + " WHERE disk = ? AND path = ?",
                rs -> (long) rs.getInt(1), name, norm);
        long count = counts.isEmpty() ? 0L : counts.get(0);
        if (count == 0) {
            return false;
        }
        executeUpdate("DELETE FROM " + chunksTable + " WHERE disk = ? AND path = ?", name, norm);
        executeUpdate("DELETE FROM " + filesTable + " WHERE disk = ? AND path = ?", name, norm);
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
        List<Long> sizes = queryRows(
                "SELECT size FROM " + filesTable + " WHERE disk = ? AND path = ?",
                rs -> rs.getLong("size"), name, norm);
        if (sizes.isEmpty()) {
            throw StorageException.notFound(norm);
        }
        return sizes.get(0);
    }

    @Override
    public Instant lastModified(String path) {
        String norm = normalize(path);
        List<Long> times = queryRows(
                "SELECT updated_at FROM " + filesTable + " WHERE disk = ? AND path = ?",
                rs -> rs.getLong("updated_at"), name, norm);
        if (times.isEmpty()) {
            throw StorageException.notFound(norm);
        }
        return Instant.ofEpochMilli(times.get(0));
    }

    @Override
    public String mimeType(String path) {
        String norm = normalize(path);
        List<String> mimes = queryRows(
                "SELECT mime_type FROM " + filesTable + " WHERE disk = ? AND path = ?",
                rs -> rs.getString("mime_type"), name, norm);
        if (!mimes.isEmpty() && mimes.get(0) != null && !mimes.get(0).isEmpty()) {
            return mimes.get(0);
        }
        return MimeTypeGuesser.guess(norm);
    }

    @Override
    public FileInfo info(String path) {
        String norm = normalize(path);
        List<FileMeta> metas = queryRows(
                "SELECT size, updated_at, mime_type, visibility FROM " + filesTable
                        + " WHERE disk = ? AND path = ?",
                rs -> new FileMeta(
                        rs.getLong("size"),
                        rs.getLong("updated_at"),
                        rs.wasNull() ? null : Boolean.TRUE,
                        rs.getString("mime_type"),
                        rs.getString("visibility")),
                name, norm);
        if (metas.isEmpty()) {
            throw StorageException.notFound(norm);
        }
        FileMeta meta = metas.get(0);
        long sz = meta.size();
        Long updated = meta.updatedPresent() ? meta.updatedAt() : null;
        Instant lm = updated == null ? Instant.now() : Instant.ofEpochMilli(updated);
        String mime = (meta.mimeType() == null || meta.mimeType().isEmpty())
                ? MimeTypeGuesser.guess(norm) : meta.mimeType();
        Visibility vis = Visibility.from(meta.visibility() == null
                ? "private" : meta.visibility());
        return new FileInfo(norm, basename(norm), false, sz, lm, mime, vis);
    }

    @Override
    public Visibility visibility(String path) {
        String norm = normalize(path);
        List<String> values = queryRows(
                "SELECT visibility FROM " + filesTable + " WHERE disk = ? AND path = ?",
                rs -> rs.getString("visibility"), name, norm);
        if (values.isEmpty() || values.get(0) == null) {
            throw StorageException.notFound(norm);
        }
        return Visibility.from(values.get(0));
    }

    @Override
    public void setVisibility(String path, Visibility visibility) {
        String norm = normalize(path);
        int updated = executeUpdate(
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
            } else if (recursive) {
                // 递归文件列表：子目录下的文件同样收集（此前遗漏，导致 allFiles 列不出任何子路径文件）
                collected.add(p);
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
        return queryRows(
                "SELECT path FROM " + filesTable + " WHERE disk = ? AND path LIKE ?",
                rs -> rs.getString("path"), name, pattern);
    }

    @Override
    public void makeDirectory(String directory) {
        // 数据库模式下目录是虚拟的，路径前缀天然「存在」，无需任何操作。
    }

    @Override
    public boolean deleteDirectory(String directory) {
        String norm = normalize(directory);
        boolean existed = exists(norm) || hasAnyUnder(norm);
        executeUpdate("DELETE FROM " + chunksTable + " WHERE disk = ? AND (path = ? OR path LIKE ?)",
                name, norm, norm + "/%");
        executeUpdate("DELETE FROM " + filesTable + " WHERE disk = ? AND (path = ? OR path LIKE ?)",
                name, norm, norm + "/%");
        return existed;
    }

    private boolean hasAnyUnder(String norm) {
        List<Long> counts = queryRows(
                "SELECT COUNT(*) FROM " + filesTable + " WHERE disk = ? AND path LIKE ?",
                rs -> (long) rs.getInt(1), name, norm + "/%");
        long count = counts.isEmpty() ? 0L : counts.get(0);
        return count > 0;
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

    // ==================== 原生 JDBC 工具方法 ====================

    /**
     * 执行更新语句（INSERT/UPDATE/DELETE）。
     *
     * @return 受影响行数
     * @throws IllegalStateException 数据库错误（含表不存在——请先生成/执行 storage 迁移）
     */
    private int executeUpdate(String sql, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            logger.debug("[storage-db] 数据库操作失败: {}", sql);
            throw new IllegalStateException("数据库操作失败: " + sql, e);
        }
    }

    /**
     * 执行查询并逐行映射。
     *
     * @throws IllegalStateException 数据库错误
     */
    private <T> List<T> queryRows(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            List<T> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
            }
            return rows;
        } catch (SQLException e) {
            logger.debug("[storage-db] 数据库查询失败: {}", sql);
            throw new IllegalStateException("数据库操作失败: " + sql, e);
        }
    }

    /**
     * 行映射函数：允许抛出受检的 {@link SQLException}。
     */
    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    // ==================== 内部类型 ====================

    private static final class ChunkRow {
        final int index;
        final byte[] binary;
        final String text;

        ChunkRow(int index, byte[] binary, String text) {
            this.index = index;
            this.binary = binary;
            this.text = text;
        }
    }

    /** 文件元信息行（{@link #info} 使用） */
    private record FileMeta(long size, long updatedAt, boolean updatedPresent,
                            String mimeType, String visibility) {
    }
}
