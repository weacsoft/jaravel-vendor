package com.weacsoft.jaravel.vendor.storage.local;

import com.weacsoft.jaravel.vendor.storage.StorageException;
import com.weacsoft.jaravel.vendor.storage.contract.FileInfo;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import com.weacsoft.jaravel.vendor.storage.contract.Visibility;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 本地文件系统实现，对齐 Laravel {@code local} / {@code public} 驱动。
 * <p>
 * 所有操作限定在 {@code root} 根目录内，通过 {@link #resolve(String)} 做<b>路径穿越防护</b>：
 * 规范化后的绝对路径若不在根目录下则抛 {@link StorageException}。
 *
 * <h3>大文件支持</h3>
 * {@link #putStream}、{@link #writeTo}、{@link #readStream} 均为流式实现，
 * 内存占用恒定（64KB 缓冲），可处理任意大小的文件。
 *
 * <h3>可见性</h3>
 * 在支持 POSIX 的系统上，可见性映射为文件权限（public → {@code rw-r--r--}，
 * private → {@code rw-------}）；Windows 等非 POSIX 系统上，
 * {@link #setVisibility} 静默忽略，{@link #visibility} 返回磁盘配置的默认可见性。
 *
 * <h3>线程安全</h3>
 * 本类无可变实例状态（{@code root}/{@code urlPrefix}/{@code defaultVisibility} 均为 final），
 * 底层依赖 JDK NIO 的原子文件操作，可安全地被多线程共享。
 */
public class LocalFilesystem implements Filesystem {

    private static final int BUFFER_SIZE = 64 * 1024;

    private static final Set<PosixFilePermission> PUBLIC_FILE_PERMS =
            PosixFilePermissions.fromString("rw-r--r--");
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMS =
            PosixFilePermissions.fromString("rw-------");

    private final String name;
    private final Path root;
    private final String urlPrefix;
    private final Visibility defaultVisibility;

    /**
     * 便捷构造器：仅指定根目录，name 默认 {@code "local"}，urlPrefix 和 defaultVisibility 为 {@code null}。
     *
     * @param root 根目录（相对运行目录或绝对路径），自动创建
     */
    public LocalFilesystem(String root) {
        this("local", root, null, null);
    }

    /**
     * 创建本地文件系统。
     *
     * @param name              磁盘名称
     * @param root              根目录（相对运行目录或绝对路径），自动创建
     * @param urlPrefix         公开访问 URL 前缀，可为 {@code null}（此时 {@link #url} 抛异常）
     * @param defaultVisibility 默认可见性，{@code null} 时为 {@link Visibility#PRIVATE}
     */
    public LocalFilesystem(String name, String root, String urlPrefix, Visibility defaultVisibility) {
        this.name = name;
        this.root = Paths.get(root).toAbsolutePath().normalize();
        this.urlPrefix = normalizeUrlPrefix(urlPrefix);
        this.defaultVisibility = defaultVisibility == null ? Visibility.PRIVATE : defaultVisibility;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new StorageException("创建磁盘根目录失败: " + this.root, e);
        }
    }

    private static String normalizeUrlPrefix(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    // ==================== 路径解析与防护 ====================

    /**
     * 将相对路径解析为绝对路径，并校验未逃逸出根目录。
     *
     * @param path 相对路径
     * @return 绝对路径
     * @throws StorageException 路径越界
     */
    protected Path resolve(String path) {
        String relative = path == null ? "" : path.replace('\\', '/').trim();
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw StorageException.invalidPath(path);
        }
        return resolved;
    }

    /**
     * 将绝对路径转回相对磁盘根目录的路径（{@code /} 分隔）。
     */
    private String relativize(Path absolute) {
        return root.relativize(absolute).toString().replace('\\', '/');
    }

    /**
     * 确保父目录存在。
     */
    private void ensureParent(Path file) {
        Path parent = file.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw StorageException.writeFailed(relativize(file), e);
        }
    }

    // ==================== 读取 ====================

    @Override
    public boolean exists(String path) {
        return Files.exists(resolve(path));
    }

    @Override
    public byte[] read(String path) {
        Path file = resolve(path);
        if (!Files.exists(file)) {
            throw StorageException.notFound(path);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw StorageException.readFailed(path, e);
        }
    }

    @Override
    public InputStream readStream(String path) {
        Path file = resolve(path);
        if (!Files.exists(file)) {
            throw StorageException.notFound(path);
        }
        try {
            return Files.newInputStream(file, StandardOpenOption.READ);
        } catch (IOException e) {
            throw StorageException.readFailed(path, e);
        }
    }

    // ==================== 写入 ====================

    @Override
    public void put(String path, byte[] contents) {
        Path file = resolve(path);
        ensureParent(file);
        try {
            Files.write(file, contents,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            applyVisibility(file, defaultVisibility);
        } catch (IOException e) {
            throw StorageException.writeFailed(path, e);
        }
    }

    @Override
    public long putStream(String path, InputStream input) {
        Path file = resolve(path);
        ensureParent(file);
        try (OutputStream out = Files.newOutputStream(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            long total = copy(input, out);
            out.flush();
            applyVisibility(file, defaultVisibility);
            return total;
        } catch (IOException e) {
            throw StorageException.writeFailed(path, e);
        }
    }

    @Override
    public void append(String path, byte[] contents) {
        Path file = resolve(path);
        ensureParent(file);
        try {
            Files.write(file, contents,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw StorageException.writeFailed(path, e);
        }
    }

    @Override
    public long writeTo(String path, OutputStream output) {
        try (InputStream in = readStream(path)) {
            return copy(in, output);
        } catch (IOException e) {
            throw StorageException.readFailed(path, e);
        }
    }

    /**
     * 流式拷贝，固定缓冲，内存占用恒定。
     */
    private long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            total += read;
        }
        return total;
    }

    // ==================== 删除 / 移动 / 复制 ====================

    @Override
    public boolean delete(String path) {
        Path file = resolve(path);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new StorageException("删除文件失败: " + path, e);
        }
    }

    @Override
    public void copy(String from, String to) {
        Path source = resolve(from);
        if (!Files.exists(source)) {
            throw StorageException.notFound(from);
        }
        Path target = resolve(to);
        ensureParent(target);
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("复制文件失败: " + from + " -> " + to, e);
        }
    }

    @Override
    public void move(String from, String to) {
        Path source = resolve(from);
        if (!Files.exists(source)) {
            throw StorageException.notFound(from);
        }
        Path target = resolve(to);
        ensureParent(target);
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("移动文件失败: " + from + " -> " + to, e);
        }
    }

    // ==================== 元信息 ====================

    @Override
    public long size(String path) {
        Path file = resolve(path);
        if (!Files.exists(file)) {
            throw StorageException.notFound(path);
        }
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw StorageException.readFailed(path, e);
        }
    }

    @Override
    public Instant lastModified(String path) {
        Path file = resolve(path);
        if (!Files.exists(file)) {
            throw StorageException.notFound(path);
        }
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            throw StorageException.readFailed(path, e);
        }
    }

    @Override
    public String mimeType(String path) {
        try {
            return Files.probeContentType(resolve(path));
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public FileInfo info(String path) {
        Path file = resolve(path);
        if (!Files.exists(file)) {
            throw StorageException.notFound(path);
        }
        return toFileInfo(file);
    }

    private FileInfo toFileInfo(Path file) {
        try {
            boolean directory = Files.isDirectory(file);
            return new FileInfo(
                    relativize(file),
                    file.getFileName().toString(),
                    directory,
                    directory ? 0L : Files.size(file),
                    Files.getLastModifiedTime(file).toInstant(),
                    directory ? null : probeQuietly(file),
                    readVisibility(file)
            );
        } catch (IOException e) {
            throw StorageException.readFailed(relativize(file), e);
        }
    }

    private String probeQuietly(Path file) {
        try {
            return Files.probeContentType(file);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public Visibility visibility(String path) {
        Path file = resolve(path);
        if (!Files.exists(file)) {
            throw StorageException.notFound(path);
        }
        return readVisibility(file);
    }

    /**
     * 读取 POSIX 权限推断可见性；非 POSIX 系统返回磁盘默认可见性。
     */
    private Visibility readVisibility(Path file) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
            return perms.contains(PosixFilePermission.OTHERS_READ)
                    ? Visibility.PUBLIC
                    : Visibility.PRIVATE;
        } catch (UnsupportedOperationException | IOException e) {
            return defaultVisibility;
        }
    }

    @Override
    public void setVisibility(String path, Visibility visibility) {
        Path file = resolve(path);
        if (!Files.exists(file)) {
            throw StorageException.notFound(path);
        }
        applyVisibility(file, visibility);
    }

    /**
     * 应用 POSIX 权限；非 POSIX 系统（Windows）静默忽略。
     */
    private void applyVisibility(Path file, Visibility visibility) {
        if (visibility == null) {
            return;
        }
        try {
            Files.setPosixFilePermissions(file,
                    visibility == Visibility.PUBLIC ? PUBLIC_FILE_PERMS : PRIVATE_FILE_PERMS);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows / 非 POSIX 文件系统不支持，忽略
        }
    }

    // ==================== 目录 ====================

    @Override
    public List<FileInfo> files(String directory) {
        return listDirectory(directory, false, false);
    }

    @Override
    public List<FileInfo> directories(String directory) {
        return listDirectory(directory, true, false);
    }

    @Override
    public List<FileInfo> allFiles(String directory) {
        return listDirectory(directory, false, true);
    }

    /**
     * 列举目录内容。
     *
     * @param directory     目录相对路径
     * @param wantDirectory true 返回子目录，false 返回文件
     * @param recursive     是否递归（仅对文件有效）
     */
    private List<FileInfo> listDirectory(String directory, boolean wantDirectory, boolean recursive) {
        Path dir = resolve(directory);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<FileInfo> result = new ArrayList<>();
        if (recursive) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(p -> result.add(toFileInfo(p)));
            } catch (IOException | UncheckedIOException e) {
                throw StorageException.readFailed(directory, e);
            }
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) == wantDirectory) {
                    result.add(toFileInfo(entry));
                }
            }
        } catch (IOException e) {
            throw StorageException.readFailed(directory, e);
        }
        result.sort(Comparator.comparing(FileInfo::path));
        return result;
    }

    @Override
    public void makeDirectory(String directory) {
        try {
            Files.createDirectories(resolve(directory));
        } catch (IOException e) {
            throw new StorageException("创建目录失败: " + directory, e);
        }
    }

    @Override
    public boolean deleteDirectory(String directory) {
        Path dir = resolve(directory);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        if (dir.equals(root)) {
            throw new StorageException("不允许删除磁盘根目录: " + directory);
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            // 逆序（先文件后目录）删除
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return true;
        } catch (IOException | UncheckedIOException e) {
            throw new StorageException("删除目录失败: " + directory, e);
        }
    }

    // ==================== URL / 本地路径 ====================

    @Override
    public String url(String path) {
        if (urlPrefix == null) {
            throw new StorageException("磁盘 [" + name + "] 未配置 url 前缀，无法生成访问 URL");
        }
        String relative = path == null ? "" : path.replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        // 校验路径合法性（防越界）
        resolve(relative);
        return urlPrefix + "/" + relative;
    }

    @Override
    public String path(String path) {
        return resolve(path).toString();
    }

    @Override
    public boolean supportsLocalPath() {
        return true;
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * 获取磁盘根目录的绝对路径。
     *
     * @return 根目录
     */
    public Path root() {
        return root;
    }
}
