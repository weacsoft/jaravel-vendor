package com.weacsoft.jaravel.vendor.database;

import com.weacsoft.jaravel.vendor.core.SpringContext;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import gaarason.database.contract.connection.GaarasonDataSource;
import gaarason.database.contract.eloquent.Record;
import gaarason.database.contract.eloquent.RecordList;
import gaarason.database.eloquent.Model;
import gaarason.database.provider.ModelShadowProvider;
import gaarason.database.query.QueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;

/**
 * Eloquent Model 基类，对齐 Laravel 的 {@code Illuminate\Database\Eloquent\Model}。
 * <p>
 * 业务 Model 继承本类并加 {@code @Repository}，泛型为「实体类型, 主键类型」，
 * 即可像 Laravel 一样用<b>单一类</b>同时承担实体定义与查询职责，无需再拆分
 * 「User 实体 POJO」与「UserModel 查询类」：
 * <pre>
 * &#64;Repository
 * &#64;Table(name = "users")
 * public class User extends BaseModel&lt;User, Long&gt; {
 *     &#64;Primary &#64;Column private Long id;
 *     &#64;Column private String name;
 *     // getter/setter ...
 *
 *     // 静态查询方法（委托给 BaseModel 工具方法，Java 静态方法不可继承故需手动声明）
 *     public static User find(Long id)                  { return BaseModel.find(User.class, id); }
 *     public static List&lt;User&gt; all()                    { return BaseModel.all(User.class); }
 *     public static QueryBuilder&lt;User, Long&gt; query()    { return BaseModel.query(User.class); }
 * }
 *
 * // 使用方式：
 * User user = new User();
 * user.setName("alice");
 * user.save();                       // 持久化（新增），返回保存后的实体
 * User found  = User.find(1L);       // 按主键查
 * List&lt;User&gt; all = User.all();       // 全部
 * User u = User.query().where("name", "alice").first().toObject(); // 条件查询
 * User clone = user.replicate();     // 复制（不含主键）
 * </pre>
 * 数据源通过 {@code @Resource @Lazy} 由 Spring 容器注入（懒加载，避免循环依赖），
 * 支持多个 Model 使用不同数据源。业务 Model 可通过 {@link DataSource @DataSource}
 * 注解指定数据源 Bean 名称，对齐 Laravel Model 的 {@code $connection} 属性；未标注则使用默认数据源。
 * <p>
 * 实现要点：{@code new User()} 创建的是普通实例（非 Spring Bean），调用 {@link #save()}
 * 等实例方法或静态查询时，统一通过 {@link SpringContext#bean(Class)} 取回本类的 Spring 单例
 * 来真正执行 gaarason 的查询/写入。Spring 单例上的 {@code gaarasonDataSource} 字段由容器注入，
 * 因此所有数据库操作均经由单例完成。
 * <p>
 * JSON 序列化：通过 {@link JsonAutoDetect} 仅序列化字段（不通过 getter），避免 gaarason
 * 父类的内部属性（数据源、线程池等）被 Jackson 序列化。
 *
 * @param <T> 实体类型
 * @param <K> 主键类型
 */
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public abstract class BaseModel<T, K> extends Model<QueryBuilder<T, K>, T, K> {

    /** 数据源（由 Spring 容器懒注入），非数据库字段，需排除 ORM 映射与 JSON 序列化 */
    @Autowired
    @Lazy
    @Column(inDatabase = false)
    @JsonIgnore
    private GaarasonDataSource gaarasonDataSource;

    /**
     * 覆盖父类的 modelShadow 字段，添加 @Column(inDatabase = false) 排除 ORM 映射。
     * <p>
     * 合并 Model 与 Entity 后，gaarason 会扫描整个类层次字段并默认映射到数据库列。
     * 父类 ModelBase 的 modelShadow 字段未标注 @Column(inDatabase=false)，
     * 此处通过字段隐藏（field hiding）方式覆盖，使 gaarason 反射时取到带排除标注的版本。
     */
    @Column(inDatabase = false, conversion = NullFieldConversion.class)
    @JsonIgnore
    protected transient ModelShadowProvider modelShadow;

    @Override
    @JsonIgnore
    public GaarasonDataSource getGaarasonDataSource() {
        // 优先检查 @DataSource 注解，对齐 Laravel Model 的 $connection 属性
        // 必须在注入字段之前检查，否则 @Autowired 总是注入 @Primary 数据源
        DataSource dsAnnotation = this.getClass().getAnnotation(DataSource.class);
        if (dsAnnotation != null) {
            return SpringContext.bean(dsAnnotation.value(), GaarasonDataSource.class);
        }
        // 未标注 @DataSource 时，使用 Spring 注入的数据源（默认为 @Primary）
        if (gaarasonDataSource != null) {
            return gaarasonDataSource;
        }
        // 回退：从容器获取默认数据源
        return SpringContext.bean(GaarasonDataSource.class);
    }

    // ==================== 实例方法 ====================

    /**
     * 持久化当前实体（新增），对齐 Laravel Eloquent 的 {@code $model->save()}。
     * <p>
     * 当前实例由 {@code new} 创建（非 Spring Bean），通过 {@link SpringContext} 取出本类的
     * Spring 单例执行 {@code create(this)}，返回保存后的实体（含生成的主键）。
     *
     * @return 保存后的实体；无记录时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public T save() {
        BaseModel<T, K> bean = (BaseModel<T, K>) SpringContext.bean(this.getClass());
        Record<T, K> record = bean.create((T) this);
        return record == null ? null : record.toObject();
    }

    /**
     * 复制当前实体（排除主键），对齐 Laravel Eloquent 的 {@code $model->replicate()}。
     * <p>
     * 反射创建同类型新实例，拷贝业务字段，跳过 {@link Primary} 主键字段，
     * 使其可作为新记录再次 {@link #save()}。
     *
     * @return 不含主键的副本
     */
    @SuppressWarnings("unchecked")
    public T replicate() {
        try {
            T copy = (T) this.getClass().getDeclaredConstructor().newInstance();
            copyFieldsExcludingPrimaryKey(copy);
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("replicate failed for " + this.getClass().getName(), e);
        }
    }

    /**
     * 反射拷贝当前实例的字段到目标实例，跳过主键与 static/transient 字段。
     * 仅遍历业务子类层级（不含 BaseModel 自身的缓存字段）。
     */
    private void copyFieldsExcludingPrimaryKey(Object target) throws ReflectiveOperationException {
        Class<?> clazz = this.getClass();
        while (clazz != null && clazz != BaseModel.class && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                    continue;
                }
                if (field.isAnnotationPresent(Primary.class)) {
                    continue; // 排除主键
                }
                field.setAccessible(true);
                field.set(target, field.get(this));
            }
            clazz = clazz.getSuperclass();
        }
    }

    // ==================== 静态工具方法（供业务 Model 的静态方法委托） ====================
    // Java 静态方法不可被子类继承，故业务 Model 需自行声明静态方法并委托给此处。

    /**
     * 按主键查询，对齐 Laravel Eloquent 的 {@code Model::find($id)}。
     * <pre>
     * public static User find(Long id) { return BaseModel.find(User.class, id); }
     * </pre>
     *
     * @param modelClass 业务 Model 类（需为 Spring Bean）
     * @param id         主键值
     * @return 实体，未找到返回 {@code null}
     */
    public static <M extends BaseModel<T, K>, T, K> T find(Class<M> modelClass, K id) {
        M bean = SpringContext.bean(modelClass);
        Record<T, K> record = bean.find(id);
        return record == null ? null : record.toObject();
    }

    /**
     * 查询全部记录，对齐 Laravel Eloquent 的 {@code Model::all()}。
     * <pre>
     * public static List&lt;User&gt; all() { return BaseModel.all(User.class); }
     * </pre>
     *
     * @param modelClass 业务 Model 类（需为 Spring Bean）
     * @return 实体列表
     */
    public static <M extends BaseModel<T, K>, T, K> List<T> all(Class<M> modelClass) {
        M bean = SpringContext.bean(modelClass);
        RecordList<T, K> records = bean.findAll();
        return records == null ? Collections.emptyList() : records.toObjectList();
    }

    /**
     * 构造查询构造器，对齐 Laravel Eloquent 的 {@code Model::query()}。
     * <pre>
     * public static QueryBuilder&lt;User, Long&gt; query() { return BaseModel.query(User.class); }
     * User u = User.query().where("name", "alice").first().toObject();
     * </pre>
     *
     * @param modelClass 业务 Model 类（需为 Spring Bean）
     * @return 查询构造器
     */
    @SuppressWarnings("unchecked")
    public static <M extends BaseModel<T, K>, T, K> QueryBuilder<T, K> query(Class<M> modelClass) {
        M bean = SpringContext.bean(modelClass);
        return bean.newQuery();
    }
}
