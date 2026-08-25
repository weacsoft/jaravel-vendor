package com.weacsoft.jaravel.vendor.http.upload;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 上传文件落盘助手（请求/响应侧，http 模块）。
 * <p>
 * {@link MultipartFile} 是 HTTP 请求侧概念，因此其落盘能力（解析原始文件名 + 交给写入目标）
 * 收敛在 http 模块；storage 等<b>纯 JVM 核心层模块不感知 Spring</b>，
 * 通过 {@link Target} 函数式接口把「写入哪、怎么写」交给调用方，
 * 不反向依赖 storage 的 {@code Filesystem} 契约。
 * <p>
 * <h3>使用示例</h3>
 * <pre>
 * // 存储到 storage 磁盘（storage 模块的 Filesystem::put 直接适配 Target）
 * MultipartFile avatar = request.file("avatar");
 * String path = UploadFile.store(avatar, "avatars", storage.disk("public")::put);
 *
 * // 指定文件名
 * String path = UploadFile.storeAs(avatar, "reports", "a-2026.pdf", fs::put);
 * </pre>
 */
public final class UploadFile {

    /**
     * 写入目标：接收最终相对路径与字节内容。
     * <p>
     * 例：{@code (path, bytes) -> filesystem.put(path, bytes)}。
     * 实现需要自行保证父目录存在（storage 的 {@code put} 会自动创建）。
     */
    @FunctionalInterface
    public interface Target {
        void store(String path, byte[] contents) throws IOException;
    }

    private UploadFile() {
    }

    /**
     * 解析上传文件的原始文件名。
     * <p>
     * 只取最后一段路径（防止文件名携带路径字符造成目录污染），
     * 文件为 {@code null} 或原始文件名为空时回退为 {@code "file"}。
     *
     * @param file 上传文件，可为 {@code null}
     * @return 文件名（不含路径）
     */
    public static String baseName(MultipartFile file) {
        String name = file == null ? null : file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "file";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = slash < 0 ? name : name.substring(slash + 1);
        return name.isEmpty() ? "file" : name;
    }

    /**
     * 将上传文件存入目标（使用原始文件名）。
     *
     * @param file   上传文件，不能为 {@code null}
     * @param dir    目标目录（相对根；{@code null}/空串表示根目录）
     * @param target 写入目标，不能为 {@code null}
     * @return 实际写入的相对路径（形如 {@code dir + "/" + 文件名}）
     * @throws IOException 读取上传流或写入失败
     */
    public static String store(MultipartFile file, String dir, Target target) throws IOException {
        return storeAs(file, dir, baseName(file), target);
    }

    /**
     * 将上传文件存入目标（使用指定文件名）。
     *
     * @param file   上传文件，不能为 {@code null}
     * @param dir    目标目录（相对根；{@code null}/空串表示根目录）
     * @param name   目标文件名，空时回退为 {@code "file"}
     * @param target 写入目标，不能为 {@code null}
     * @return 实际写入的相对路径
     * @throws IOException 读取上传流或写入失败
     */
    public static String storeAs(MultipartFile file, String dir, String name, Target target) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("上传文件不能为 null");
        }
        if (target == null) {
            throw new IllegalArgumentException("Target 不能为 null");
        }
        if (name == null || name.isBlank()) {
            name = "file";
        }
        byte[] content = file.getBytes();
        if (dir == null || dir.isBlank()) {
            target.store(name, content);
            return name;
        }
        String d = dir.replace('\\', '/').trim();
        while (d.startsWith("/")) {
            d = d.substring(1);
        }
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        String path = d + "/" + name;
        target.store(path, content);
        return path;
    }
}
