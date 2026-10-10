package com.weacsoft.jaravel.vendor.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 迁移解析失败的可见性（审计 M19，按两位专家意见收窄范围）。
 * <p>
 * 关键不变量：
 * <ol>
 *   <li><b>「目录不存在 / 目录内无 .java」是合法回退路径</b>（新检出、纯 classpath 部署）——
 *       {@code parseAll} 不得抛异常，也不得把它记成「解析失败」（否则会把合法部署误判为故障）；</li>
 *   <li><b>「文件存在但 {@code up()} / 实例化抛异常」必须被记录</b>并在命令层导致非零退出，
 *       绝不能静默产出缺表结果；</li>
 *   <li>失败清单<b>每次解析重置</b>，避免同一 parser 实例把上一次的失败串到下一次调用。</li>
 * </ol>
 * 注：命令层退出码同样受「classpath 上是否有可用迁移」影响，因此「合法回退」在<b>parser 层</b>断言
 * （这也是专家建议的最小、最稳写法）。
 */
class MigrationParserFailureTest {

    @TempDir
    Path dir;

    @Test
    void missingDirectoryIsALegalFallbackNotAFailure() {
        MigrationFileParser parser = new MigrationFileParser();

        java.util.Map<String, ParsedTable> tables = parser.parseAll(dir.resolve("does-not-exist").toString());

        assertNotNull(tables);
        assertTrue(parser.lastFailures().isEmpty(),
                "目录缺失是合法回退（回退 classpath），不得记为解析失败");
    }

    @Test
    void directoryWithoutJavaFilesIsAlsoALegalFallback() {
        MigrationFileParser parser = new MigrationFileParser();

        parser.parseAll(dir.toString());

        assertTrue(parser.lastFailures().isEmpty(), "目录内无 .java 时同样是合法回退");
    }

    @Test
    void migrationThatThrowsInUpIsRecorded() throws IOException {
        Files.writeString(dir.resolve("BoomMigration.java"), boomSource("BoomMigration", "boom"));

        MigrationFileParser parser = new MigrationFileParser();
        parser.parseAll(dir.toString());

        assertFalse(parser.lastFailures().isEmpty(),
                "文件确实存在却解析失败的迁移必须被记录（否则静默缺表）");
        assertTrue(parser.lastFailures().stream().anyMatch(f -> f.contains("BoomMigration")),
                "失败清单应包含类名与原因: " + parser.lastFailures());
    }

    @Test
    void failuresAreResetBetweenCalls() throws IOException {
        Files.writeString(dir.resolve("BoomMigration2.java"), boomSource("BoomMigration2", "boom-2"));

        MigrationFileParser parser = new MigrationFileParser();
        parser.parseAll(dir.toString());
        assertFalse(parser.lastFailures().isEmpty(), "第一次解析应记录失败");

        // 第二次解析一个「不存在」的目录：旧失败必须被清空
        parser.parseAll(dir.resolve("nope").toString());
        assertTrue(parser.lastFailures().isEmpty(),
                "失败清单必须每次解析重置（否则旧失败会串到下一次调用）");
    }
/** 生成一个「能编译但 up() 抛异常」的迁移源码（形态对齐既有测试的写法） */
    private static String boomSource(String className, String message) {
        return String.join("\n",
                "package migrations;",
                "import com.weacsoft.jaravel.vendor.migration.Migration;",
                "import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;",
                "import com.weacsoft.jaravel.vendor.migration.Schema;",
                "@MigrationAnnotation",
                "public class " + className + " implements Migration {",
                "    @Override public void up(Schema schema) {",
                "        throw new IllegalStateException(\"" + message + "\");",
                "    }",
                "    @Override public void down(Schema schema) { }",
                "}");
    }
}