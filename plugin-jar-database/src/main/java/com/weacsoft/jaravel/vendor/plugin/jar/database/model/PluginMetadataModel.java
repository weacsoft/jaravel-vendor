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
}
