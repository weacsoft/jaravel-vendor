package com.weacsoft.jaravel.vendor.storage.local;

import com.weacsoft.jaravel.vendor.storage.StorageException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路径越界防护回归（审计 L2 / L3）。
 * <p>
 * 修复前两个真实缺陷：
 * <ol>
 *   <li>{@code resolve("")} 等于磁盘根，而 {@code delete}/{@code copy}/{@code move} 没有根目录判定 ——
 *       {@code delete("")} 会把空根目录直接删掉（数据毁坏）；{@code ""}/{@code "."}/{@code "a/.."}
 *       都解析成根，所以只挡空串不够；</li>
 *   <li>{@code resolve} 只做词法 {@code normalize} + {@code startsWith}，根目录内的软链接/junction
 *       指向外部时可以越界读写。</li>
 * </ol>
 * 现在：真实路径（canonical）归属校验 + 写操作统一拒绝「解析后等于根目录」。
 */
class LocalFilesystemRootGuardTest {

    @TempDir
    Path root;

    private LocalFilesystem fs;

    @BeforeEach
    void setUp() {
        fs = new LocalFilesystem(root.toString());
    }

    @Test
    void rootAliasesAreRejectedForDelete() {
        for (String alias : List.of("", "/", ".", "./", "a/..", "x/../..")) {
            assertThrows(StorageException.class, () -> fs.delete(alias),
                    "delete(\"" + alias + "\") 必须被拒绝（否则会删掉磁盘根）");
        }
        assertTrue(Files.exists(root), "根目录必须仍然存在");
    }

    @Test
    void rootAliasesAreRejectedForDirectoryDelete() {
        for (String alias : List.of("", "/", ".", "a/..")) {
            assertThrows(StorageException.class, () -> fs.deleteDirectory(alias),
                    "deleteDirectory(\"" + alias + "\") 必须被拒绝");
        }
        assertTrue(Files.exists(root));
    }

    @Test
    void rootIsRejectedAsCopyOrMoveSourceAndTarget() {
        fs.put("x.txt", "x".getBytes());

        assertThrows(StorageException.class, () -> fs.copy("", "y.txt"), "源=根 必须拒绝");
        assertThrows(StorageException.class, () -> fs.move("", "y.txt"), "源=根 必须拒绝");
        assertThrows(StorageException.class, () -> fs.copy("x.txt", ""), "目标=根 必须拒绝");
        assertThrows(StorageException.class, () -> fs.copy("x.txt", "."), "目标=根(.) 必须拒绝");
        assertThrows(StorageException.class, () -> fs.move("x.txt", ""), "目标=根 必须拒绝");
    }

    @Test
    void parentTraversalStillRejected() {
        assertThrows(StorageException.class, () -> fs.put("../evil.txt", "x".getBytes()));
        assertThrows(StorageException.class, () -> fs.delete("../evil.txt"));
        assertFalse(Files.exists(root.getParent().resolve("evil.txt")), "不得在根目录外创建文件");
    }

    @Test
    void normalOperationsStillWork() {
        fs.put("a/b.txt", "hello".getBytes());

        assertTrue(fs.exists("a/b.txt"), "正常写入必须可用（不要误伤）");
        assertTrue(new String(fs.read("a/b.txt")).contains("hello"));
        assertTrue(fs.delete("a/b.txt"), "正常删除必须可用");
        assertFalse(fs.exists("a/b.txt"));
    }

    @Test
    void rootItselfBeingAJunctionStillWorks() throws Exception {
        // 最易漏的一条：root 自身是 junction/软链时，canonical 根比较必须仍然放行正常读写
        Path realTarget = Files.createDirectories(root.resolve("real-target"));
        Path link = root.getParent().resolve("root-link-" + System.nanoTime());
        Assumptions.assumeTrue(createJunction(link, realTarget), "本机无法创建 junction（跳过，不算通过）");

        try {
            LocalFilesystem linked = new LocalFilesystem(link.toString());
            linked.put("inner.txt", "ok".getBytes());
            assertTrue(linked.exists("inner.txt"), "根为 junction 时正常写入必须可用");
            assertTrue(Files.exists(realTarget.resolve("inner.txt")), "文件应落在 junction 目标目录");
            // 越界仍必须被拒
            assertThrows(StorageException.class, () -> linked.put("../outside.txt", "x".getBytes()));
        } finally {
            try {
                Files.deleteIfExists(link);
            } catch (IOException ignored) {
                // 清理失败不影响断言
            }
        }
    }

    @Test
    void junctionInsideRootPointingOutsideIsRejected() throws Exception {
        Path outside = Files.createDirectories(root.getParent().resolve("outside-" + System.nanoTime()));
        Path link = root.resolve("escape");
        Assumptions.assumeTrue(createJunction(link, outside), "本机无法创建 junction（跳过，不算通过）");

        // 通过根内的 junction 写外部路径必须被真实路径校验拦下
        assertThrows(StorageException.class, () -> fs.put("escape/pwned.txt", "x".getBytes()),
                "根内 junction 指向外部时不得越界写入");
        assertFalse(Files.exists(outside.resolve("pwned.txt")), "外部目录内不得出现文件");
    }

    /**
     * 创建目录 junction（Windows 免提权；Java NIO 无法创建 junction）。
     *
     * @param link   链接路径
     * @param target 目标目录
     * @return 成功返回 true
     */
    private static boolean createJunction(Path link, Path target) {
        try {
            Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J",
                    link.toString(), target.toString())
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0 && Files.exists(link);
        } catch (Exception e) {
            return false;
        }
    }
}