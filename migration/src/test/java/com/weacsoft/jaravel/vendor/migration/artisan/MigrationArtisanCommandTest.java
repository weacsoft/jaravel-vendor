package com.weacsoft.jaravel.vendor.migration.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanApplication;
import com.weacsoft.jaravel.vendor.migration.autoconfigure.MigrationProperties;
import com.weacsoft.jaravel.vendor.migration.engine.MigrationExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 迁移 Artisan 命令测试。
 * <p>
 * 验证 5 个迁移命令（migrate、migrate:rollback、migrate:reset、migrate:refresh、migrate:status）
 * 的签名、命令名和委托调用行为。使用 {@link ArtisanApplication} 调度命令，
 * 覆盖从命令行参数解析到 MigrationExecutor 委托的完整流程。
 */
class MigrationArtisanCommandTest {

    /**
     * 测试用 MigrationExecutor 子类，捕获 execute() 调用参数。
     */
    static class CapturingExecutor extends MigrationExecutor {
        final List<String[]> capturedArgs = new ArrayList<>();

        CapturingExecutor() {
            super(null, new MigrationProperties());
        }

        @Override
        public void execute(String... args) {
            capturedArgs.add(args);
        }
    }

    /**
     * 创建一个不依赖 Spring 容器的 ArtisanApplication，手动注册命令。
     */
    private ArtisanApplication createArtisan(CapturingExecutor executor) {
        ArtisanApplication app = new ArtisanApplication(null);
        app.register(new MigrateCommand(executor));
        app.register(new MigrateRollbackCommand(executor));
        app.register(new MigrateResetCommand(executor));
        app.register(new MigrateRefreshCommand(executor));
        app.register(new MigrateStatusCommand(executor));
        return app;
    }

    @Test
    void testMigrateCommand() {
        CapturingExecutor executor = new CapturingExecutor();
        MigrateCommand cmd = new MigrateCommand(executor);

        assertEquals("migrate {--force}", cmd.signature());
        assertEquals("migrate", cmd.commandName());
        assertEquals("执行数据库迁移", cmd.description());

        ArtisanApplication app = createArtisan(executor);
        int result = app.call("migrate");
        assertEquals(0, result);
        assertEquals(1, executor.capturedArgs.size());
        assertArrayEquals(new String[]{"migrate"}, executor.capturedArgs.get(0));
    }

    @Test
    void testMigrateRollbackCommand() {
        CapturingExecutor executor = new CapturingExecutor();
        MigrateRollbackCommand cmd = new MigrateRollbackCommand(executor);

        assertEquals("migrate:rollback {--step=1}", cmd.signature());
        assertEquals("migrate:rollback", cmd.commandName());

        ArtisanApplication app = createArtisan(executor);

        // 默认 step=1
        int result = app.call("migrate:rollback");
        assertEquals(0, result);
        assertArrayEquals(new String[]{"rollback=1"}, executor.capturedArgs.get(0));

        // 指定 step=5
        executor.capturedArgs.clear();
        result = app.call("migrate:rollback", new String[]{"--step=5"});
        assertEquals(0, result);
        assertArrayEquals(new String[]{"rollback=5"}, executor.capturedArgs.get(0));
    }

    @Test
    void testMigrateResetCommand() {
        CapturingExecutor executor = new CapturingExecutor();
        MigrateResetCommand cmd = new MigrateResetCommand(executor);

        assertEquals("migrate:reset", cmd.signature());
        assertEquals("migrate:reset", cmd.commandName());

        ArtisanApplication app = createArtisan(executor);
        int result = app.call("migrate:reset");
        assertEquals(0, result);
        assertArrayEquals(new String[]{"reset"}, executor.capturedArgs.get(0));
    }

    @Test
    void testMigrateRefreshCommand() {
        CapturingExecutor executor = new CapturingExecutor();
        MigrateRefreshCommand cmd = new MigrateRefreshCommand(executor);

        assertEquals("migrate:refresh", cmd.signature());
        assertEquals("migrate:refresh", cmd.commandName());

        ArtisanApplication app = createArtisan(executor);
        int result = app.call("migrate:refresh");
        assertEquals(0, result);
        assertArrayEquals(new String[]{"refresh"}, executor.capturedArgs.get(0));
    }

    @Test
    void testMigrateStatusCommand() {
        CapturingExecutor executor = new CapturingExecutor();
        MigrateStatusCommand cmd = new MigrateStatusCommand(executor);

        assertEquals("migrate:status", cmd.signature());
        assertEquals("migrate:status", cmd.commandName());

        ArtisanApplication app = createArtisan(executor);
        int result = app.call("migrate:status");
        assertEquals(0, result);
        assertArrayEquals(new String[]{"status"}, executor.capturedArgs.get(0));
    }

    @Test
    void testHandleExceptionReturnsOne() {
        MigrationExecutor failingExecutor = new MigrationExecutor(null, new MigrationProperties()) {
            @Override
            public void execute(String... args) {
                throw new RuntimeException("DB connection failed");
            }
        };

        ArtisanApplication app = new ArtisanApplication(null);
        app.register(new MigrateCommand(failingExecutor));

        int result = app.call("migrate");
        assertEquals(1, result);
    }

    @Test
    void testAllCommandsRegistered() {
        CapturingExecutor executor = new CapturingExecutor();
        ArtisanApplication app = createArtisan(executor);

        // 验证 5 个迁移命令都已注册
        assertTrue(app.all().containsKey("migrate"));
        assertTrue(app.all().containsKey("migrate:rollback"));
        assertTrue(app.all().containsKey("migrate:reset"));
        assertTrue(app.all().containsKey("migrate:refresh"));
        assertTrue(app.all().containsKey("migrate:status"));
    }
}
