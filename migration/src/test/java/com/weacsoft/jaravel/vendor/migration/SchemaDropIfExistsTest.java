package com.weacsoft.jaravel.vendor.migration;

import com.weacsoft.jaravel.vendor.migration.dialect.Dialect;
import com.weacsoft.jaravel.vendor.migration.dialect.DialectFactory;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Schema#dropIfExists} 的删表语句测试。
 * <p>
 * Oracle 不支持 {@code DROP TABLE IF EXISTS}（执行报 ORA-00900），
 * 改为先查询系统目录确认表是否存在再执行删除；其余方言直接执行带 IF EXISTS 的语句。
 */
class SchemaDropIfExistsTest {

    /** 只记录 SQL、不真正执行的执行器 */
    private static final class RecordingJdbcExecutor extends JdbcExecutor {
        private final List<String> executed = new ArrayList<>();
        /** 表存在性查询返回的表数量 */
        private final int tableCount;

        RecordingJdbcExecutor(int tableCount) {
            super((DataSource) null);
            this.tableCount = tableCount;
        }

        @Override
        public void execute(String sql) {
            executed.add(sql);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return requiredType.cast(Integer.valueOf(tableCount));
        }
    }

    /** 用反射替换 Schema 的 dialect 与 jdbc 字段 */
    private static Schema newSchema(Dialect dialect, RecordingJdbcExecutor jdbc) {
        Schema schema = new Schema(null);
        setField(schema, "dialect", dialect);
        setField(schema, "jdbc", jdbc);
        return schema;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = Schema.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void oracleChecksTableExistenceBeforeDropping() {
        RecordingJdbcExecutor jdbc = new RecordingJdbcExecutor(0);
        Schema schema = newSchema(DialectFactory.create("oracle"), jdbc);
        schema.dropIfExists("users");
        assertTrue(jdbc.executed.isEmpty(), "表不存在时不应执行删除语句");
    }

    @Test
    void oracleDropsTableWhenItExists() {
        RecordingJdbcExecutor jdbc = new RecordingJdbcExecutor(1);
        Schema schema = newSchema(DialectFactory.create("oracle"), jdbc);
        schema.dropIfExists("users");
        assertEquals(1, jdbc.executed.size());
        assertEquals("DROP TABLE \"users\"", jdbc.executed.get(0));
    }

    @Test
    void mysqlUsesDropTableIfExists() {
        RecordingJdbcExecutor jdbc = new RecordingJdbcExecutor(0);
        Schema schema = newSchema(DialectFactory.create("mysql"), jdbc);
        schema.dropIfExists("users");
        assertEquals(1, jdbc.executed.size());
        assertEquals("DROP TABLE IF EXISTS `users`", jdbc.executed.get(0));
    }
}
