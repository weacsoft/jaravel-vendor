package com.weacsoft.jaravel.vendor.plugin.jar.persistence;

import com.weacsoft.jaravel.vendor.json.Json;
import com.weacsoft.jaravel.vendor.plugin.jar.model.PluginInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 JSON 文件的元数据持久化实现。
 * <p>
 * 每个插件的元数据存储于 {@code pluginsDir/{pluginId}/metadata.json}。
 * {@code loadAll} 扫描 {@code pluginsDir} 下的所有子目录（跳过 {@code shared} 目录），
 * 读取其中的 {@code metadata.json}，仅返回 {@code persisted=true} 的插件。
 * <p>
 * 线程安全：每次读写都通过 {@link Json} 序列化/反序列化，无共享可变状态。
 * 文件操作由调用方（{@code HotPluginManager}）通过 ReadWriteLock 串行化。
 */
public class JsonMetadataPersistence implements MetadataPersistence {

    private static final Logger log = LoggerFactory.getLogger(JsonMetadataPersistence.class);

    private static final String METADATA_FILE = "metadata.json";
    private static final String SHARED_DIR = "shared";

    private final Path pluginsDir;

    /**
     * 构造 JSON 元数据持久化。
     *
     * @param pluginsDir    插件目录
     */
    public JsonMetadataPersistence(Path pluginsDir) {
        this.pluginsDir = pluginsDir;
    }

    @Override
    public void save(PluginInfo info) {
        if (info == null || info.getPluginId() == null) {
            return;
        }
        // 仅持久化磁盘插件
        if (!info.isPersisted()) {
            return;
        }
        try {
            Path pluginDir = pluginsDir.resolve(info.getPluginId());
            Files.createDirectories(pluginDir);
            Path metadataFile = pluginDir.resolve(METADATA_FILE);
            Json.writeToPrettyFile(metadataFile.toFile(), info);
        } catch (IOException e) {
            log.error("保存插件元数据失败: {}", info.getPluginId(), e);
        }
    }

    @Override
    public PluginInfo load(String pluginId) {
        if (pluginId == null) {
            return null;
        }
        Path metadataFile = pluginsDir.resolve(pluginId).resolve(METADATA_FILE);
        if (!Files.exists(metadataFile)) {
            return null;
        }
        try {
            PluginInfo info = Json.readFromFile(metadataFile.toFile(), PluginInfo.class);
            // 仅返回磁盘持久化的插件
            if (info != null && info.isPersisted()) {
                return info;
            }
            return null;
        } catch (Exception e) {
            log.error("加载插件元数据失败: {}", pluginId, e);
            return null;
        }
    }

    @Override
    public List<PluginInfo> loadAll() {
        List<PluginInfo> result = new ArrayList<>();
        if (!Files.exists(pluginsDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                String dirName = entry.getFileName().toString();
                // 跳过 shared 目录
                if (SHARED_DIR.equals(dirName)) {
                    continue;
                }
                Path metadataFile = entry.resolve(METADATA_FILE);
                if (!Files.exists(metadataFile)) {
                    continue;
                }
                try {
                    PluginInfo info = Json.readFromFile(metadataFile.toFile(), PluginInfo.class);
                    if (info != null && info.isPersisted()) {
                        result.add(info);
                    }
                } catch (Exception e) {
                    log.error("加载插件元数据失败: {}", metadataFile, e);
                }
            }
        } catch (IOException e) {
            log.error("扫描插件目录失败: {}", pluginsDir, e);
        }
        return result;
    }

    @Override
    public void delete(String pluginId) {
        if (pluginId == null) {
            return;
        }
        Path pluginDir = pluginsDir.resolve(pluginId);
        Path metadataFile = pluginDir.resolve(METADATA_FILE);
        try {
            Files.deleteIfExists(metadataFile);
            // 若插件目录为空则删除
            File[] children = pluginDir.toFile().listFiles();
            if (children == null || children.length == 0) {
                Files.deleteIfExists(pluginDir);
            }
        } catch (IOException e) {
            log.error("删除插件元数据失败: {}", pluginId, e);
        }
    }
}
