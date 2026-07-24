package com.weacsoft.jaravel.vendor.migration;

import com.weacsoft.jaravel.vendor.migration.engine.MigrationScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 迁移文件解析器，从迁移 Java 文件中提取表结构定义。
 * <p>
 * 通过 {@link MigrationScanner} 编译迁移 {@code .java} 文件（或从 classpath 加载），
 * 然后使用 {@link CapturingSchema} 执行每个迁移的 {@code up()} 方法，
 * 捕获 {@link Blueprint} 中的列定义，转换为 {@link ParsedTable} 结构。
 * <p>
 * 与 {@link ReverseModelGenerator} 从数据库表反向生成不同，本解析器直接从
 * 迁移源码中提取表结构定义，无需连接数据库，适用于以下场景：
 * <ul>
 *   <li>开发阶段：表结构尚未迁移到数据库，但迁移文件已编写完成</li>
 *   <li>CI/CD：在无数据库环境中从迁移文件生成 Model 类</li>
 *   <li>代码审查：快速查看迁移文件定义了哪些表和字段</li>
 * </ul>
 * <p>
 * <b>多迁移合并</b>：若多个迁移操作同一张表（如先 create 再 table 添加字段），
 * 解析器会按迁移名称排序后依次执行，将列定义合并到同一个 {@link ParsedTable} 中。
 * 同名列以最后一次定义为准（模拟 ALTER TABLE MODIFY 的语义）。
 * <p>
 * <b>加载策略</b>：
 * <ol>
 *   <li>若指定目录存在且包含 {@code .java} 文件，使用 DIRECTORY 模式编译（需要 JDK）</li>
 *   <li>否则，回退到 CLASSPATH 模式从 classpath 加载已编译的迁移类</li>
 * </ol>
 *
 * <pre>
 * MigrationFileParser parser = new MigrationFileParser();
 * Map&lt;String, ParsedTable&gt; tables = parser.parseAll("database/migrations");
 * ParsedTable usersTable = parser.findTable("database/migrations", "users");
 * </pre>
 *
 * @see CapturingSchema
 * @see ParsedTable
 * @see ParsedColumn
 * @see MigrationScanner
 * @see ReverseModelGenerator#generateFromParsedTable
 */
public class MigrationFileParser {

    private static final Logger log = LoggerFactory.getLogger(MigrationFileParser.class);

    /**
     * 解析指定目录下所有迁移文件，返回表名到表定义的映射。
     * <p>
     * 若目录存在且包含 {@code .java} 文件，使用 DIRECTORY 模式编译；
     * 否则回退到 CLASSPATH 模式。
     *
     * @param migrationDir 迁移文件目录路径
     * @return 表名 → {@link ParsedTable} 映射（按表名排序），无迁移时返回空 Map
     */
    public Map<String, ParsedTable> parseAll(String migrationDir) {
        MigrationScanner scanner = new MigrationScanner();
        try {
            loadMigrations(scanner, migrationDir);
            return parseFromScanner(scanner);
        } finally {
            scanner.finish();
        }
    }

    /**
     * 在指定目录的迁移文件中查找特定表的定义。
     *
     * @param migrationDir 迁移文件目录路径
     * @param tableName    要查找的表名
     * @return 表定义，未找到返回 null
     */
    public ParsedTable findTable(String migrationDir, String tableName) {
        return parseAll(migrationDir).get(tableName);
    }

    /**
     * 获取指定目录中所有迁移文件定义的表名列表。
     *
     * @param migrationDir 迁移文件目录路径
     * @return 表名列表（排序后），无迁移时返回空列表
     */
    public List<String> listTables(String migrationDir) {
        Map<String, ParsedTable> tables = parseAll(migrationDir);
        List<String> names = new ArrayList<>(tables.keySet());
        Collections.sort(names);
        return names;
    }

    // ==================== 内部方法 ====================

    /**
     * 加载迁移类：优先从目录编译，回退到 classpath。
     */
    private void loadMigrations(MigrationScanner scanner, String migrationDir) {
        File dir = new File(migrationDir);
        if (dir.exists() && dir.isDirectory()) {
            File[] javaFiles = dir.listFiles((d, name) -> name.endsWith(".java"));
            if (javaFiles != null && javaFiles.length > 0) {
                try {
                    log.info("[migration-parser] 从目录编译迁移文件: {} ({} 个 .java 文件)",
                        dir.getAbsolutePath(), javaFiles.length);
                    scanner.compileFromDirectory(dir);
                    return;
                } catch (Exception e) {
                    log.warn("[migration-parser] 目录编译失败，回退到 classpath: {}", e.getMessage());
                }
            } else {
                log.info("[migration-parser] 目录无 .java 文件，尝试 classpath 加载");
            }
        } else {
            log.info("[migration-parser] 迁移目录不存在或不是目录: {}，尝试 classpath 加载", migrationDir);
        }

        try {
            scanner.loadFromClasspath();
        } catch (Exception e) {
            log.warn("[migration-parser] classpath 加载失败: {}", e.getMessage());
        }
    }

    /**
     * 从已加载的 MigrationScanner 解析所有迁移的表结构。
     * <p>
     * 按类名排序后依次实例化迁移类，调用 {@code up()} 方法，
     * 从 {@link CapturingSchema} 捕获 {@link Blueprint}，转换为 {@link ParsedTable}。
     */
    private Map<String, ParsedTable> parseFromScanner(MigrationScanner scanner) {
        Map<String, ParsedTable> tables = new LinkedHashMap<>();

        List<String> classNames = new ArrayList<>(scanner.getAllMigrationClassNames());
        Collections.sort(classNames);

        CapturingSchema schema = new CapturingSchema();

        for (String className : classNames) {
            try {
                Class<?> clazz = scanner.getCompiledClass(className);
                if (!isMigrationClass(clazz)) {
                    continue;
                }

                Migration migration = (Migration) clazz.getDeclaredConstructor().newInstance();
                schema.clear();
                migration.up(schema);

                // 将捕获的 Blueprint 合并到 ParsedTable
                for (Blueprint bp : schema.getBlueprints()) {
                    String tableName = bp.getTable();
                    ParsedTable table = tables.get(tableName);
                    if (table == null) {
                        table = new ParsedTable(tableName);
                        tables.put(tableName, table);
                    }
                    for (ColumnDefinition col : bp.getColumns()) {
                        table.addColumn(ParsedColumn.from(col));
                    }
                }

                log.debug("[migration-parser] 解析迁移: {} ({} 张表)",
                    className, schema.getBlueprints().size());
            } catch (Exception e) {
                log.warn("[migration-parser] 无法解析迁移 {}: {}", className, e.getMessage());
            }
        }

        // 按表名排序
        Map<String, ParsedTable> sorted = new LinkedHashMap<>();
        List<String> tableNames = new ArrayList<>(tables.keySet());
        Collections.sort(tableNames);
        for (String name : tableNames) {
            sorted.put(name, tables.get(name));
        }

        log.info("[migration-parser] 解析完成: {} 张表", sorted.size());
        return sorted;
    }

    /**
     * 检查类是否为迁移类：标注 {@link MigrationAnnotation} 且实现 {@link Migration}。
     */
    private boolean isMigrationClass(Class<?> clazz) {
        if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
            return false;
        }
        if (!clazz.isAnnotationPresent(MigrationAnnotation.class)) {
            return false;
        }
        return Migration.class.isAssignableFrom(clazz);
    }
}
