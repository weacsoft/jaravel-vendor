package com.weacsoft.jaravel.vendor.storage.database;

import com.weacsoft.jaravel.vendor.storage.StorageException;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.FilesystemDriver;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;

import javax.sql.DataSource;

import java.util.Map;

/**
 * 数据库文件存储驱动（工厂），支持将文件存入关系型数据库。
 * <p>
 * 支持 {@code driver: database}，由 {@code StorageAutoConfiguration} 自动注册为 Bean，
 * 并被 {@code StorageManager} 自动收集，业务方无需手动注册。
 *
 * <h3>支持的配置键（放在 disk 的 options 或直接在 disk 下均可）</h3>
 * <ul>
 *   <li><b>binary</b> — 是否以二进制（BLOB）存放；{@code false} 时改为 base64 文本（LONGTEXT）。
 *       默认 {@code true}。当数据库不支持二进制（或二进制列不便使用时）可关闭。</li>
 *   <li><b>content-column</b> — 存放文件内容的列名，默认 {@code content}。允许自行指定列名。
 *       该列的类型由 {@code binary} 决定：{@code binary=true} 时为 BLOB，否则为 LONGTEXT。
 *       不再区分 binary/text 双列，文件内容统一落在该列。</li>
 *   <li><b>chunk-size</b> — 单条记录（分片）字节上限；超过则按此大小切分为多条记录再组装。
 *       默认 {@code 1048576}（1MB）。设为 {@code 0} 或负数表示不切分，整文件存为单条记录。
 *       用于规避数据库对单条记录大小的常见限制（一般 4G）。base64 模式下文本约为二进制的 4/3，
 *       如需贴合 DB 记录上限，请将 chunk-size 相应调小。</li>
 *   <li><b>table-prefix</b> — 数据表前缀，默认 {@code storage_}。</li>
 *   <li><b>datasource</b> — 可选，指定的 {@link DataSource} Bean 名称。不配置时使用主数据源
 *       （即项目当前的数据源）。配置后可实现「用单独的数据库存文件」做多机同步。</li>
 *   <li><b>visibility</b> — 默认可见性，默认 {@code private}。</li>
 * </ul>
 *
 * <h3>数据表</h3>
 * 每个磁盘共用两张表（通过 {@code disk} 列区分）：
 * <ul>
 *   <li><b>prefix + file</b> — 文件元信息（路径、大小、分片数、存放方式、MIME、可见性等）。</li>
 *   <li><b>prefix + file_chunk</b> — 文件内容分片（按 chunk_index 排序后拼接还原）。</li>
 * </ul>
 * 磁盘实例创建时会执行 {@code CREATE TABLE IF NOT EXISTS} 自动建表，因此即使不跑迁移也能直接使用。
 */
public class DatabaseFilesystemDriver implements FilesystemDriver, ApplicationContextAware {

    private ApplicationContext ctx;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        this.ctx = applicationContext;
    }

    @Override
    public boolean support(String driver) {
        return "database".equalsIgnoreCase(driver);
    }

    @Override
    public Filesystem create(String name, Map<String, Object> config) {
        if (ctx == null) {
            throw new StorageException("DatabaseFilesystemDriver 尚未注入 ApplicationContext，无法解析数据源");
        }
        Map<String, Object> cfg = config == null ? Map.of() : config;

        boolean binary = parseBool(cfg.get("binary"), true);
        String contentColumn = string(cfg.get("content-column"), "content");
        long chunkSize = parseLong(cfg.get("chunk-size"), 1024L * 1024L);
        String prefix = string(cfg.get("table-prefix"), "storage_");
        String datasourceBean = string(cfg.get("datasource"), null);
        String visibility = string(cfg.get("visibility"), "private");

        DataSource dataSource = resolveDataSource(datasourceBean);
        return new DatabaseFilesystem(name, dataSource, binary, contentColumn, chunkSize, prefix, visibility);
    }

    /**
     * 解析数据源：配置了 bean 名称则按名称取，否则取主数据源。
     */
    private DataSource resolveDataSource(String datasourceBean) {
        if (datasourceBean != null && !datasourceBean.isBlank()) {
            return ctx.getBean(datasourceBean, DataSource.class);
        }
        return ctx.getBean(DataSource.class);
    }

    private static boolean parseBool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return text.equals("true") || text.equals("1") || text.equals("yes") || text.equals("on");
    }

    private static long parseLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String string(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }
}
