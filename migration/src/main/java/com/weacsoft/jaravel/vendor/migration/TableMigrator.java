package com.weacsoft.jaravel.vendor.migration;

import com.weacsoft.jaravel.vendor.migration.dialect.Dialect;
import com.weacsoft.jaravel.vendor.migration.dialect.DialectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 跨数据库表迁移工具，支持两种迁移模式：
 *
 * <h3>1 步迁移（直连）</h3>
 * <p>源库和目标库同时在线，通过两个独立连接直接复制表结构和数据。
 * <pre>
 * TableMigrator migrator = new TableMigrator(sourceDs, targetDs);
 * migrator.migrateAll();                    // 迁移所有表（含 migrations 记录表）
 * migrator.migrateAll(false);               // 迁移所有用户表（不含 migrations 记录表）
 * migrator.migrateTables("users", "orders"); // 迁移指定表
 * </pre>
 *
 * <h3>2 步迁移（导出 → 导入）</h3>
 * <p>源库和目标库可能不在线、不互通，或需要跨时间段操作。
 * 第一步将源库表结构和数据导出为本地二进制文件（.jvd 格式），
 * 第二步在任意时间从文件导入到目标库。
 * <pre>
 * // 第一步：导出（只需源数据源）
 * TableMigrator exporter = new TableMigrator(sourceDs, null);
 * exporter.exportAll(new File("dump.jvd"));                    // 导出所有表（含 migrations）
 * exporter.exportTables(new File("dump.jvd"), "users", "orders"); // 导出指定表
 *
 * // 第二步：导入（只需目标数据源，可在不同时间调用）
 * TableMigrator importer = new TableMigrator(null, targetDs);
 * importer.importAll(new File("dump.jvd"));                    // 导入所有表
 * importer.importTables(new File("dump.jvd"), "users", "orders"); // 导入指定表
 * </pre>
 *
 * <h3>数据类型支持</h3>
 * <p>以 SQLite 兼容的基本类型为标准，额外支持以下类型：
 * <ul>
 *   <li><b>基本类型</b>：NULL、String/Text、Integer、Long、Boolean、Float、Double、BigDecimal</li>
 *   <li><b>日期时间</b>：Date、Time、Timestamp（以 ISO 字符串存储，兼容 SQLite TEXT）</li>
 *   <li><b>二进制</b>：byte[]（BLOB，额外支持）</li>
 * </ul>
 * 不支持 JSON、自定义对象等高级类型。
 *
 * <h3>连接管理</h3>
 * <p>1 步迁移使用两个独立的 JDBC 连接（源、目标分别从各自 DataSource 获取），
 * 不依赖跨数据库链接或 dblink。2 步迁移各阶段只需一个数据源。
 *
 * <h3>二进制文件格式（.jvd）</h3>
 * <pre>
 * Header:  magic(4) + version(4) + tableCount(4)
 * Per table:
 *   tableName(UTF) + columnCount(4)
 *   Per column: name(UTF) + jdbcType(4) + typeName(UTF) + size(4) + decimalDigits(4) + nullable(1) + autoIncrement(1)
 *   pkColumnCount(4) + per pk column: name(UTF)
 *   rowCount(4)
 *   Per row per column: typeMarker(1) + value(type-dependent)
 * </pre>
 */
public class TableMigrator {

    private static final Logger log = LoggerFactory.getLogger(TableMigrator.class);

    /** 文件魔数 "JAVD" */
    static final int MAGIC = 0x4A415644;
    /** 文件格式版本 */
    static final int VERSION = 1;
    /** 默认批量大小 */
    private static final int DEFAULT_BATCH_SIZE = 1000;

    // 值类型标记
    static final byte VAL_NULL = 0;
    static final byte VAL_STRING = 1;
    static final byte VAL_INT = 2;
    static final byte VAL_LONG = 3;
    static final byte VAL_BIG_DECIMAL = 4;
    static final byte VAL_BOOLEAN = 5;
    static final byte VAL_DATE = 6;
    static final byte VAL_TIMESTAMP = 7;
    static final byte VAL_TIME = 8;
    static final byte VAL_BYTES = 9;
    static final byte VAL_FLOAT = 10;
    static final byte VAL_DOUBLE = 11;

    private final DataSource sourceDataSource;
    private final DataSource targetDataSource;
    private final Dialect sourceDialect;
    private final Dialect targetDialect;
    private final JdbcExecutor targetJdbc;
    private final int batchSize;

    /**
     * 创建表迁移器（1 步迁移用）。
     *
     * @param sourceDataSource 源数据源（导出方）
     * @param targetDataSource 目标数据源（导入方）
     */
    public TableMigrator(DataSource sourceDataSource, DataSource targetDataSource) {
        this(sourceDataSource, targetDataSource, DEFAULT_BATCH_SIZE);
    }

    /**
     * 创建表迁移器，指定批量大小。
     *
     * @param sourceDataSource 源数据源（导出方，2步导入时可传 null）
     * @param targetDataSource 目标数据源（导入方，2步导出时可传 null）
     * @param batchSize        数据复制/导入的批量大小
     */
    public TableMigrator(DataSource sourceDataSource, DataSource targetDataSource, int batchSize) {
        this.sourceDataSource = sourceDataSource;
        this.targetDataSource = targetDataSource;
        this.sourceDialect = sourceDataSource != null ? DialectFactory.detect(sourceDataSource) : null;
        this.targetDialect = targetDataSource != null ? DialectFactory.detect(targetDataSource) : null;
        this.targetJdbc = targetDataSource != null ? new JdbcExecutor(targetDataSource) : null;
        this.batchSize = batchSize;
    }

    // ========================================================================
    // 1 步迁移：直连
    // ========================================================================

    /**
     * 迁移源数据库中的所有用户表到目标库（含 migrations 记录表）。
     *
     * @return 实际迁移的表数量
     */
    public int migrateAll() {
        return migrateAll(true);
    }

    /**
     * 迁移源数据库中的所有用户表到目标库。
     *
     * @param includeMigrationsTable 是否包含 migrations 记录表
     * @return 实际迁移的表数量
     */
    public int migrateAll(boolean includeMigrationsTable) {
        requireSource();
        requireTarget();
        List<String> tables = listSourceTables(includeMigrationsTable);
        log.info("[migration] 1步迁移: 发现 {} 张源表: {}", tables.size(), tables);
        int count = 0;
        for (String table : tables) {
            try {
                migrateTable(table);
                count++;
            } catch (Exception e) {
                log.error("[migration] 迁移表 {} 失败: {}", table, e.getMessage(), e);
            }
        }
        log.info("[migration] 1步迁移完成: 成功 {}/{} 张表", count, tables.size());
        return count;
    }

    /**
     * 迁移指定的多张表（1 步直连）。
     *
     * @param tableNames 表名列表
     * @return 实际迁移的表数量
     */
    public int migrateTables(String... tableNames) {
        requireSource();
        requireTarget();
        int count = 0;
        for (String table : tableNames) {
            try {
                migrateTable(table);
                count++;
            } catch (Exception e) {
                log.error("[migration] 迁移表 {} 失败: {}", table, e.getMessage(), e);
            }
        }
        return count;
    }

    /**
     * 迁移单张表（1 步直连）：读源表结构 → 目标库建表 → 批量复制数据。
     * <p>
     * 使用两个独立连接：从源 DataSource 读取，写入目标 DataSource。
     *
     * @param tableName 表名
     */
    public void migrateTable(String tableName) {
        requireSource();
        requireTarget();
        log.info("[migration] 1步迁移表: {} (源: {} → 目标: {})",
            tableName, sourceDialect.getName(), targetDialect.getName());

        TableMetaData metaData = readTableMetaData(tableName);
        log.info("[migration] 源表 {}: {} 列, 主键: {}", tableName, metaData.columns.size(), metaData.primaryKeyColumns);

        dropTargetTableIfExists(tableName);
        createTargetTable(tableName, metaData);
        int rowCount = copyDataDirect(tableName, metaData);
        log.info("[migration] 表 {} 迁移完成, {} 行", tableName, rowCount);
    }

    // ========================================================================
    // 2 步迁移 - 第一步：导出到本地文件
    // ========================================================================

    /**
     * 将源库所有表导出为本地二进制文件（含 migrations 记录表）。
     *
     * @param file 目标文件
     * @return 实际导出的表数量
     */
    public int exportAll(File file) {
        return exportAll(file, true);
    }

    /**
     * 将源库所有表导出为本地二进制文件。
     *
     * @param file                   目标文件
     * @param includeMigrationsTable 是否包含 migrations 记录表
     * @return 实际导出的表数量
     */
    public int exportAll(File file, boolean includeMigrationsTable) {
        requireSource();
        List<String> tables = listSourceTables(includeMigrationsTable);
        log.info("[migration] 导出: 发现 {} 张源表: {}", tables.size(), tables);
        return exportTables(file, tables.toArray(new String[0]));
    }

    /**
     * 将源库指定表导出为本地二进制文件。
     *
     * @param file       目标文件
     * @param tableNames 表名列表
     * @return 实际导出的表数量
     */
    public int exportTables(File file, String... tableNames) {
        requireSource();
        List<TableMetaData> tablesMeta = new ArrayList<>();
        for (String name : tableNames) {
            tablesMeta.add(readTableMetaData(name));
        }
        log.info("[migration] 导出 {} 张表到文件: {}", tablesMeta.size(), file.getAbsolutePath());

        int exported = 0;
        try (FileOutputStream fos = new FileOutputStream(file);
             DataOutputStream dos = new DataOutputStream(fos)) {

            dos.writeInt(MAGIC);
            dos.writeInt(VERSION);
            dos.writeInt(tablesMeta.size());

            for (TableMetaData meta : tablesMeta) {
                try {
                    exportTable(dos, meta);
                    exported++;
                    log.info("[migration] 导出表: {} ({} 列)", meta.tableName, meta.columns.size());
                } catch (Exception e) {
                    log.error("[migration] 导出表 {} 失败: {}", meta.tableName, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("导出文件失败: " + file.getAbsolutePath(), e);
        }
        log.info("[migration] 导出完成: {}/{} 张表 → {}", exported, tablesMeta.size(), file.getName());
        return exported;
    }

    // ========================================================================
    // 2 步迁移 - 第二步：从本地文件导入
    // ========================================================================

    /**
     * 从本地二进制文件导入所有表到目标库。
     *
     * @param file 源文件
     * @return 实际导入的表数量
     */
    public int importAll(File file) {
        requireTarget();
        List<String> tableNames = listTablesInDump(file);
        log.info("[migration] 导入: 文件中包含 {} 张表: {}", tableNames.size(), tableNames);
        return importTables(file, tableNames.toArray(new String[0]));
    }

    /**
     * 从本地二进制文件导入指定表到目标库。
     *
     * @param file       源文件
     * @param tableNames 要导入的表名列表
     * @return 实际导入的表数量
     */
    public int importTables(File file, String... tableNames) {
        requireTarget();
        // 大小写不敏感匹配
        java.util.Set<String> wanted = java.util.Arrays.stream(tableNames)
            .map(String::toLowerCase)
            .collect(java.util.stream.Collectors.toSet());
        int imported = 0;

        try (FileInputStream fis = new FileInputStream(file);
             DataInputStream dis = new DataInputStream(fis)) {

            int magic = dis.readInt();
            if (magic != MAGIC) {
                throw new RuntimeException("无效的文件格式: 魔数不匹配");
            }
            int version = dis.readInt();
            if (version != VERSION) {
                throw new RuntimeException("不支持的文件版本: " + version);
            }
            int tableCount = dis.readInt();
            log.info("[migration] 文件版本 {}, 包含 {} 张表", version, tableCount);

            for (int i = 0; i < tableCount; i++) {
                TableMetaData meta = readTableMetaFromDump(dis);
                if (!wanted.contains(meta.tableName.toLowerCase())) {
                    // 跳过不需要的表：仍需读取数据流以保持位置正确
                    skipTableData(dis, meta);
                    log.debug("[migration] 跳过表: {}", meta.tableName);
                    continue;
                }
                try {
                    importTable(dis, meta);
                    imported++;
                } catch (Exception e) {
                    log.error("[migration] 导入表 {} 失败: {}", meta.tableName, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("导入文件失败: " + file.getAbsolutePath(), e);
        }
        log.info("[migration] 导入完成: {}/{} 张表 ← {}", imported, wanted.size(), file.getName());
        return imported;
    }

    // ========================================================================
    // 导出内部实现
    // ========================================================================

    /** 导出单张表到 DataOutputStream（先查行数，再写数据） */
    private void exportTable(DataOutputStream dos, TableMetaData meta) throws SQLException, IOException {
        // 写表元数据
        dos.writeUTF(meta.tableName);
        dos.writeInt(meta.columns.size());
        for (ColumnMetaData col : meta.columns) {
            dos.writeUTF(col.name);
            dos.writeInt(col.jdbcType);
            dos.writeUTF(col.typeName != null ? col.typeName : "");
            dos.writeInt(col.size);
            dos.writeInt(col.decimalDigits);
            dos.writeBoolean(col.nullable);
            dos.writeBoolean(col.autoIncrement);
        }
        dos.writeInt(meta.primaryKeyColumns.size());
        for (String pk : meta.primaryKeyColumns) {
            dos.writeUTF(pk);
        }

        // 先查询行数（DataOutputStream 不支持 seek 回补，所以先查再写）
        String countSql = "SELECT COUNT(*) FROM " + sourceDialect.quote(meta.tableName);
        int rowCount;
        try (Connection conn = sourceDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            rs.next();
            rowCount = rs.getInt(1);
        }

        // 写行数
        dos.writeInt(rowCount);

        // 读源表数据并写入
        String quotedCols = meta.columns.stream()
            .map(c -> sourceDialect.quote(c.name))
            .collect(Collectors.joining(", "));
        String selectSql = "SELECT " + quotedCols + " FROM " + sourceDialect.quote(meta.tableName);

        int written = 0;
        try (Connection conn = sourceDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql)) {

            while (rs.next()) {
                for (int i = 0; i < meta.columns.size(); i++) {
                    writeValue(dos, rs, meta.columns.get(i), i + 1);
                }
                written++;
            }
        }
        log.debug("[migration] 导出表 {}: 写入 {} 行 (预期 {})", meta.tableName, written, rowCount);
    }

    /** 从 ResultSet 写入单个值到 DataOutputStream */
    private void writeValue(DataOutputStream dos, ResultSet rs, ColumnMetaData col, int colIndex)
            throws SQLException, IOException {
        Object value = rs.getObject(colIndex);
        if (value == null) {
            dos.writeByte(VAL_NULL);
            return;
        }

        switch (col.jdbcType) {
            case Types.VARCHAR:
            case Types.NVARCHAR:
            case Types.CHAR:
            case Types.NCHAR:
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.CLOB:
                dos.writeByte(VAL_STRING);
                dos.writeUTF(rs.getString(colIndex));
                break;
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                dos.writeByte(VAL_INT);
                dos.writeInt(rs.getInt(colIndex));
                break;
            case Types.BIGINT:
                dos.writeByte(VAL_LONG);
                dos.writeLong(rs.getLong(colIndex));
                break;
            case Types.BOOLEAN:
            case Types.BIT:
                dos.writeByte(VAL_BOOLEAN);
                dos.writeBoolean(rs.getBoolean(colIndex));
                break;
            case Types.FLOAT:
            case Types.REAL:
                dos.writeByte(VAL_FLOAT);
                dos.writeFloat(rs.getFloat(colIndex));
                break;
            case Types.DOUBLE:
                dos.writeByte(VAL_DOUBLE);
                dos.writeDouble(rs.getDouble(colIndex));
                break;
            case Types.DECIMAL:
            case Types.NUMERIC:
                String decimalStr = rs.getString(colIndex);
                dos.writeByte(VAL_BIG_DECIMAL);
                dos.writeUTF(decimalStr != null ? decimalStr : "");
                break;
            case Types.DATE:
                Date date = rs.getDate(colIndex);
                dos.writeByte(VAL_DATE);
                dos.writeUTF(date.toString());
                break;
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                Timestamp ts = rs.getTimestamp(colIndex);
                dos.writeByte(VAL_TIMESTAMP);
                dos.writeUTF(ts.toString());
                break;
            case Types.TIME:
                Time time = rs.getTime(colIndex);
                dos.writeByte(VAL_TIME);
                dos.writeUTF(time.toString());
                break;
            case Types.BLOB:
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                byte[] bytes = rs.getBytes(colIndex);
                dos.writeByte(VAL_BYTES);
                if (bytes == null) {
                    dos.writeInt(0);
                } else {
                    dos.writeInt(bytes.length);
                    dos.write(bytes);
                }
                break;
            default:
                // 未知类型，尝试作为字符串
                String fallback = rs.getString(colIndex);
                if (fallback == null) {
                    dos.writeByte(VAL_NULL);
                } else {
                    dos.writeByte(VAL_STRING);
                    dos.writeUTF(fallback);
                }
                break;
        }
    }

    // ========================================================================
    // 导入内部实现
    // ========================================================================

    /** 从文件中列出所有表名（不读取数据） */
    private List<String> listTablesInDump(File file) {
        List<String> names = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             DataInputStream dis = new DataInputStream(fis)) {
            int magic = dis.readInt();
            if (magic != MAGIC) throw new RuntimeException("无效的文件格式");
            dis.readInt(); // version
            int tableCount = dis.readInt();
            for (int i = 0; i < tableCount; i++) {
                String name = dis.readUTF();
                names.add(name);
                // 跳过列定义、主键、数据
                int colCount = dis.readInt();
                for (int c = 0; c < colCount; c++) {
                    dis.readUTF(); // name
                    dis.readInt(); // jdbcType
                    dis.readUTF(); // typeName
                    dis.readInt(); // size
                    dis.readInt(); // decimalDigits
                    dis.readBoolean(); // nullable
                    dis.readBoolean(); // autoIncrement
                }
                int pkCount = dis.readInt();
                for (int p = 0; p < pkCount; p++) {
                    dis.readUTF();
                }
                int rowCount = dis.readInt();
                // 跳过所有行的所有列
                for (int r = 0; r < rowCount; r++) {
                    for (int c = 0; c < colCount; c++) {
                        skipValue(dis);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + file.getAbsolutePath(), e);
        }
        return names;
    }

    /** 从 DataInputStream 读取表元数据 */
    private TableMetaData readTableMetaFromDump(DataInputStream dis) throws IOException {
        TableMetaData meta = new TableMetaData();
        meta.tableName = dis.readUTF();
        int colCount = dis.readInt();
        meta.columns = new ArrayList<>();
        for (int i = 0; i < colCount; i++) {
            ColumnMetaData col = new ColumnMetaData();
            col.name = dis.readUTF();
            col.jdbcType = dis.readInt();
            col.typeName = dis.readUTF();
            col.size = dis.readInt();
            col.decimalDigits = dis.readInt();
            col.nullable = dis.readBoolean();
            col.autoIncrement = dis.readBoolean();
            meta.columns.add(col);
        }
        int pkCount = dis.readInt();
        meta.primaryKeyColumns = new ArrayList<>();
        for (int i = 0; i < pkCount; i++) {
            meta.primaryKeyColumns.add(dis.readUTF());
        }
        meta.rowCount = dis.readInt();
        return meta;
    }

    /** 跳过表的全部数据（不导入） */
    private void skipTableData(DataInputStream dis, TableMetaData meta) throws IOException {
        for (int r = 0; r < meta.rowCount; r++) {
            for (int c = 0; c < meta.columns.size(); c++) {
                skipValue(dis);
            }
        }
    }

    /** 跳过单个值 */
    private void skipValue(DataInputStream dis) throws IOException {
        byte typeMarker = dis.readByte();
        switch (typeMarker) {
            case VAL_NULL: break;
            case VAL_STRING: dis.readUTF(); break;
            case VAL_INT: dis.readInt(); break;
            case VAL_LONG: dis.readLong(); break;
            case VAL_BIG_DECIMAL: dis.readUTF(); break;
            case VAL_BOOLEAN: dis.readBoolean(); break;
            case VAL_DATE: dis.readUTF(); break;
            case VAL_TIMESTAMP: dis.readUTF(); break;
            case VAL_TIME: dis.readUTF(); break;
            case VAL_BYTES:
                int len = dis.readInt();
                dis.skipBytes(len);
                break;
            case VAL_FLOAT: dis.readFloat(); break;
            case VAL_DOUBLE: dis.readDouble(); break;
            default: throw new IOException("未知的值类型标记: " + typeMarker);
        }
    }

    /** 导入单张表（从 DataInputStream 读取数据，写入目标库） */
    private void importTable(DataInputStream dis, TableMetaData meta) throws IOException {
        log.info("[migration] 导入表: {} ({} 列, {} 行)", meta.tableName, meta.columns.size(), meta.rowCount);

        // 1. 在目标库建表
        dropTargetTableIfExists(meta.tableName);
        createTargetTable(meta.tableName, meta);

        // 2. 批量导入数据
        String targetQuotedCols = meta.columns.stream()
            .map(c -> targetDialect.quote(c.name))
            .collect(Collectors.joining(", "));
        String placeholders = meta.columns.stream()
            .map(c -> "?").collect(Collectors.joining(", "));
        String insertSql = "INSERT INTO " + targetDialect.quote(meta.tableName)
            + " (" + targetQuotedCols + ") VALUES (" + placeholders + ")";

        boolean isSqlServer = targetDialect.getName().contains("sql server");
        int imported = 0;

        try (Connection targetConn = targetDataSource.getConnection()) {
            // SQL Server 需要开启 IDENTITY_INSERT
            if (isSqlServer && hasAutoIncrementColumn(meta)) {
                try (Statement stmt = targetConn.createStatement()) {
                    stmt.execute("SET IDENTITY_INSERT " + targetDialect.quote(meta.tableName) + " ON");
                }
            }

            targetConn.setAutoCommit(false);
            try (PreparedStatement ps = targetConn.prepareStatement(insertSql)) {
                int batchCount = 0;
                for (int r = 0; r < meta.rowCount; r++) {
                    for (int c = 0; c < meta.columns.size(); c++) {
                        Object value = readValue(dis, meta.columns.get(c));
                        ps.setObject(c + 1, value);
                    }
                    ps.addBatch();
                    batchCount++;
                    imported++;

                    if (batchCount >= batchSize) {
                        ps.executeBatch();
                        targetConn.commit();
                        batchCount = 0;
                    }
                }
                if (batchCount > 0) {
                    ps.executeBatch();
                    targetConn.commit();
                }
            } catch (SQLException e) {
                targetConn.rollback();
                throw new RuntimeException("导入数据失败: " + meta.tableName, e);
            }

            if (isSqlServer && hasAutoIncrementColumn(meta)) {
                try (Statement stmt = targetConn.createStatement()) {
                    stmt.execute("SET IDENTITY_INSERT " + targetDialect.quote(meta.tableName) + " OFF");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("导入表失败: " + meta.tableName, e);
        }

        log.info("[migration] 导入表 {} 完成: {} 行", meta.tableName, imported);
    }

    /** 从 DataInputStream 读取单个值 */
    private Object readValue(DataInputStream dis, ColumnMetaData col) throws IOException {
        byte typeMarker = dis.readByte();
        if (typeMarker == VAL_NULL) {
            return null;
        }
        switch (typeMarker) {
            case VAL_STRING: return dis.readUTF();
            case VAL_INT: return dis.readInt();
            case VAL_LONG: return dis.readLong();
            case VAL_BIG_DECIMAL:
                String decimalStr = dis.readUTF();
                return decimalStr.isEmpty() ? null : new BigDecimal(decimalStr);
            case VAL_BOOLEAN: return dis.readBoolean();
            case VAL_DATE: return Date.valueOf(dis.readUTF());
            case VAL_TIMESTAMP: return Timestamp.valueOf(dis.readUTF());
            case VAL_TIME: return Time.valueOf(dis.readUTF());
            case VAL_BYTES:
                int len = dis.readInt();
                if (len == 0) return new byte[0];
                byte[] bytes = new byte[len];
                dis.readFully(bytes);
                return bytes;
            case VAL_FLOAT: return dis.readFloat();
            case VAL_DOUBLE: return dis.readDouble();
            default: throw new IOException("未知的值类型标记: " + typeMarker);
        }
    }

    // ========================================================================
    // 共用内部方法
    // ========================================================================

    /**
     * 列出源数据库中的所有用户表。
     * <p>
     * 使用方言感知的 catalog/schema 获取方式，兼容 MySQL、PostgreSQL、Oracle、SQL Server 等。
     * 当 getTables(catalog, schema, ...) 查不到表时，回退到 null/null 查询全部。
     */
    private List<String> listSourceTables(boolean includeMigrationsTable) {
        List<String> tables = new ArrayList<>();
        try (Connection conn = sourceDataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            // 先用方言感知方式获取 catalog/schema
            try (ResultSet rs = meta.getTables(getEffectiveCatalog(conn), getEffectiveSchema(conn), "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name == null || name.isEmpty()) continue;
                    String lower = name.toLowerCase();
                    if (lower.startsWith("sqlite_")) continue;
                    if (!includeMigrationsTable && lower.equals("migrations")) continue;
                    tables.add(name);
                }
            }
            // 如果方言感知方式查不到表，回退到 null/null
            if (tables.isEmpty()) {
                log.debug("[migration] 方言感知方式未找到表，回退到 null/null 查询");
                try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String name = rs.getString("TABLE_NAME");
                        if (name == null || name.isEmpty()) continue;
                        String lower = name.toLowerCase();
                        if (lower.startsWith("sqlite_")) continue;
                        if (!includeMigrationsTable && lower.equals("migrations")) continue;
                        tables.add(name);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("无法获取源数据库表列表", e);
        }
        return tables;
    }

    /** 读取源表结构（列定义 + 主键），表名大小写不敏感 */
    private TableMetaData readTableMetaData(String tableName) {
        TableMetaData meta = new TableMetaData();
        meta.columns = new ArrayList<>();
        meta.primaryKeyColumns = new ArrayList<>();

        try (Connection conn = sourceDataSource.getConnection()) {
            DatabaseMetaData dbMeta = conn.getMetaData();
            String catalog = getEffectiveCatalog(conn);
            String schema = getEffectiveSchema(conn);

            // 先用原始表名尝试，如果找不到则进行大小写不敏感匹配
            try (ResultSet rs = dbMeta.getColumns(catalog, schema, tableName, null)) {
                while (rs.next()) {
                    ColumnMetaData col = new ColumnMetaData();
                    col.name = rs.getString("COLUMN_NAME");
                    col.jdbcType = rs.getInt("DATA_TYPE");
                    col.typeName = rs.getString("TYPE_NAME");
                    col.size = rs.getInt("COLUMN_SIZE");
                    col.decimalDigits = rs.getInt("DECIMAL_DIGITS");
                    col.nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                    col.defaultValue = rs.getString("COLUMN_DEF");
                    String autoInc = rs.getString("IS_AUTOINCREMENT");
                    col.autoIncrement = "YES".equalsIgnoreCase(autoInc);
                    meta.columns.add(col);
                }
            }

            // 如果原始表名找不到列，尝试大小写不敏感匹配
            if (meta.columns.isEmpty()) {
                // 回退到 null/null 查询
                if (catalog != null || schema != null) {
                    try (ResultSet rs = dbMeta.getColumns(null, null, tableName, null)) {
                        while (rs.next()) {
                            ColumnMetaData col = new ColumnMetaData();
                            col.name = rs.getString("COLUMN_NAME");
                            col.jdbcType = rs.getInt("DATA_TYPE");
                            col.typeName = rs.getString("TYPE_NAME");
                            col.size = rs.getInt("COLUMN_SIZE");
                            col.decimalDigits = rs.getInt("DECIMAL_DIGITS");
                            col.nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                            col.defaultValue = rs.getString("COLUMN_DEF");
                            String autoInc = rs.getString("IS_AUTOINCREMENT");
                            col.autoIncrement = "YES".equalsIgnoreCase(autoInc);
                            meta.columns.add(col);
                        }
                    }
                }

                // 如果还是找不到，尝试大小写不敏感匹配
                if (meta.columns.isEmpty()) {
                    String actualName = findActualTableName(conn, tableName);
                    if (actualName != null) {
                        meta.tableName = actualName;
                        readColumnsAndPrimaryKeys(dbMeta, conn, actualName, meta);
                    }
                }
            } else {
                meta.tableName = tableName;
                try (ResultSet rs = dbMeta.getPrimaryKeys(catalog, schema, tableName)) {
                    while (rs.next()) {
                        meta.primaryKeyColumns.add(rs.getString("COLUMN_NAME"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("无法读取源表结构: " + tableName, e);
        }

        if (meta.columns.isEmpty()) {
            throw new RuntimeException("源表不存在或无列定义: " + tableName);
        }
        return meta;
    }

    /** 读取列定义和主键（使用 null/null 回退） */
    private void readColumnsAndPrimaryKeys(DatabaseMetaData dbMeta, Connection conn,
                                           String tableName, TableMetaData meta) throws SQLException {
        try (ResultSet rs = dbMeta.getColumns(getEffectiveCatalog(conn), getEffectiveSchema(conn), tableName, null)) {
            while (rs.next()) {
                ColumnMetaData col = new ColumnMetaData();
                col.name = rs.getString("COLUMN_NAME");
                col.jdbcType = rs.getInt("DATA_TYPE");
                col.typeName = rs.getString("TYPE_NAME");
                col.size = rs.getInt("COLUMN_SIZE");
                col.decimalDigits = rs.getInt("DECIMAL_DIGITS");
                col.nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                col.defaultValue = rs.getString("COLUMN_DEF");
                String autoInc = rs.getString("IS_AUTOINCREMENT");
                col.autoIncrement = "YES".equalsIgnoreCase(autoInc);
                meta.columns.add(col);
            }
        }
        if (meta.columns.isEmpty()) {
            try (ResultSet rs = dbMeta.getColumns(null, null, tableName, null)) {
                while (rs.next()) {
                    ColumnMetaData col = new ColumnMetaData();
                    col.name = rs.getString("COLUMN_NAME");
                    col.jdbcType = rs.getInt("DATA_TYPE");
                    col.typeName = rs.getString("TYPE_NAME");
                    col.size = rs.getInt("COLUMN_SIZE");
                    col.decimalDigits = rs.getInt("DECIMAL_DIGITS");
                    col.nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                    col.defaultValue = rs.getString("COLUMN_DEF");
                    String autoInc = rs.getString("IS_AUTOINCREMENT");
                    col.autoIncrement = "YES".equalsIgnoreCase(autoInc);
                    meta.columns.add(col);
                }
            }
        }
        try (ResultSet rs = dbMeta.getPrimaryKeys(getEffectiveCatalog(conn), getEffectiveSchema(conn), tableName)) {
            while (rs.next()) {
                meta.primaryKeyColumns.add(rs.getString("COLUMN_NAME"));
            }
        }
        if (meta.primaryKeyColumns.isEmpty()) {
            try (ResultSet rs = dbMeta.getPrimaryKeys(null, null, tableName)) {
                while (rs.next()) {
                    meta.primaryKeyColumns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
    }

    /**
     * 大小写不敏感地查找实际表名，使用 null/null 回退
     */
    private String findActualTableName(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData dbMeta = conn.getMetaData();
        // 先用方言感知方式
        try (ResultSet rs = dbMeta.getTables(getEffectiveCatalog(conn), getEffectiveSchema(conn), "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && name.equalsIgnoreCase(tableName)) {
                    return name;
                }
            }
        }
        // 回退到 null/null
        try (ResultSet rs = dbMeta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && name.equalsIgnoreCase(tableName)) {
                    return name;
                }
            }
        }
        return null;
    }

    /**
     * 删除目标表（如存在）。
     * <p>
     * 对于支持 DROP TABLE IF EXISTS 的方言直接执行；
     * 对于不支持的方言（Oracle），先通过 hasTable 检查再执行 DROP。
     */
    private void dropTargetTableIfExists(String tableName) {
        String targetName = targetDialect.getName();
        if (targetName.contains("oracle")) {
            // Oracle 不支持 DROP TABLE IF EXISTS
            try {
                String hasSql = targetDialect.hasTableSql();
                if (hasSql != null) {
                    Integer cnt = targetJdbc.queryForObject(hasSql, Integer.class, tableName);
                    if (cnt != null && cnt > 0) {
                        targetJdbc.execute("DROP TABLE " + targetDialect.quote(tableName));
                    }
                    return;
                }
            } catch (Exception e) {
                log.warn("[migration] Oracle 检查表存在性失败，尝试直接 DROP: {}", e.getMessage());
            }
            try {
                targetJdbc.execute("DROP TABLE " + targetDialect.quote(tableName));
            } catch (Exception e) {
                log.debug("[migration] 表 {} 不存在或删除失败（忽略）: {}", tableName, e.getMessage());
            }
        } else {
            try {
                targetJdbc.execute("DROP TABLE IF EXISTS " + targetDialect.quote(tableName));
            } catch (Exception e) {
                log.warn("[migration] 删除目标表 {} 时忽略错误: {}", tableName, e.getMessage());
            }
        }
    }

    /** 在目标库创建表 */
    private void createTargetTable(String tableName, TableMetaData meta) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(targetDialect.quote(tableName)).append(" (");

        List<String> columnDefs = new ArrayList<>();
        for (ColumnMetaData col : meta.columns) {
            columnDefs.add(buildColumnDef(col, meta));
        }

        if (meta.primaryKeyColumns.size() > 1) {
            StringBuilder pk = new StringBuilder("PRIMARY KEY (");
            for (int i = 0; i < meta.primaryKeyColumns.size(); i++) {
                if (i > 0) pk.append(", ");
                pk.append(targetDialect.quote(meta.primaryKeyColumns.get(i)));
            }
            pk.append(")");
            columnDefs.add(pk.toString());
        }

        sb.append(String.join(", ", columnDefs));
        sb.append(")");
        sb.append(targetDialect.tableOptions());

        log.debug("[migration] CREATE TABLE SQL: {}", sb);
        targetJdbc.execute(sb.toString());
    }

    /** 构建单个列的目标库定义 */
    private String buildColumnDef(ColumnMetaData col, TableMetaData meta) {
        StringBuilder sb = new StringBuilder();
        sb.append(targetDialect.quote(col.name)).append(" ");

        boolean isPk = meta.primaryKeyColumns.contains(col.name);

        // 自增主键：按目标方言生成
        if (col.autoIncrement && isPk) {
            String logicalType = jdbcTypeToLogicalType(col.jdbcType);
            String baseType = targetDialect.mapType(logicalType, col.size, null, null, false);
            String targetName = targetDialect.getName();

            if (targetName.contains("postgresql")) {
                sb.append(baseType).append(" GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY");
            } else if (targetName.contains("oracle")) {
                sb.append("NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY");
            } else if (targetName.contains("sqlite")) {
                sb.append("INTEGER PRIMARY KEY AUTOINCREMENT");
            } else if (targetName.contains("sql server")) {
                sb.append(baseType).append(" IDENTITY(1,1) PRIMARY KEY");
            } else {
                sb.append(baseType).append(" AUTO_INCREMENT PRIMARY KEY");
            }
            return sb.toString();
        }

        // 普通列：JDBC 类型 → 逻辑类型 → 目标方言类型
        String logicalType = jdbcTypeToLogicalType(col.jdbcType);
        String targetType;
        if (logicalType != null) {
            Integer length = null, precision = null, scale = null;
            if ("string".equals(logicalType) || "char".equals(logicalType)) {
                length = col.size;
            } else if ("decimal".equals(logicalType)) {
                precision = col.size;
                scale = col.decimalDigits;
            }
            targetType = targetDialect.mapType(logicalType, length, precision, scale, false);
        } else {
            log.warn("[migration] 未知 JDBC 类型 {} ({}), 使用源类型名 {}",
                col.jdbcType, col.typeName, col.typeName);
            targetType = col.typeName;
        }
        sb.append(targetType);

        if (!col.nullable) {
            sb.append(" NOT NULL");
        }
        return sb.toString();
    }

    /** 1 步迁移：直接复制数据（两个独立连接，源用实际表名，目标用参数表名） */
    private int copyDataDirect(String tableName, TableMetaData meta) {
        List<String> columnNames = meta.columns.stream()
            .map(c -> c.name).collect(Collectors.toList());

        // 源库查询使用 meta.tableName（实际大小写）
        String sourceQuotedCols = columnNames.stream()
            .map(sourceDialect::quote).collect(Collectors.joining(", "));
        String selectSql = "SELECT " + sourceQuotedCols + " FROM " + sourceDialect.quote(meta.tableName);

        // 目标库插入使用 tableName（用户指定的名称）
        String targetQuotedCols = columnNames.stream()
            .map(targetDialect::quote).collect(Collectors.joining(", "));
        String placeholders = columnNames.stream()
            .map(c -> "?").collect(Collectors.joining(", "));
        String insertSql = "INSERT INTO " + targetDialect.quote(tableName)
            + " (" + targetQuotedCols + ") VALUES (" + placeholders + ")";

        boolean isSqlServer = targetDialect.getName().contains("sql server");
        int totalRows = 0;

        try (Connection sourceConn = sourceDataSource.getConnection();
             Statement sourceStmt = sourceConn.createStatement();
             ResultSet rs = sourceStmt.executeQuery(selectSql);
             Connection targetConn = targetDataSource.getConnection()) {

            if (isSqlServer && hasAutoIncrementColumn(meta)) {
                try (Statement stmt = targetConn.createStatement()) {
                    stmt.execute("SET IDENTITY_INSERT " + targetDialect.quote(tableName) + " ON");
                }
            }

            targetConn.setAutoCommit(false);
            try (PreparedStatement targetPs = targetConn.prepareStatement(insertSql)) {
                int batchCount = 0;
                while (rs.next()) {
                    for (int i = 0; i < columnNames.size(); i++) {
                        targetPs.setObject(i + 1, rs.getObject(i + 1));
                    }
                    targetPs.addBatch();
                    batchCount++;
                    totalRows++;
                    if (batchCount >= batchSize) {
                        targetPs.executeBatch();
                        targetConn.commit();
                        batchCount = 0;
                        log.debug("[migration] {} 已复制 {} 行", tableName, totalRows);
                    }
                }
                if (batchCount > 0) {
                    targetPs.executeBatch();
                    targetConn.commit();
                }
            } catch (SQLException e) {
                targetConn.rollback();
                throw e;
            }

            if (isSqlServer && hasAutoIncrementColumn(meta)) {
                try (Statement stmt = targetConn.createStatement()) {
                    stmt.execute("SET IDENTITY_INSERT " + targetDialect.quote(tableName) + " OFF");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("数据复制失败: " + tableName, e);
        }
        return totalRows;
    }

    /** 是否有自增列 */
    private boolean hasAutoIncrementColumn(TableMetaData meta) {
        return meta.columns.stream().anyMatch(c -> c.autoIncrement);
    }

    /**
     * 获取有效的 catalog（数据库名），用于 DatabaseMetaData 查询。
     * <p>
     * MySQL/SQL Server 使用 catalog 定位数据库；Oracle 的 catalog 返回 null，
     * 此时返回 null 让 getTables 用 null 匹配所有 catalog。
     */
    private static String getEffectiveCatalog(Connection conn) throws SQLException {
        try {
            return conn.getCatalog();
        } catch (SQLException e) {
            log.debug("[migration] getCatalog() 失败，返回 null: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取有效的 schema，用于 DatabaseMetaData 查询。
     * <p>
     * PostgreSQL 返回 "public"，Oracle 返回用户名（如 "SCOTT"），
     * SQL Server 返回 "dbo"，MySQL 返回 null，H2 返回 "PUBLIC"。
     * 如果获取失败则返回 null。
     */
    private static String getEffectiveSchema(Connection conn) throws SQLException {
        try {
            return conn.getSchema();
        } catch (SQLException e) {
            log.debug("[migration] getSchema() 失败，返回 null: {}", e.getMessage());
            return null;
        }
    }

    /** JDBC 类型 → 逻辑类型名 */
    static String jdbcTypeToLogicalType(int jdbcType) {
        switch (jdbcType) {
            case Types.VARCHAR:
            case Types.NVARCHAR:
                return "string";
            case Types.CHAR:
            case Types.NCHAR:
                return "char";
            case Types.INTEGER:
                return "integer";
            case Types.BIGINT:
                return "bigInteger";
            case Types.SMALLINT:
                return "smallInteger";
            case Types.TINYINT:
                return "tinyInteger";
            case Types.BOOLEAN:
            case Types.BIT:
                return "boolean";
            case Types.DECIMAL:
            case Types.NUMERIC:
                return "decimal";
            case Types.FLOAT:
            case Types.REAL:
                return "float";
            case Types.DOUBLE:
                return "double";
            case Types.DATE:
                return "date";
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return "timestamp";
            case Types.TIME:
                return "time";
            case Types.BLOB:
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return "binary";
            case Types.CLOB:
            case Types.NCLOB:
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
                return "text";
            default:
                return null;
        }
    }

    private void requireSource() {
        if (sourceDataSource == null) {
            throw new IllegalStateException("源数据源未设置（导出/1步迁移需要源数据源）");
        }
    }

    private void requireTarget() {
        if (targetDataSource == null) {
            throw new IllegalStateException("目标数据源未设置（导入/1步迁移需要目标数据源）");
        }
    }

    // ==================== 内部数据结构 ====================

    /** 源表元数据 */
    static class TableMetaData {
        String tableName;
        List<ColumnMetaData> columns;
        List<String> primaryKeyColumns;
        int rowCount; // 仅导入时从文件读取
    }

    /** 源列元数据 */
    static class ColumnMetaData {
        String name;
        int jdbcType;
        String typeName;
        int size;
        int decimalDigits;
        boolean nullable;
        String defaultValue;
        boolean autoIncrement;
    }
}
