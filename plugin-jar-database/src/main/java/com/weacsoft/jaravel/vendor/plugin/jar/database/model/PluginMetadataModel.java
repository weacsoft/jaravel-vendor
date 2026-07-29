package com.weacsoft.jaravel.vendor.plugin.jar.database.model;

import com.weacsoft.jaravel.vendor.database.BaseModel;
import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import gaarason.database.annotation.Table;
import gaarason.database.query.QueryBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 插件元数据数据库模型，映射到 {@code plugin_metadata} 表。
 * <p>
 * 继承 jaravel 的 {@link BaseModel}，遵循 Eloquent 模式：单一类同时承担实体定义与查询职责。
 * 通过 {@code @Repository} 注册为 Spring Bean，使 {@link BaseModel#save()}、
 * {@link BaseModel#find(Class, Object)} 等方法能经由 Spring 单例执行数据库操作。
 * <p>
 * 复杂字段（{@code sharedClassDependencies}、{@code componentClasses}、{@code routeMappings}）
 * 以 JSON 字符串形式存储在 TEXT 列中，由 {@code ModelMetadataPersistence} 负责序列化/反序列化。
 *
 * @see com.weacsoft.jaravel.vendor.plugin.jar.database.persistence.ModelMetadataPersistence
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Repository
@Table(name = "plugin_metadata")
public class PluginMetadataModel extends BaseModel<PluginMetadataModel, Long> {

    /** 主键 */
    @Primary
    @Column(name = "id")
    private Long id;

    /** 插件 ID */
    @Column(name = "plugin_id")
    private String pluginId;

    /** 插件版本 */
    @Column(name = "version")
    private String version;

    /** JAR 路径 */
    @Column(name = "jar_path")
    private String jarPath;

    /** 插件状态：UPLOADED、ENABLED、DISABLED */
    @Column(name = "state")
    private String state;

    /** 共享类依赖（JSON 数组字符串） */
    @Column(name = "shared_class_dependencies")
    private String sharedClassDependencies;

    /** 组件类（JSON 数组字符串） */
    @Column(name = "component_classes")
    private String componentClasses;

    /** 路由映射（JSON 数组字符串） */
    @Column(name = "route_mappings")
    private String routeMappings;

    /** 错误信息 */
    @Column(name = "error_message")
    private String errorMessage;

    /** 是否磁盘持久化 */
    @Column(name = "persisted")
    private Boolean persisted;

    /** 创建时间 */
    @Column(name = "created_at")
    private String createdAt;

    /** 更新时间 */
    @Column(name = "updated_at")
    private String updatedAt;

    // ==================== 静态查询方法（委托给 BaseModel） ====================

    /**
     * 获取 Spring 管理的实例，可调用所有 gaarason 方法。
     *
     * @return Spring 容器中本类的管理实例
     */
    public static PluginMetadataModel self() {
        return BaseModel.self(PluginMetadataModel.class);
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 实体，未找到返回 {@code null}
     */
    public static PluginMetadataModel find(Long id) {
        return BaseModel.find(PluginMetadataModel.class, id);
    }

    /**
     * 查询全部记录。
     *
     * @return 实体列表
     */
    public static List<PluginMetadataModel> all() {
        return BaseModel.all(PluginMetadataModel.class);
    }

    /**
     * 构造查询构造器。
     *
     * @return 查询构造器
     */
    public static QueryBuilder<PluginMetadataModel, Long> query() {
        return BaseModel.query(PluginMetadataModel.class);
    }

    /**
     * 查找匹配条件的记录，存在则更新，不存在则创建。
     *
     * @param conditions 查找条件（列名 → 值）
     * @param attributes 需更新/创建的属性（列名 → 值）
     * @return 操作后的实体
     */
    public static PluginMetadataModel updateOrCreate(Map<String, Object> conditions, Map<String, Object> attributes) {
        return BaseModel.updateOrCreate(PluginMetadataModel.class, conditions, attributes);
    }

    /**
     * 查找匹配条件的记录，不存在则创建。
     *
     * @param conditions 查找条件（列名 → 值）
     * @param attributes 不存在时创建使用的属性（列名 → 值）
     * @return 找到或新创建的实体
     */
    public static PluginMetadataModel firstOrCreate(Map<String, Object> conditions, Map<String, Object> attributes) {
        return BaseModel.firstOrCreate(PluginMetadataModel.class, conditions, attributes);
    }

    /**
     * 查找匹配条件的记录，不存在则返回新实例（未持久化）。
     *
     * @param conditions 查找条件（列名 → 值）
     * @param attributes 不存在时新实例的属性（列名 → 值）
     * @return 找到的实体或新实例（未持久化）
     */
    public static PluginMetadataModel firstOrNew(Map<String, Object> conditions, Map<String, Object> attributes) {
        return BaseModel.firstOrNew(PluginMetadataModel.class, conditions, attributes);
    }
}
