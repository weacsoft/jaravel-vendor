package com.weacsoft.jaravel.vendor.storage.database;

import com.weacsoft.jaravel.vendor.database.JdbcExecutor;
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
 * <b>架构对齐（0.1.3）</b>：连接与 SQL 执行统一走 database 模块
 * （{@link JdbcExecutor}，数据源来自 {@code ConnectionManager} 注册表或业务方显式传入），
 * 驱动不再自带一套私有 JDBC 工具方法；建表统一走 migration 模块迁移能力
 * （{@code vendor:publish --tag=migrations} 发布内置迁移 + {@code artisan migrate}，
 * 或 {@code artisan storage:table} 生成迁移文件）。
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
 * <b>不会自动建表</b>：建表统一走迁移能力——
 * {@code artisan vendor:publish --tag=migrations} 发布本模块内置迁移（默认
 * {@code storage_file} / {@code storage_file_chunk} 两张表）后执行 {@code artisan migrate}；
 * 或 {@code artisan storage:table} 生成迁移文件（支持自定义表前缀/列名）后执行 {@code artisan migrate}。
 * 自定义表结构时需保证与本类读取的表/列名一致。
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

    /** database 模块 SQL 执行底座（驱动不再自带私有 JDBC 四件套） */
    private final JdbcExecutor jdbc;

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
        this.jdbc = new JdbcExecutor(dataSource);
        // 不自动建表：建表统一走迁移能力（vendor:publish --tag=migrations / storage:table + migrate）
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
        List<Long> counts = jdbc.queryMapped(
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
        List<ChunkRow> rows = jdbc.queryMapped(
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

    /**
     * 流式读取（审计 M8/O1）：<b>按分片惰性读取</b>，内存占用 O(chunkSize) 而不是整个文件。
     * <p>
     * 旧实现是 {@code new ByteArrayInputStream(read(path))} —— 先把整个文件读进堆再包一层流，
     * 配置 {@code disk: <数据库磁盘>} 时大文件下载会 OOM（与「流式」的名字完全相反）。
     * <p>
     * 实现方式：先取分片总数，再按需逐片查询（每片一条 SELECT），因此任何时刻只持有一个分片。
     *
     * @param path 相对路径
     * @return 惰性输入流
     */
    @Override
    public InputStream readStream(String path) {
        String norm = normalize(path);
        Long total = jdbc.queryForObject(
                "SELECT size FROM " + filesTable + " WHERE disk = ? AND path = ?", Long.class, name, norm);
        if (total == null) {
            throw StorageException.notFound(norm);
        }
        final long totalSize = total;
        final int chunkCount = (int) ((totalSize + chunkSize - 1) / chunkSize);
        final boolean isBinary = binary;

        return new InputStream() {

            private int nextIndex = 0;
            private byte[] current = new byte[0];
            private int offset = 0;
            private boolean exhausted = false;

            private boolean ensure() {
                while (offset >= current.length) {
                    if (exhausted || nextIndex >= chunkCount) {
                        return false;
                    }
                    byte[] chunk = readChunk(norm, nextIndex++, isBinary);
                    if (chunk == null) {
                        exhausted = true;
                        return false;
                    }
                    current = chunk;
                    offset = 0;
                }
                return true;
            }

            @Override
            public int read() {
                if (!ensure()) {
                    return -1;
                }
                return current[offset++] & 0xFF;
            }

            @Override
            public int read(byte[] target, int off, int len) {
                if (len == 0) {
                    return 0;
                }
                if (!ensure()) {
                    return -1;
                }
                int available = Math.min(len, current.length - offset);
                System.arraycopy(current, offset, target, off, available);
                offset += available;
                return available;
            }

            @Override
            public int available() {
                return current.length - offset;
            }
        };
    }

    /**
     * 读取单个分片（O(chunkSize) 内存）。
     *
     * @param norm     规范化路径
     * @param index    分片序号
     * @param isBinary 是否二进制列
     * @return 分片字节；不存在返回 null
     */
    private byte[] readChunk(String norm, int index, boolean isBinary) {
        String col = contentColumn;
        List<ChunkRow> rows = jdbc.queryMapped(
                "SELECT chunk_index, " + col + " FROM " + chunksTable +
                        " WHERE disk = ? AND path = ? AND chunk_index = ?",
                rs -> new ChunkRow(rs.getInt("chunk_index"),
                        isBinary ? rs.getBytes(col) : null,
                        isBinary ? null : rs.getString(col)),
                name, norm, index);
        if (rows.isEmpty()) {
            return null;
        }
        ChunkRow row = rows.get(0);
        if (isBinary) {
            return row.binary;
        }
        return row.text == null || row.text.isEmpty() ? new byte[0] : Base64.getDecoder().decode(row.text);
    }

    // ==================== 写入 ====================

    @Override
    public void put(String path, byte[] contents) {
        String norm = normalize(path);
        long now = System.currentTimeMillis();
        boolean isBinary = binary;
        List<byte[]> chunks = split(contents, chunkSize);
        int count = chunks.size();
        String col = contentColumn;

        // 四条语句必须在同一事务里：先清旧数据再写新数据，中途失败会留下
        // 「元信息已删、分片只写了一半」的坏状态（读出来是半截文件）。
        // 依赖 JdbcExecutor.inTransaction 保证同一连接 + 失败回滚。
        jdbc.inTransaction(tx -> {
            tx.update("DELETE FROM " + chunksTable + " WHERE disk = ? AND path = ?", name, norm);
            tx.update("DELETE FROM " + filesTable + " WHERE disk = ? AND path = ?", name, norm);

            for (int i = 0; i < count; i++) {
                byte[] c = chunks.get(i);
                Object chunkValue = isBinary ? c : Base64.getEncoder().encodeToString(c);
                tx.update("INSERT INTO " + chunksTable +
                                " (disk, path, chunk_index, " + col + ", size, created_at, updated_at)" +
                                " VALUES (?, ?, ?, ?, ?, ?, ?)",
                        name, norm, i, chunkValue, c.length, now, now);
            }

            tx.update("INSERT INTO " + filesTable +
                            " (disk, path, visibility, mime_type, size, chunk_count, created_at, updated_at)" +
                            " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    name, norm, defaultVisibility.value(), MimeTypeGuesser.guess(norm),
                    contents.length, count, now, now);
            return null;
        });
    }

    /**
     * 流式写入（审计 M8/O1）：<b>边读边写分片</b>，内存占用 O(chunkSize) 而不是整个文件。
     * <p>
     * 旧实现 {@code input.readAllBytes()} 会把整个上传文件读进堆 —— 上传大文件时与「流式」的名字
     * 相反地 OOM（{@code AetherUploadManager.moveToDisk} 的「内存占用恒定」注释因此不成立）。
     * 全部分片在同一事务内写入，失败整体回滚，不留半截文件。
     *
     * @param path  相对路径
     * @param input 输入流（不关闭，由调用方负责）
     * @return 写入字节数
     */
    @Override
    public long putStream(String path, InputStream input) {
        String norm = normalize(path);
        long now = System.currentTimeMillis();
        boolean isBinary = binary;
        String col = contentColumn;
        int bufferSize = (int) Math.max(1, Math.min(chunkSize, Integer.MAX_VALUE - 8));
        try {
            return jdbc.inTransaction(tx -> {
                tx.update("DELETE FROM " + chunksTable + " WHERE disk = ? AND path = ?", name, norm);
                tx.update("DELETE FROM " + filesTable + " WHERE disk = ? AND path = ?", name, norm);

                long written = 0;
                int index = 0;
                byte[] buffer = new byte[bufferSize];
                while (true) {
                    int filled;
                    try {
                        filled = readFully(input, buffer, bufferSize);
                    } catch (IOException e) {
                        // 受检异常不能穿过 inTransaction（它只保证回滚并包装运行时异常）
                        throw new java.io.UncheckedIOException(e);
                    }
                    if (filled <= 0) {
                        break;
                    }
                    byte[] chunk = filled == bufferSize ? buffer.clone() : java.util.Arrays.copyOf(buffer, filled);
                    Object chunkValue = isBinary ? chunk : Base64.getEncoder().encodeToString(chunk);
                    tx.update("INSERT INTO " + chunksTable +
                                    " (disk, path, chunk_index, " + col + ", size, created_at, updated_at)" +
                                    " VALUES (?, ?, ?, ?, ?, ?, ?)",
                            name, norm, index, chunkValue, filled, now, now);
                    written += filled;
                    index++;
                }

                tx.update("INSERT INTO " + filesTable +
                                " (disk, path, visibility, mime_type, size, chunk_count, created_at, updated_at)" +
                                " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        name, norm, defaultVisibility.value(), MimeTypeGuesser.guess(norm),
                        written, index, now, now);
                return written;
            });
        } catch (java.io.UncheckedIOException e) {
            throw StorageException.writeFailed(path, e.getCause());
        }
    }

    /**
     * 尽量读满缓冲区（{@code InputStream.read} 允许提前返回）。
     *
     * @param input  输入流
     * @param buffer 缓冲区
     * @param length 期望长度
     * @return 实际读入字节数；流结束返回 -1
     * @throws IOException 读取失败
     */
    private static int readFully(InputStream input, byte[] buffer, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = input.read(buffer, total, length - total);
            if (read < 0) {
                return total == 0 ? -1 : total;
            }
            total += read;
        }
        return total;
    }

    /**
     * 追加内容（审计 M8/O2）。
     * <p>
     * 旧实现是「{@code read} 全量 → 拼接 → {@code put} 全量」：既把整个文件读进堆（大文件 OOM），
     * 又是**跨事务的读-改-写**（并发追加丢更新）。
     * <p>
     * 现在：只读<b>最后一个分片</b>（≤ chunkSize）与新增内容拼接后重写该分片及其后续分片，
     * 内存 O(chunkSize + 追加长度)；元信息更新用<b>大小 CAS</b>（{@code WHERE size = 读到的旧值}），
     * 更新 0 行说明期间被别人追加过 → 重读重试，因此并发追加不再丢更新。
     *
     * @param path     相对路径
     * @param contents 追加内容
     */
    @Override
    public void append(String path, byte[] contents) {
        if (contents == null || contents.length == 0) {
            return;
        }
        String norm = normalize(path);
        long now = System.currentTimeMillis();
        boolean isBinary = binary;
        String col = contentColumn;

        for (int attempt = 0; attempt < APPEND_MAX_ATTEMPTS; attempt++) {
          synchronized (appendLockFor(norm)) {
            Long oldSizeValue = jdbc.queryForObject(
                    "SELECT size FROM " + filesTable + " WHERE disk = ? AND path = ?", Long.class, name, norm);
            long oldSize = oldSizeValue == null ? 0L : oldSizeValue;
            int oldChunkCount = (int) ((oldSize + chunkSize - 1) / chunkSize);
            byte[] tail = oldChunkCount == 0 ? new byte[0] : readChunk(norm, oldChunkCount - 1, isBinary);
            if (tail == null) {
                tail = new byte[0];
            }
            byte[] merged = new byte[tail.length + contents.length];
            System.arraycopy(tail, 0, merged, 0, tail.length);
            System.arraycopy(contents, 0, merged, tail.length, contents.length);

            long newSize = oldSize + contents.length;
            int startIndex = Math.max(0, oldChunkCount - 1);
            List<byte[]> newChunks = split(merged, chunkSize);
            int newChunkCount = startIndex + newChunks.size();

            Boolean committed = jdbc.inTransaction(tx -> {
                if (oldSizeValue == null) {
                    tx.update("INSERT INTO " + filesTable +
                                    " (disk, path, visibility, mime_type, size, chunk_count, created_at, updated_at)" +
                                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            name, norm, defaultVisibility.value(), MimeTypeGuesser.guess(norm),
                            newSize, newChunkCount, now, now);
                } else {
                    int updated = tx.update("UPDATE " + filesTable +
                                    " SET size = ?, chunk_count = ?, updated_at = ?" +
                                    " WHERE disk = ? AND path = ? AND size = ?",
                            newSize, newChunkCount, now, name, norm, oldSize);
                    if (updated == 0) {
                        return false;   // 期间被并发追加/覆盖 → 重读重试
                    }
                }
                tx.update("DELETE FROM " + chunksTable +
                        " WHERE disk = ? AND path = ? AND chunk_index >= ?", name, norm, startIndex);
                for (int i = 0; i < newChunks.size(); i++) {
                    byte[] c = newChunks.get(i);
                    Object chunkValue = isBinary ? c : Base64.getEncoder().encodeToString(c);
                    tx.update("INSERT INTO " + chunksTable +
                                    " (disk, path, chunk_index, " + col + ", size, created_at, updated_at)" +
                                    " VALUES (?, ?, ?, ?, ?, ?, ?)",
                            name, norm, startIndex + i, chunkValue, c.length, now, now);
                }
                return true;
            });
            if (Boolean.TRUE.equals(committed)) {
                return;
            }
          }
        }
        throw new StorageException("追加失败：并发冲突重试 " + APPEND_MAX_ATTEMPTS + " 次仍未成功: " + path);
    }

    /**
     * 按路径分段锁：把「读旧长度 → 重写末片 → 更新元信息」整段串行化（同一 JVM 内）。
     * <p>
     * 为何必须有它：{@code UPDATE ... WHERE size = 旧值} 的 CAS 在 MVCC 快照语义下仍可能
     * 基于过期快照成功提交（H2 MVStore 实测：并发追加 8000 字节只留下 1900），因此同一进程内
     * 必须用锁串行；CAS 保留用于<b>跨实例</b>冲突检测（失败即重读重试）。
     */
    private static final int APPEND_LOCK_SEGMENTS = 64;
    private static final Object[] APPEND_LOCKS = new Object[APPEND_LOCK_SEGMENTS];

    static {
        for (int i = 0; i < APPEND_LOCK_SEGMENTS; i++) {
            APPEND_LOCKS[i] = new Object();
        }
    }

    private static Object appendLockFor(String norm) {
        return APPEND_LOCKS[Math.floorMod(norm.hashCode(), APPEND_LOCK_SEGMENTS)];
    }

    /** 追加时的最大 CAS 重试次数（并发追加同一文件） */
    private static final int APPEND_MAX_ATTEMPTS = 5;

    /**
     * 流式写出（审计 M8/O1）：逐分片写目标流，内存 O(chunkSize)。
     *
     * @param path   相对路径
     * @param output 目标流（不关闭）
     * @return 写出字节数
     */
    @Override
    public long writeTo(String path, OutputStream output) {
        String norm = normalize(path);
        if (!exists(norm)) {
            throw StorageException.notFound(path);
        }
        try (InputStream in = readStream(norm)) {
            return in.transferTo(output);
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
        List<Long> counts = jdbc.queryMapped(
                "SELECT COUNT(*) FROM " + filesTable + " WHERE disk = ? AND path = ?",
                rs -> (long) rs.getInt(1), name, norm);
        long count = counts.isEmpty() ? 0L : counts.get(0);
        if (count == 0) {
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
        List<Long> sizes = jdbc.queryMapped(
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
        List<Long> times = jdbc.queryMapped(
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
        List<String> mimes = jdbc.queryMapped(
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
        List<FileMeta> metas = jdbc.queryMapped(
                "SELECT size, updated_at, mime_type, visibility FROM " + filesTable
                        + " WHERE disk = ? AND path = ?",
                rs -> {
                    long size = rs.getLong("size");
                    // updated_at 在迁移里是可空列：必须紧跟 getLong 判断 wasNull，
                    // 并用可空的 Long 接收 —— 原先把 null 传给 boolean 形参，拆箱即 NPE
                    long updatedRaw = rs.getLong("updated_at");
                    Long updatedAt = rs.wasNull() ? null : updatedRaw;
                    return new FileMeta(size, updatedAt, rs.getString("mime_type"), rs.getString("visibility"));
                },
                name, norm);
        if (metas.isEmpty()) {
            throw StorageException.notFound(norm);
        }
        FileMeta meta = metas.get(0);
        long sz = meta.size();
        Long updated = meta.updatedAt();
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
        List<String> values = jdbc.queryMapped(
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
        String pattern = dir.isEmpty() ? "%" : likeUnder(dir);
        return jdbc.queryMapped(
                "SELECT path FROM " + filesTable + " WHERE disk = ? AND path LIKE ? ESCAPE '\\'",
                rs -> rs.getString("path"), name, pattern);
    }

    /**
     * 构造「某目录之下」的 LIKE 模式，并<b>转义</b>目录名里的 LIKE 元字符。
     * <p>
     * 不转义会出事：{@code _} 在 LIKE 里是「任意单字符」通配符，
     * 于是 {@code deleteDirectory("user_files")} 会把 {@code userXfiles/…} 也一起删掉，
     * 且删除不可恢复（元信息与分片同时被删）。
     *
     * @param dir 已归一化的目录路径
     * @return LIKE 模式（配合各查询里的 {@code ESCAPE '\'}）
     */
    private static String likeUnder(String dir) {
        return dir.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "/%";
    }

    @Override
    public void makeDirectory(String directory) {
        // 数据库模式下目录是虚拟的，路径前缀天然「存在」，无需任何操作。
    }

    @Override
    public boolean deleteDirectory(String directory) {
        String norm = normalize(directory);
        boolean existed = exists(norm) || hasAnyUnder(norm);
        // LIKE 模式必须转义（见 likeUnder）：否则目录名里的 _ / % 会误伤同层其它目录
        String under = likeUnder(norm);
        jdbc.update("DELETE FROM " + chunksTable + " WHERE disk = ? AND (path = ? OR path LIKE ? ESCAPE '\\')",
                name, norm, under);
        jdbc.update("DELETE FROM " + filesTable + " WHERE disk = ? AND (path = ? OR path LIKE ? ESCAPE '\\')",
                name, norm, under);
        return existed;
    }

    private boolean hasAnyUnder(String norm) {
        List<Long> counts = jdbc.queryMapped(
                "SELECT COUNT(*) FROM " + filesTable + " WHERE disk = ? AND path LIKE ? ESCAPE '\\'",
                rs -> (long) rs.getInt(1), name, likeUnder(norm));
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

    /** 文件元信息行（{@link #info} 使用）：{@code updatedAt} 可为 null（updated_at 是可空列） */
    private record FileMeta(long size, Long updatedAt,
                            String mimeType, String visibility) {
    }
}
