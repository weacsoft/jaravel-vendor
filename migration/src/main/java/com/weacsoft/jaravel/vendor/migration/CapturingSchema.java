package com.weacsoft.jaravel.vendor.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 捕获型 Schema，用于从迁移文件中提取表结构定义而不实际执行 SQL。
 * <p>
 * 继承 {@link Schema}，但重写 {@link #create(String, Consumer)} 和
 * {@link #table(String, Consumer)} 方法，将 {@link Blueprint} 定义捕获到列表中，
 * 而非生成并执行 DDL 语句。其他写操作（drop、rename 等）均为空实现。
 * <p>
 * 典型用法：在 {@link MigrationFileParser} 中，将此 Schema 传入迁移类的
 * {@code up(Schema)} 方法，执行后通过 {@link #getBlueprints()} 获取所有表定义。
 * <pre>
 * CapturingSchema schema = new CapturingSchema();
 * migration.up(schema);
 * List&lt;Blueprint&gt; blueprints = schema.getBlueprints();
 * </pre>
 * <p>
 * 构造时传入 {@code null} 数据源：{@link DialectFactory#detect} 会捕获 NPE
 * 并回退到 MySQL 方言，但由于不调用 {@code toCreateSql()}，方言实际不参与解析。
 *
 * @see MigrationFileParser
 * @see Schema
 * @see Blueprint
 */
public class CapturingSchema extends Schema {

    /** 捕获的 Blueprint 列表（create 和 table 调用均会追加） */
    private final List<Blueprint> blueprints = new ArrayList<>();

    /**
     * 创建捕获型 Schema，不连接数据库。
     * <p>
     * 调用 {@code super(null)}，{@link JdbcExecutor} 仅存储 null 引用，
     * {@link DialectFactory#detect} 捕获 NPE 后回退到 MySQL 方言。
     * 由于重写了所有执行方法，jdbc 和 dialect 实际不会被使用。
     */
    public CapturingSchema() {
        super(null);
    }

    /**
     * 捕获 CREATE TABLE 定义，不执行 SQL。
     * <p>
     * 创建 {@link Blueprint}，执行定义回调以填充列信息，然后将 Blueprint 加入捕获列表。
     *
     * @param table      表名
     * @param definition 表结构定义回调
     */
    @Override
    public void create(String table, Consumer<Blueprint> definition) {
        Blueprint blueprint = new Blueprint(table);
        definition.accept(blueprint);
        blueprints.add(blueprint);
    }

    /**
     * 捕获 ALTER TABLE 定义，不执行 SQL。
     * <p>
     * 与 {@link #create} 类似，创建 Blueprint 并执行定义回调，
     * 然后加入捕获列表。后续由 {@link MigrationFileParser} 合并到对应表定义。
     *
     * @param table      表名
     * @param definition 表结构修改回调
     */
    @Override
    public void table(String table, Consumer<Blueprint> definition) {
        Blueprint blueprint = new Blueprint(table);
        definition.accept(blueprint);
        blueprints.add(blueprint);
    }

    /** 空实现：不删除表 */
    @Override
    public void dropIfExists(String table) {
        // no-op
    }

    /** 空实现：不删除表 */
    @Override
    public void drop(String table) {
        // no-op
    }

    /** 空实现：不重命名表 */
    @Override
    public void rename(String from, String to) {
        // no-op
    }

    /** 空实现：始终返回 false */
    @Override
    public boolean hasTable(String table) {
        return false;
    }

    /** 空实现：始终返回 false */
    @Override
    public boolean hasColumn(String table, String column) {
        return false;
    }

    /**
     * 获取所有捕获的 Blueprint。
     *
     * @return Blueprint 列表（不可变视图）
     */
    public List<Blueprint> getBlueprints() {
        return new ArrayList<>(blueprints);
    }

    /**
     * 清除已捕获的 Blueprint，以便复用此实例处理下一个迁移。
     */
    public void clear() {
        blueprints.clear();
    }
}
