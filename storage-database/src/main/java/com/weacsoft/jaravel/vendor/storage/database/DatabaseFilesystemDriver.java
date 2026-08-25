package com.weacsoft.jaravel.vendor.storage.database;

import com.weacsoft.jaravel.vendor.database.ConnectionManager;
import com.weacsoft.jaravel.vendor.storage.StorageException;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.FilesystemDriver;

import javax.sql.DataSource;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 数据库文件存储驱动（工厂），支持将文件存入关系型数据库。
 * <p>
 * 支持 {@code driver: database}，由 {@code springboot} 模块的装配层
 * （{@code vendor.springboot.storage.StorageAutoConfiguration}）自动注册为 Bean，
 * 并被 {@code StorageManager} 自动收集，业务方无需手动注册；
 * 纯 JVM 环境下可手动 {@code manager.registerDriver(new DatabaseFilesystemDriver(...))}。
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
 *   <li><b>connection</b>（或旧键 {@code datasource}）— 可选，database 模块
 *       {@code @RegisterConnection} 声明的连接别名。不配置时使用默认连接
 *       （{@link ConnectionManager#defaultRawDataSource()}，Spring 环境可回退容器主数据源）。
 *       配置后可实现「用单独的数据库存文件」做多机同步。</li>
 *   <li><b>visibility</b> — 默认可见性，默认 {@code private}。</li>
 * </ul>
 *
 * <h3>数据表</h3>
 * 每个磁盘共用两张表（通过 {@code disk} 列区分），需提前建表：
 * <ul>
 *   <li><b>prefix + file</b> — 文件元信息（路径、大小、分片数、存放方式、MIME、可见性等）。</li>
 *   <li><b>prefix + file_chunk</b> — 文件内容分片（按 chunk_index 排序后拼接还原）。</li>
 * </ul>
 * 不自动建表：通过 {@code artisan storage:table} 命令生成迁移文件，执行 {@code artisan migrate} 建表。
 */
public class DatabaseFilesystemDriver implements FilesystemDriver {

    /** 数据源解析器（默认连接），延迟到真正创建磁盘时才调用。 */
    private final Supplier<DataSource> dataSourceSupplier;

    /**
     * 以固定数据源构建（测试或明确指定数据源时使用）。
     *
     * @param dataSource 数据源
     */
    public DatabaseFilesystemDriver(DataSource dataSource) {
        this(() -> dataSource);
    }

    /**
     * 以惰性解析器构建（springboot 自动装配使用，可叠加容器回退）。
     *
     * @param dataSourceSupplier 数据源解析器，在 {@link #create(String, Map)} 时调用
     */
    public DatabaseFilesystemDriver(Supplier<DataSource> dataSourceSupplier) {
        this.dataSourceSupplier = dataSourceSupplier;
    }

    /**
     * 使用 database 模块默认连接构建（纯 jaravel 环境的标准用法，无 Spring）。
     *
     * @return 驱动
     */
    public static DatabaseFilesystemDriver fromConnectionManager() {
        return new DatabaseFilesystemDriver(ConnectionManager::defaultRawDataSource);
    }

    @Override
    public boolean support(String driver) {
        return "database".equalsIgnoreCase(driver);
    }

    @Override
    public Filesystem create(String name, Map<String, Object> config) {
        Map<String, Object> cfg = config == null ? Map.of() : config;

        boolean binary = parseBool(cfg.get("binary"), true);
        String contentColumn = string(cfg.get("content-column"), "content");
        long chunkSize = parseLong(cfg.get("chunk-size"), 1024L * 1024L);
        String prefix = string(cfg.get("table-prefix"), "storage_");
        String visibility = string(cfg.get("visibility"), "private");
        String alias = firstNonBlank(string(cfg.get("connection"), null),
                string(cfg.get("datasource"), null));

        DataSource dataSource = resolve(alias);
        return new DatabaseFilesystem(name, dataSource, binary, contentColumn, chunkSize, prefix, visibility);
    }

    /**
     * 解析数据源；缺失时给出可操作的错误提示，而不是让应用在启动期莫名失败。
     *
     * @param alias 磁盘配置里可选的 connection / datasource 连接别名
     * @return 数据源
     */
    private DataSource resolve(String alias) {
        DataSource dataSource = (alias == null || alias.isBlank())
                ? dataSourceSupplier.get()
                : resolveByAlias(alias);
        if (dataSource == null) {
            throw new StorageException(
                    "存储磁盘使用了 driver: database，但未找到可用的数据库连接"
                            + (alias == null || alias.isBlank() ? "" : "（别名: " + alias + "）")
                            + "。请使用 @RegisterConnection 注册连接，"
                            + "或把该磁盘的 driver 改为 local。");
        }
        return dataSource;
    }

    /**
     * 按别名解析 database 模块的原始数据源，不存在时返回 {@code null}。
     *
     * @param alias 连接别名
     * @return 数据源
     */
    private DataSource resolveByAlias(String alias) {
        return ConnectionManager.rawDataSource(alias);
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

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
