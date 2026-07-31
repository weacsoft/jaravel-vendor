package com.weacsoft.jaravel.vendor.plugin.jar.database.model;

import com.weacsoft.jaravel.vendor.database.BaseModel;
import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import gaarason.database.annotation.Table;
import gaarason.database.contract.eloquent.Record;
import gaarason.database.eloquent.Model;
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
 * {@link BaseModel#self(Class)} 等方法能经由 Spring 单例执行数据库操作。
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

    /**
     * 获取 Spring 管理的实例，可调用所有 gaarason 方法。
     *
     * @return Spring 容器中本类的管理实例
     */
    public static PluginMetadataModel self() {
        return BaseModel.self(PluginMetadataModel.class);
    }

    /**
     * Eloquent 风格的静态查询入口，对齐 Laravel 的 {@code Model::query()}。
     *
     * @return 新的查询构造器
     */
    public static QueryBuilder<PluginMetadataModel, Long> query() {
        return self().newQuery();
    }

    /**
     * 查询全部记录，对齐 Laravel 的 {@code Model::all()}。
     *
     * @return 全部记录实体列表，无记录时返回空列表
     */
    public static List<PluginMetadataModel> all() {
        return self().findAll().toObjectList();
    }

    /**
     * 按主键查询单条记录，对齐 Laravel 的 {@code Model::find($id)}。
     *
     * @param id 主键值
     * @return 对应实体，未找到返回 {@code null}
     */
    public static PluginMetadataModel find(Long id) {
        // 注意：不能写成 self().find(id)——静态方法 find(Long) 与 gaarason 实例方法同名同参，
        // 编译期会优先解析到本静态方法造成无限递归，故显式以父类类型引用调用实例方法。
        Model<QueryBuilder<PluginMetadataModel, Long>, PluginMetadataModel, Long> model = self();
        Record<PluginMetadataModel, Long> record = model.find(id);
        return record == null ? null : record.toObject();
    }
}
