package com.weacsoft.jaravel.vendor.plugin.jar.database.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weacsoft.jaravel.vendor.plugin.jar.database.model.PluginMetadataModel;
import com.weacsoft.jaravel.vendor.plugin.jar.model.PluginInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.model.RouteInfo;
import com.weacsoft.jaravel.vendor.plugin.jar.persistence.MetadataPersistence;
import gaarason.database.contract.eloquent.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于数据库的插件元数据持久化实现。
 * <p>
 * 使用 jaravel 的 {@link PluginMetadataModel}（Eloquent Model）进行 CRUD 操作，
 * 复杂字段（{@code Set<String>}、{@code Set<RouteInfo>}）通过 Jackson {@link ObjectMapper}
 * 序列化为 JSON 字符串存储在数据库 TEXT 列中。
 * <p>
 * 与 {@link com.weacsoft.jaravel.vendor.plugin.jar.persistence.JsonMetadataPersistence} 语义一致：
 * 仅持久化 {@code persisted=true} 的磁盘插件，内存插件不参与持久化。
 * <p>
 * 线程安全：{@link ObjectMapper} 本身线程安全，数据库操作由 gaarason 底层连接池保证。
 * 并发写入由调用方（{@code HotPluginManager}）通过 ReadWriteLock 串行化。
 */
public class ModelMetadataPersistence implements MetadataPersistence {

    private static final Logger log = LoggerFactory.getLogger(ModelMetadataPersistence.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void save(PluginInfo info) {
        if (info == null || info.getPluginId() == null) {
            return;
        }
        // 仅持久化磁盘插件，与 JsonMetadataPersistence 语义一致
        if (!info.isPersisted()) {
            return;
        }

        try {
            // BaseModel.save() 始终执行 INSERT，故先删除已有记录再新增
            // 使用 QueryBuilder.forceDelete() 执行物理删除（plugin_metadata 表无软删除列）
            PluginMetadataModel.query().where("plugin_id", info.getPluginId()).forceDelete();

            PluginMetadataModel model = new PluginMetadataModel();
            model.setPluginId(info.getPluginId());
            model.setVersion(info.getVersion());
            model.setJarPath(info.getJarPath());
            model.setState(info.getState() != null ? info.getState().name() : null);
            model.setSharedClassDependencies(toJson(info.getSharedClassDependencies()));
            model.setComponentClasses(toJson(info.getComponentClasses()));
            model.setRouteMappings(toJsonRouteInfos(info.getRouteMappings()));
            model.setErrorMessage(info.getErrorMessage());
            model.setPersisted(info.isPersisted());
            model.save();
        } catch (Exception e) {
            log.error("保存插件元数据失败: {}", info.getPluginId(), e);
        }
    }

    @Override
    public PluginInfo load(String pluginId) {
        if (pluginId == null) {
            return null;
        }
        PluginMetadataModel model = findModel(pluginId);
        if (model == null) {
            return null;
        }
        PluginInfo info = toPluginInfo(model);
        // 仅返回磁盘持久化的插件，与 JsonMetadataPersistence 语义一致
        return info.isPersisted() ? info : null;
    }

    @Override
    public List<PluginInfo> loadAll() {
        return PluginMetadataModel.all().stream()
            .map(this::toPluginInfo)
            .filter(PluginInfo::isPersisted)
            .collect(Collectors.toList());
    }

    @Override
    public void delete(String pluginId) {
        if (pluginId == null) {
            return;
        }
        try {
            // 使用 QueryBuilder.forceDelete() 执行物理删除
            PluginMetadataModel.query().where("plugin_id", pluginId).forceDelete();
        } catch (Exception e) {
            log.error("删除插件元数据失败: {}", pluginId, e);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 按 {@code plugin_id} 查询模型。
     *
     * @param pluginId 插件 ID
     * @return 模型实例，未找到返回 {@code null}
     */
    private PluginMetadataModel findModel(String pluginId) {
        Record<PluginMetadataModel, Long> record = PluginMetadataModel.query()
            .where("plugin_id", pluginId)
            .first();
        return record == null ? null : record.toObject();
    }

    /**
     * 将数据库模型转换为 {@link PluginInfo}。
     *
     * @param model 数据库模型
     * @return 插件元信息
     */
    private PluginInfo toPluginInfo(PluginMetadataModel model) {
        PluginInfo info = new PluginInfo();
        info.setPluginId(model.getPluginId());
        info.setVersion(model.getVersion());
        info.setJarPath(model.getJarPath());
        info.setState(model.getState() != null ? PluginInfo.State.valueOf(model.getState()) : null);
        info.setSharedClassDependencies(fromJson(model.getSharedClassDependencies()));
        info.setComponentClasses(fromJson(model.getComponentClasses()));
        info.setRouteMappings(fromJsonRouteInfos(model.getRouteMappings()));
        info.setErrorMessage(model.getErrorMessage());
        info.setPersisted(model.getPersisted() != null && model.getPersisted());
        return info;
    }

    // ==================== JSON 序列化辅助方法 ====================

    /**
     * 将 {@code Set<String>} 序列化为 JSON 数组字符串。
     *
     * @param set 字符串集合
     * @return JSON 字符串，如 {@code ["a","b"]}
     */
    private String toJson(Set<String> set) {
        if (set == null || set.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(set);
        } catch (Exception e) {
            log.error("序列化 Set<String> 失败", e);
            return "[]";
        }
    }

    /**
     * 将 JSON 数组字符串反序列化为 {@code Set<String>}。
     *
     * @param json JSON 字符串
     * @return 字符串集合
     */
    private Set<String> fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new HashSet<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Set<String>>() {});
        } catch (Exception e) {
            log.error("反序列化 Set<String> 失败: {}", json, e);
            return new HashSet<>();
        }
    }

    /**
     * 将 {@code Set<RouteInfo>} 序列化为 JSON 数组字符串。
     * <p>
     * 每个 {@link RouteInfo} 序列化为包含 {@code path}、{@code method}、{@code beanName}、
     * {@code methodName}、{@code produces} 字段的 JSON 对象。
     *
     * @param routes 路由信息集合
     * @return JSON 字符串
     */
    private String toJsonRouteInfos(Set<RouteInfo> routes) {
        if (routes == null || routes.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(routes);
        } catch (Exception e) {
            log.error("序列化 Set<RouteInfo> 失败", e);
            return "[]";
        }
    }

    /**
     * 将 JSON 数组字符串反序列化为 {@code Set<RouteInfo>}。
     *
     * @param json JSON 字符串
     * @return 路由信息集合
     */
    private Set<RouteInfo> fromJsonRouteInfos(String json) {
        if (json == null || json.isEmpty()) {
            return new HashSet<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Set<RouteInfo>>() {});
        } catch (Exception e) {
            log.error("反序列化 Set<RouteInfo> 失败: {}", json, e);
            return new HashSet<>();
        }
    }
}
