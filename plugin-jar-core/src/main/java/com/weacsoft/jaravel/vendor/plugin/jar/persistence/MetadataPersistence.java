package com.weacsoft.jaravel.vendor.plugin.jar.persistence;

import com.weacsoft.jaravel.vendor.plugin.jar.model.PluginInfo;

import java.util.List;

/**
 * 插件元数据持久化接口。
 * <p>
 * 抽象持久化层，使插件元数据可存储于不同后端：
 * <ul>
 *   <li>{@link JsonMetadataPersistence}：默认实现，存储为 JSON 文件。</li>
 *   <li>数据库实现：由 {@code plugin-jar-database} 模块提供，基于 jaravel BaseModel。</li>
 * </ul>
 * <p>
 * 仅磁盘持久化的插件（{@code PluginInfo.persisted=true}）才会被持久化与自动恢复；
 * 内存插件（{@code persisted=false}）不参与持久化。
 */
public interface MetadataPersistence {

    /**
     * 保存插件元数据。
     *
     * @param info 插件元信息
     */
    void save(PluginInfo info);

    /**
     * 加载单个插件元数据。
     *
     * @param pluginId 插件 ID
     * @return 插件元信息，不存在返回 {@code null}
     */
    PluginInfo load(String pluginId);

    /**
     * 加载所有已持久化的插件元数据。
     * <p>
     * 仅返回 {@code persisted=true} 的插件。
     *
     * @return 插件元信息列表
     */
    List<PluginInfo> loadAll();

    /**
     * 删除插件元数据。
     *
     * @param pluginId 插件 ID
     */
    void delete(String pluginId);
}
