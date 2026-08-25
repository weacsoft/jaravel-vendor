package com.weacsoft.jaravel.vendor.database;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.weacsoft.jaravel.vendor.core.SpringContext;
import com.weacsoft.jaravel.vendor.core.pagination.Paginator;
import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import gaarason.database.contract.connection.GaarasonDataSource;
import gaarason.database.contract.eloquent.Builder;
import gaarason.database.contract.eloquent.Record;
import gaarason.database.eloquent.Model;
import gaarason.database.provider.ModelShadowProvider;
import gaarason.database.query.QueryBuilder;
import gaarason.database.support.EntityMember;
import gaarason.database.support.ModelMember;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *     // 通过 self() 获取 Spring 管理的实例，可直接调用所有 gaarason 方法
 *     public static User self() { return BaseModel.self(User.class); }
 * }
 *
 * // 使用方式：
 * User user = new User();
 * user.setName("alice");
 * user.save();                       // 持久化（新增），返回保存后的实体
 *
 * // 通过 self() 统一访问所有 gaarason 方法：
 * User found  = User.self().find(1L).toObject();               // 按主键查
 * List&lt;User&gt; all = User.self().findAll().toObjectList();     // 全部
 * User u = User.self().newQuery()
 *                 .where("name", "alice").first().toObject();   // 条件查询
 * User clone = user.replicate();                                // 复制（不含主键）
 *
 * // 需要更多静态快捷方法（find/all/query/updateOrCreate 等）时，业务 Model 自行声明并委托给 self()：
 * public static User find(Long id) { return User.self().find(id).toObject(); }
 * </pre>
 * <p>
 * <b>设计理念</b>：BaseModel 只提供 {@code self()} 作为统一入口（获取 Spring 管理的 Bean），
 * 以及 {@code save()}、{@code replicate()} 等实例方法。其余 gaarason 原生方法（find、findAll、
 * newQuery、updateOrCreate、findOrCreate、create 等）均为实例方法，通过 {@code self()} 链式调用。
 * 不预先包装静态方法，避免框架臆断业务需求；业务 Model 按需自行声明静态快捷方法。
 * <p>
 * <b>self() 与 getSelf()</b>：gaarason 父类 {@link Model} 已有实例方法 {@code getSelf()}（返回 {@code this}），
 * 本类覆盖它以返回 Spring 管理的 Bean（含注入的数据源）。由于 Java 不允许静态方法与继承的实例方法同名同参，
 * 静态访问使用 {@link #self(Class)} 方法。每个业务 Model 声明自己的 {@code public static XxxModel self()}
 * 即可通过 {@code XxxModel.self()} 静态获取 Spring Bean，进而调用所有 gaarason 实例方法。
 * 数据源由宿主侧装配注入（Spring 宿主由 jaravel-springboot 的
 * {@code BaseModelDataSourceBindingPostProcessor} 自动绑定，等效原 {@code @Autowired @Lazy} 语义；
 * 非 Spring 宿主可经 {@link #setGaarasonDataSource(GaarasonDataSource)} 手动绑定），
 * 支持多个 Model 使用不同数据源。业务 Model 可通过 {@link DataSource @DataSource}
 * 注解指定数据源名称，对齐 Laravel Model 的 {@code $connection} 属性；未标注则使用默认数据源。
 * <p>
 * 实现要点：{@code new User()} 创建的是普通实例（非 Spring Bean），调用 {@link #save()}
 * 等实例方法时，统一通过 {@link SpringContext#bean(Class)} 取回本类的宿主单例
 * 来真正执行 gaarason 的查询/写入。单例上的 {@code gaarasonDataSource} 字段由宿主绑定，
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

    /**
     * 数据源（D2 起为纯 Java setter 注入；Spring 宿主由 jaravel-springboot 的
     * {@code BaseModelDataSourceBindingPostProcessor} 自动绑定，承接原 {@code @Autowired @Lazy} 语义），
     * 非数据库字段，需排除 ORM 映射与 JSON 序列化
     */
    @Column(inDatabase = false)
    @JsonIgnore
    private GaarasonDataSource gaarasonDataSource;

    /**
     * 绑定本模型使用的数据源（宿主侧装配，如 Spring 宿主；非 Spring 宿主可手动调用）。
     *
     * @param gaarasonDataSource 数据源实例
     */
    public void setGaarasonDataSource(GaarasonDataSource gaarasonDataSource) {
        this.gaarasonDataSource = gaarasonDataSource;
    }

    /**
     * 覆盖父类的 modelShadow 字段，添加 @Column(inDatabase = false) 排除 ORM 映射。
     * <p>
     * 合并 Model 与 Entity 后，gaarason 会扫描整个类层次字段并默认映射到数据库列。
     * 父类 ModelBase 的 modelShadow 字段未标注 @Column(inDatabase=false)，
     * 此处通过字段隐藏（field hiding）方式覆盖，使 gaarason 反射时取到带排除标注的版本。
     * <p>
     * <b>注意</b>：由于 gaarason 的 {@code EntityMember.dealColumnMap()} 和
     * {@code dealSelectColumnList()} 使用列名（而非字段名）做去重，当子类字段
     * {@code inDatabase=false} 时会被跳过，导致父类字段（{@code inDatabase=true}）
     * 仍然被加入 SELECT 列表。此问题通过<b>双保险</b>修复：
     * <ol>
     *   <li>{@code springboot.database.ModelShadowPatcher}（D2 起位于 jaravel-springboot）
     *       在 Spring 容器就绪后统一移除（同时修补 ModelMember 和 parseAnyEntityWithCache 两个来源）</li>
     *   <li>{@link #getModelMember()} 在每次调用时进行幂等修补，
     *       确保 parseAnyEntityWithCache 新建的 EntityMember 也被处理</li>
     * </ol>
     * <p>
     * 此处的字段隐藏仍有两个作用：
     * <ol>
     *   <li>使 {@code javaFieldMap} 取到子类版本（带 {@code @Column} 注解），JSON 序列化正常</li>
     *   <li>{@code NullFieldConversion} 确保即使数据库中存在 model_shadow 列，反序列化也返回 null</li>
     * </ol>
     */
    @Column(inDatabase = false, conversion = NullFieldConversion.class)
    @JsonIgnore
    protected transient ModelShadowProvider modelShadow;

    /**
     * 需要从 ORM 映射中移除的 gaarason 内部列名
     */
    private static final String SHADOW_COLUMN = "model_shadow";

    /**
     * 重写 getModelMember()，在返回前修补 EntityMember，确保 model_shadow 列
     * 不会出现在 SELECT 查询中。
     * <p>
     * <b>为什么需要重写此方法</b>：gaarason 的 {@code SelectBuilder.select(Class)}
     * 通过 {@code modelShadowProvider.parseAnyEntityWithCache(entityClass)} 获取
     * {@link EntityMember}，而非通过 {@code modelMember.getEntityMember()}。
     * 当实体类因 CGLIB 代理等原因未正确注册到 {@code persistence.entityIndexMap} 时，
     * {@code parseAnyEntityWithCache} 会创建新的未修补的 {@code EntityMember}。
     * <p>
     * 本方法在每次调用 {@code getModelMember()} 时（包括 {@code BaseBuilder.initBuilder()}
     * 和 {@code getEntityClass()} 调用链中），同时修补两个来源的 {@code EntityMember}：
     * <ol>
     *   <li>{@code ModelMember} 自带的 {@code EntityMember}</li>
     *   <li>{@code parseAnyEntityWithCache} 返回的 {@code EntityMember}（select(Class) 实际使用的）</li>
     * </ol>
     * 修补是幂等的：仅在 {@code model_shadow} 仍存在于列表中时才执行移除。
     */
    @Override
    protected ModelMember<QueryBuilder<T, K>, T, K> getModelMember() {
        ModelMember<QueryBuilder<T, K>, T, K> modelMember = super.getModelMember();
        // 1. 修补 ModelMember 自带的 EntityMember
        patchShadowColumn(modelMember.getEntityMember());
        // 2. 修补 parseAnyEntityWithCache 返回的 EntityMember（select(Class) 实际使用的）
        try {
            Class<?> entityClass = modelMember.getEntityClass();
            EntityMember<?, ?> cachedEntityMember = getModelShadow().parseAnyEntityWithCache(entityClass);
            patchShadowColumn(cachedEntityMember);
        } catch (Exception e) {
            // 忽略，步骤 1 已处理
        }
        return modelMember;
    }

    /**
     * 从 EntityMember 的 selectColumnList 和 columnFieldMap 中移除 model_shadow 列。
     * 幂等操作：仅在列名仍存在时才执行移除。
     *
     * @param entityMember 实体元数据
     */
    private static void patchShadowColumn(EntityMember<?, ?> entityMember) {
        if (entityMember == null) {
            return;
        }
        if (entityMember.getSelectColumnList().contains(SHADOW_COLUMN)) {
            entityMember.getSelectColumnList().removeIf(SHADOW_COLUMN::equals);
            entityMember.getColumnFieldMap().remove(SHADOW_COLUMN);
        }
    }

    /**
     * 解析本 Model 使用的数据源。
     * <p>
     * <b>解析顺序</b>（对齐框架「别名优先」的统一约定）：
     * <ol>
     *   <li>由 {@link #getConnectionAlias()} 决定别名（默认读 {@link DataSource @DataSource} 注解，
     *       业务 Model 也可重写）；</li>
     *   <li>先在 {@link ConnectionManager} 注册表中查找 —— 即
     *       {@code @RegisterConnection} 在 {@code config/DatabaseConfig.java} 中声明的连接；</li>
     *   <li>注册表没有，再<b>回退</b>到 Spring 容器中同名的
     *       {@code GaarasonDataSource} / {@code DataSource} bean（裸 DataSource 会用全局
     *       {@code ContainerBootstrap} 自动包装）；</li>
     *   <li>最后回退到 Spring 注入的默认数据源。</li>
     * </ol>
     * 这样「模型里使用的别名」不再直接从 Spring 里找，而是先扫描注册表，找不到才查 Spring。
     *
     * @return 本 Model 对应的 gaarason 数据源
     */
    @Override
    @JsonIgnore
    public GaarasonDataSource getGaarasonDataSource() {
        String alias = getConnectionAlias();

        // 1) 别名优先走注册表（@RegisterConnection）
        if (alias != null && !alias.isEmpty() && ConnectionManager.hasConnection(alias)) {
            return ConnectionManager.connection(alias);
        }

        // 2) 注册表未命中：非默认别名交由 ConnectionManager 回退 Spring 解析
        if (alias != null && !alias.isEmpty()
                && !ConnectionManager.DEFAULT_CONNECTION.equals(alias)) {
            return ConnectionManager.connection(alias);
        }

        // 3) 默认别名：优先使用 Spring 注入的数据源（通常即 @Primary）
        if (gaarasonDataSource != null) {
            return gaarasonDataSource;
        }

        // 4) 最终回退：由 ConnectionManager 解析默认连接
        return ConnectionManager.connection(ConnectionManager.DEFAULT_CONNECTION);
    }

    /**
     * 声明本 Model 使用的数据库连接别名（多数据库支持）。
     * <p>
     * 返回的别名对应 Spring 容器中 {@code GaarasonDataSource} 的 bean 名称（例如
     * {@code gaarasonDataSource}、{@code mysql}、{@code sqlite}、{@code oracle}、{@code pg} 等）。
     * 默认优先级：若类上标注了 {@link DataSource @DataSource} 注解则返回其 {@code value()}，
     * 否则返回 {@link ConnectionManager#defaultConnectionName()}（即被
     * {@code @RegisterConnection(defaultConnection = true)} 设为默认的别名，
     * 默认值是 {@code "sqlite"}，但业务可改为其他任意名称）。
     * <p>
     * 业务 Model 可重写本方法以切换到其它数据库，例如：
     * <pre>
     * &#64;Override
     * protected String getConnectionAlias() {
     *     return "mysql";   // 使用名为 mysql 的 GaarasonDataSource bean
     * }
     * </pre>
     * 这与迁移接口的 {@code Migration#connection()} 命名一致，便于在「同一数据库」上保持
     * 表结构迁移与 ORM 读写使用相同的连接别名。
     *
     * @return 数据库连接别名
     */
    protected String getConnectionAlias() {
        DataSource dsAnnotation = this.getClass().getAnnotation(DataSource.class);
        if (dsAnnotation != null) {
            return dsAnnotation.value();
        }
        return ConnectionManager.defaultConnectionName();
    }

    // ==================== getSelf / self ====================

    /**
     * 覆盖 gaarason 父类的实例方法 {@code getSelf()}，返回 Spring 管理的 Bean 而非 {@code this}。
     * <p>
     * gaarason 父类 {@link Model#getSelf()} 返回 {@code this}，但在本框架中 {@code new User()} 创建的是
     * 普通实例（非 Spring Bean，数据源未注入），需要通过 {@link SpringContext#bean(Class)} 获取
     * Spring 管理的单例来执行数据库操作。本方法确保任何实例上调用 {@code getSelf()} 都返回正确的 Spring Bean。
     * <p>
     * 业务 Model 可通过覆盖此方法实现协变返回类型：
     * <pre>
     * &#64;Override
     * public User getSelf() { return (User) super.getSelf(); }
     * </pre>
     *
     * @return Spring 容器中本类的管理实例
     */
    @Override
    @SuppressWarnings("unchecked")
    public BaseModel<T, K> getSelf() {
        return SpringContext.bean(this.getClass());
    }

    /**
     * 静态获取 Spring 管理的 Model 实例，对齐 Laravel 中通过 {@code Model::query()} 等静态方法
     * 间接获取查询构造器的模式。
     * <p>
     * 由于 Java 不允许静态方法与继承的实例方法同名同参（gaarason 已有实例方法 {@code getSelf()}），
     * 静态访问使用 {@code self()} 而非 {@code getSelf()}。
     * <p>
     * 每个业务 Model 声明自己的静态方法委托给本方法：
     * <pre>
     * public static User self() { return BaseModel.self(User.class); }
     * </pre>
     * 然后即可通过 {@code User.self()} 静态获取 Spring Bean，调用所有 gaarason 实例方法：
     * <pre>
     * User found  = User.self().find(1L).toObject();               // 按主键查
     * List&lt;User&gt; all = User.self().findAll().toObjectList();     // 全部
     * User u      = User.self().newQuery().where("name", "alice").first().toObject();
     * User result = User.self().updateOrCreate(conditions, attributes);
     * </pre>
     *
     * @param modelClass 业务 Model 类（需为 Spring Bean）
     * @return Spring 容器中该类的管理实例
     */
    public static <M extends BaseModel<?, ?>> M self(Class<M> modelClass) {
        return SpringContext.bean(modelClass);
    }

    // ==================== 实例方法 ====================

    /**
     * 返回具备安全 count/aggregate 的查询构造器（兼容 SQLite 等驱动返回的 Integer 型 COUNT 结果）。
     * <p>
     * gaarason 的 {@code paginate()} 通过 {@code clone()} 复制当前 builder（保留运行时类型），
     * 因此分页内部使用的 {@code count()} 同样为本类重写的安全版本，业务代码无需改动。
     */
    @Override
    public QueryBuilder<T, K> newQuery() {
        return JaravelQueryBuilder.wrap(super.newQuery());
    }

    /**
     * 同上，用于不追加默认作用域的查询入口。
     */
    @Override
    public QueryBuilder<T, K> newQueryWithoutApply() {
        return JaravelQueryBuilder.wrap(super.newQueryWithoutApply());
    }

    // ==================== 分页快捷方法 ====================

    /**
     * 分页查询，对齐 Laravel Eloquent 的 {@code Model::paginate($perPage)}。
     * <p>
     * 返回的 {@link Paginator} 可直接在 Blade 模板中一行生成分页器：
     * <pre>
     * // 控制器
     * Paginator&lt;User&gt; list = User.self().paginate(1, 15);
     *
     * // 模板
     * &#64;foreach($list as $u) ... &#64;endforeach
     * {{ $list-&gt;links('layouts.mdui.pageinator') }}
     * </pre>
     * 若总页数不足 2 页或分页模板不存在，{@code links()} 会返回空串，等同于「没执行」。
     *
     * @param currentPage 当前页码（从 1 开始，小于 1 时按 1 处理）
     * @param perPage     每页条数（小于 1 时按 15 处理）
     * @return 分页结果
     */
    public Paginator<T> paginate(int currentPage, int perPage) {
        return paginate(newQuery(), currentPage, perPage);
    }

    /**
     * 分页查询，使用默认每页 15 条。
     *
     * @param currentPage 当前页码
     * @return 分页结果
     */
    public Paginator<T> paginate(int currentPage) {
        return paginate(currentPage, 15);
    }

    /**
     * 对指定查询构造器分页，便于附加 where 条件后再分页。
     * <pre>
     * Paginator&lt;User&gt; list = User.self().paginate(
     *         User.self().newQuery().where("status", "1"), 1, 15);
     * </pre>
     *
     * @param builder     查询构造器
     * @param currentPage 当前页码（从 1 开始）
     * @param perPage     每页条数
     * @return 分页结果
     */
    public Paginator<T> paginate(Builder<?, T, K> builder, int currentPage, int perPage) {
        int page = currentPage <= 0 ? 1 : currentPage;
        int size = perPage <= 0 ? 15 : perPage;
        gaarason.database.appointment.Paginate<T> paginate =
                builder.paginate(page, size);
        List<T> items = paginate.getItemList();
        Long total = paginate.getTotal();
        return new Paginator<>(items, total == null ? 0L : total, size, page);
    }

    /**
     * 持久化当前实体（新增或更新），对齐 Laravel Eloquent 的 {@code $model->save()}。
     * <p>
     * 当主键为 null 时执行 INSERT（新增），当主键已有值时执行 UPDATE（更新）。
     * 通过反射检测 {@link Primary} 注解字段判断主键是否已设值。
     *
     * @return 保存后的实体；INSERT 时返回含生成主键的实体，UPDATE 时返回当前实体
     */
    @SuppressWarnings("unchecked")
    public T save() {
        BaseModel<T, K> bean = (BaseModel<T, K>) SpringContext.bean(this.getClass());
        Object pkValue = getPrimaryKeyValue();
        if (pkValue != null) {
            // UPDATE：主键已存在，执行更新
            String pkName = resolvePrimaryKeyColumnName();
            QueryBuilder<T, K> query = bean.newQuery().where(pkName, pkValue);
            fillColumnData(query);
            query.update();
            return (T) this;
        } else {
            // INSERT：主键为空，执行新增
            Record<T, K> record = bean.create((T) this);
            return record == null ? null : record.toObject();
        }
    }

    /**
     * 反射获取主键字段的值。
     *
     * @return 主键值，未找到 @Primary 字段或值为 null 时返回 null
     */
    private Object getPrimaryKeyValue() {
        Class<?> clazz = this.getClass();
        while (clazz != null && clazz != BaseModel.class && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Primary.class)) {
                    try {
                        field.setAccessible(true);
                        return field.get(this);
                    } catch (IllegalAccessException e) {
                        return null;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * 反射获取主键列名（优先取 @Column(name=...)，否则字段名转 snake_case）。
     * <p>
     * 注意：父类 {@link Model} 已有 public {@code getPrimaryKeyColumnName()} 方法，
     * 此处用不同方法名避免访问权限冲突。
     *
     * @return 主键列名
     */
    private String resolvePrimaryKeyColumnName() {
        Class<?> clazz = this.getClass();
        while (clazz != null && clazz != BaseModel.class && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Primary.class)) {
                    Column column = field.getAnnotation(Column.class);
                    if (column != null && !column.name().isEmpty()) {
                        return column.name();
                    }
                    return toSnakeCase(field.getName());
                }
            }
            clazz = clazz.getSuperclass();
        }
        return "id";
    }

    /**
     * 将实体中所有非主键、非排除字段的值填入查询构造器，用于 UPDATE。
     * <p>
     * 当遇到 {@code updated_at} 列时，自动填充为当前时间字符串（{@code "yyyy-MM-dd HH:mm:ss"}），
     * 因为 {@code query.data()} 直接操作列值，绕过了 gaarason 的 FieldFill 机制。
     *
     * @param query 查询构造器
     */
    @SuppressWarnings("unchecked")
    private void fillColumnData(QueryBuilder<T, K> query) {
        Class<?> clazz = this.getClass();
        while (clazz != null && clazz != BaseModel.class && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                    continue;
                }
                if (field.isAnnotationPresent(Primary.class)) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                if (column != null && !column.inDatabase()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    String colName = (column != null && !column.name().isEmpty())
                            ? column.name() : toSnakeCase(field.getName());
                    // updated_at 列在 UPDATE 时自动刷新为当前时间
                    if ("updated_at".equals(colName)) {
                        query.data(colName, TimestampFill.nowString());
                    } else {
                        query.data(colName, field.get(this));
                    }
                } catch (IllegalAccessException e) {
                    // 跳过无法访问的字段
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 将 camelCase 转为 snake_case。
     *
     * @param input 输入字符串
     * @return snake_case 字符串
     */
    private static String toSnakeCase(String input) {
        if (input == null || input.isEmpty()) {
            return "generated";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
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

    protected String getSoftDeleteColumnName() {
        return "deleted_at";
    }

    //只查询被删除的数据
    @Override
    protected void scopeSoftDeleteOnlyTrashed(Builder<?, T, K> builder) {
        builder.whereNotNull(getSoftDeleteColumnName());
    }

    //查询包含被删除的数据
    @Override
    protected void scopeSoftDeleteWithTrashed(Builder<?, T, K> builder) {
        super.scopeSoftDeleteWithTrashed(builder);
    }

    //查询所有没被删除的数据
    @Override
    protected void scopeSoftDelete(Builder<?, T, K> builder) {
        builder.whereNull(getSoftDeleteColumnName());
    }

    //执行删除
    @Override
    protected int softDelete(Builder<?, T, K> builder) {
        String now = TimestampFill.nowString();
        // 删除时：deleted_at 和 updated_at 都更新为当前时间
        return builder.data(getSoftDeleteColumnName(), now)
                      .data("updated_at", now)
                      .update();
    }

    //撤销执行删除
    @Override
    protected int softDeleteRestore(Builder<?, T, K> builder) {
        // 恢复时：deleted_at 置空，updated_at 更新为当前时间
        return builder.data(getSoftDeleteColumnName(), null)
                      .data("updated_at", TimestampFill.nowString())
                      .update();
    }

    // ==================== 软删除感知的写入操作（withTrash / withoutTrash / onlyTrash） ====================
    //
    // 背景（为什么需要这一段）：
    // gaarason 的 updateOrCreate / findOrCreate / findOrNew 等方法定义在 Model（ModelOfQuery）上，
    // 其内部实现固定为 `newQuery().select(...).where(conditionEntity).first()`，而 newQuery()
    // 会自动附加默认软删除作用域（scopeSoftDelete，即 `WHERE deleted_at IS NULL`）。
    // 同时 withTrashed() / onlyTrashed() 返回的是一个 Builder（QueryBuilder），Builder 上并没有
    // updateOrCreate 等方法（它们只在 Model 上）。二者无法链式组合，因此原生 API 无法表达
    // 「在包含软删除数据的范围内 updateOrCreate」这种语义，导致对一条已被软删除的记录再次
    // updateOrCreate 时会因默认作用域查不到而误判为「不存在」，从而重复 INSERT。
    //
    // 解决方案：在 BaseModel 上提供 withTrash() / onlyTrash() 入口，返回一个链式作用域对象
    // TrashedScope，在其上复刻 gaarason ModelOfQuery 的 updateOrCreate/findOrCreate/findOrNew 逻辑，
    // 但把用于条件查询的 Builder 换成 withTrashed()/onlyTrashed() 产生的、不带默认软删除过滤的 Builder。
    // 由于这些方法返回 gaarason 的 Record，天然支持继续链式调用 Record 上的 restore()/save()/delete()，
    // 从而实现形如：
    //     Admin.self().withTrash().updateOrCreate(condition, complement).restore();
    // 的「更新或创建 + 合并软删除处理」能力，语义对齐 Laravel 的
    //     Admin::withTrashed()->updateOrCreate($condition, $complement)->restore();

    /**
     * 软删除数据的可见范围，决定条件查询时使用哪一个 gaarason 内置作用域构造 Builder。
     */
    public enum TrashScope {
        /** 仅未删除（默认，等价于普通查询）：{@code WHERE deleted_at IS NULL} */
        WITHOUT_TRASHED,
        /** 包含已删除：不追加软删除过滤（对齐 Laravel {@code withTrashed()}） */
        WITH_TRASHED,
        /** 仅已删除：{@code WHERE deleted_at IS NOT NULL}（对齐 Laravel {@code onlyTrashed()}） */
        ONLY_TRASHED
    }

    /**
     * 进入「包含软删除数据」的作用域，对齐 Laravel {@code Model::withTrashed()}。
     * <p>
     * 返回的 {@link TrashedScope} 上可继续调用 {@code updateOrCreate}、{@code findOrCreate}、
     * {@code findOrNew}、{@code first} 等方法；这些方法在<b>包含已软删除记录</b>的范围内进行
     * 条件匹配，从而弥补 gaarason 原生 {@code updateOrCreate} 只能在「未删除」范围内匹配的缺陷。
     * <pre>
     * // 更新或创建后立即恢复（若命中的是已软删除记录）
     * Admin.self().withTrash().updateOrCreate(condition, complement).restore();
     * </pre>
     *
     * @return 包含软删除数据的链式作用域对象
     */
    public TrashedScope withTrash() {
        return new TrashedScope(TrashScope.WITH_TRASHED);
    }

    /**
     * {@link #withTrash()} 的别名，命名对齐 gaarason/Laravel 的 {@code withTrashed()}。
     */
    public TrashedScope withTrashScope() {
        return new TrashedScope(TrashScope.WITH_TRASHED);
    }

    /**
     * 简易填充：将 Map 中的值按属性名调用 setter 写入当前对象。
     *
     * <p>行为约定：</p>
     * <ul>
     *     <li>Map 的键视为属性名（对应 {@code setXxx}），值按字符串处理。</li>
     *     <li><b>键为 {@code null}</b> 或当前对象<b>没有对应 setter</b> 的条目 → 直接忽略（视为多余属性）。</li>
     *     <li><b>值为 {@code null}</b> 或<b>空字符串</b>（长度为 0）→ 视为不做任何操作（跳过，不调用 setter）。</li>
     *     <li>setter 参数类型不一定是 {@code String}，会对字符串做常见类型转换
     *         （数值、布尔、BigDecimal、LocalDate/LocalDateTime/LocalTime、Date、字符等）。</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>{@code
     * user.fill(Map.of("username", "alice", "age", "18", "active", "true"));
     * }</pre>
     *
     * @param data 待填充的键值数据（值统一视为 String 处理）；为 {@code null} 时直接返回当前对象
     * @return this，便于链式调用
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public T fill(Map<String, Object> data) {
        return fill(data, (String[]) null);
    }

    /**
     * 简易填充（限定字段）：将 Map 中指定属性名的值调用 setter 写入当前对象。
     * <p>
     * 与 {@link #fill(Map)} 的区别：第二个参数指定只赋值哪些属性，Map 中其余键值对被忽略。
     * 这在快速增删改查场景中非常有用——例如表单提交的数据包含多个字段，
     * 但你只想更新其中部分字段（防止批量赋值漏洞）。
     *
     * <p>行为约定：与 {@link #fill(Map)} 一致，区别仅在于先按 {@code fields} 过滤 Map。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 只填充 name 和 age，忽略 Map 中的其他键
     * user.fill(formData, "name", "age");
     *
     * // 传入 null 或空数组等价于不限定（填充全部）
     * user.fill(formData, (String[]) null);
     * }</pre>
     *
     * @param data   待填充的键值数据
     * @param fields 只允许赋值的属性名列表；为 {@code null} 或空时等价于 {@link #fill(Map)}
     * @return this，便于链式调用
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public T fill(Map<String, Object> data, String... fields) {
        if (data == null || data.isEmpty()) {
            return (T) this;
        }
        Class<?> entityClass = this.getClass();
        if (entityClass == null) {
            return (T) this;
        }
        // 若指定了字段列表，先过滤 Map，只保留指定字段的键值对
        Map<String, Object> effectiveData = data;
        if (fields != null && fields.length > 0) {
            Set<String> fieldSet = new HashSet<>(Arrays.asList(fields));
            effectiveData = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (entry.getKey() != null && fieldSet.contains(entry.getKey())) {
                    effectiveData.put(entry.getKey(), entry.getValue());
                }
            }
            if (effectiveData.isEmpty()) {
                return (T) this;
            }
        }
        for (Map.Entry<String, Object> entry : effectiveData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // 1. 键为 null → 忽略
            if (key == null) {
                continue;
            }
            // 2. 值为 null 或空字符串 → 不做任何操作
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                continue;
            }
            // 3. 查找对应 setter（set<Key>）
            Method setter = findSetter(entityClass, key);
            if (setter == null) {
                // 多余属性 → 忽略
                continue;
            }
            // 4. 类型转换并写入
            try {
                Class<?> paramType = setter.getParameterTypes()[0];
                Object converted = convertValue(value, paramType);
                if (converted == SKIP) {
                    continue;
                }
                setter.invoke(this, converted);
            } catch (Exception ignored) {
                // 转换或写入失败 → 忽略该字段，不中断整体填充
            }
        }
        return (T) this;
    }

    /**
     * 获取 Model 类的所有业务属性名（不含属性值）。
     * <p>
     * 遍历指定 Model 类的类层次结构（从业务子类到 BaseModel 之前），收集所有非 static、
     * 非 transient、非 {@code @Column(inDatabase = false)} 的字段名。
     * <p>
     * 可用于快速构建增删改查模板页面：获取属性名列表后，可自动生成表单字段、列表表头等。
     *
     * <p>示例：</p>
     * <pre>{@code
     * List<String> fields = BaseModel.getPropertyNames(User.class);
     * // → ["id", "name", "email", "createdAt", ...]
     *
     * // 结合 fill 限定字段使用：
     * user.fill(formData, BaseModel.getPropertyNames(User.class)
     *         .toArray(new String[0]));
     * }</pre>
     *
     * @param modelClass 业务 Model 类
     * @return 属性名列表（按类层次从子类到父类顺序）
     */
    public static List<String> getPropertyNames(Class<?> modelClass) {
        List<String> names = new ArrayList<>();
        Class<?> clazz = modelClass;
        while (clazz != null && clazz != BaseModel.class && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                if (column != null && !column.inDatabase()) {
                    continue;
                }
                names.add(field.getName());
            }
            clazz = clazz.getSuperclass();
        }
        return names;
    }

    /**
     * 获取当前对象的所有业务属性名（实例方法便捷入口）。
     *
     * @return 属性名列表
     * @see #getPropertyNames(Class)
     */
    public List<String> getPropertyNames() {
        return getPropertyNames(this.getClass());
    }

    /** 内部哨兵：表示因转换失败而跳过该字段。 */
    private static final Object SKIP = new Object();

    /**
     * 在实体类及其父类上查找 {@code set<Property>} 单参 setter。
     */
    private Method findSetter(Class<?> clazz, String property) {
        if (property == null || property.isEmpty()) {
            return null;
        }
        String setterName = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(setterName)
                        && m.getParameterCount() == 1
                        && !Modifier.isStatic(m.getModifiers())) {
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * 将 value（统一按字符串看待）转换为 targetType。转换失败返回 {@link #SKIP}。
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return SKIP;
        }
        // 已经是目标类型（或目标为 Object）→ 直接返回
        if (targetType.isInstance(value) || targetType == Object.class) {
            return value;
        }
        String str = value.toString().trim();
        if (str.isEmpty()) {
            return SKIP;
        }
        try {
            if (targetType == String.class) {
                return str;
            }
            if (targetType == char.class || targetType == Character.class) {
                return str.charAt(0);
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return parseBoolean(str);
            }
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(str);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(str);
            }
            if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(str);
            }
            if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(str);
            }
            if (targetType == short.class || targetType == Short.class) {
                return Short.parseShort(str);
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return Byte.parseByte(str);
            }
            if (targetType == BigDecimal.class) {
                return new BigDecimal(str);
            }
            if (targetType == LocalDate.class) {
                return LocalDate.parse(str);
            }
            if (targetType == LocalDateTime.class) {
                return parseDateTime(str);
            }
            if (targetType == LocalTime.class) {
                return LocalTime.parse(str);
            }
            if (targetType == Date.class) {
                return java.sql.Timestamp.valueOf(parseDateTime(str));
            }
            // 兜底：尝试 valueOf(String) 或单参 String 构造函数
            try {
                Method valueOf = targetType.getMethod("valueOf", String.class);
                return valueOf.invoke(null, str);
            } catch (NoSuchMethodException e) {
                return targetType.getConstructor(String.class).newInstance(str);
            }
        } catch (Exception e) {
            return SKIP;
        }
    }

    private static boolean parseBoolean(String str) {
        if ("1".equals(str) || "true".equalsIgnoreCase(str) || "yes".equalsIgnoreCase(str) || "y".equalsIgnoreCase(str)) {
            return true;
        }
        if ("0".equals(str) || "false".equalsIgnoreCase(str) || "no".equalsIgnoreCase(str) || "n".equalsIgnoreCase(str)) {
            return false;
        }
        return Boolean.parseBoolean(str);
    }

    private static LocalDateTime parseDateTime(String str) {
        // 兼容常见格式：带 T 的 ISO、带空格的 "yyyy-MM-dd HH:mm:ss"、纯日期
        String normalized = str.contains("T") ? str : str.replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            // 退化为日期（时间补 00:00）
            return LocalDate.parse(str).atStartOfDay();
        }
    }

    /**
     * 进入「仅软删除数据」的作用域，对齐 Laravel {@code Model::onlyTrashed()}。
     * <p>
     * 在<b>仅已软删除</b>的范围内进行条件匹配，可用于「仅在回收站中更新或创建」等场景。
     *
     * @return 仅软删除数据的链式作用域对象
     */
    public TrashedScope onlyTrash() {
        return new TrashedScope(TrashScope.ONLY_TRASHED);
    }

    /**
     * 显式进入「不含软删除数据」的作用域（与默认行为一致），便于统一写法。
     *
     * @return 不含软删除数据的链式作用域对象
     */
    public TrashedScope withoutTrash() {
        return new TrashedScope(TrashScope.WITHOUT_TRASHED);
    }

    /**
     * 根据软删除可见范围构造用于条件查询的 Builder。
     * <p>
     * 复用 gaarason 内置的 {@code withTrashed()} / {@code onlyTrashed()} / {@code newQuery()}，
     * 分别对应「包含已删除」「仅已删除」「仅未删除」三种作用域，确保软删除过滤条件正确应用。
     *
     * @param scope 软删除可见范围
     * @return 已附加对应作用域的查询构造器
     */
    @SuppressWarnings("unchecked")
    private Builder<QueryBuilder<T, K>, T, K> newScopedQuery(TrashScope scope) {
        BaseModel<T, K> bean = (BaseModel<T, K>) SpringContext.bean(this.getClass());
        switch (scope) {
            case WITH_TRASHED:
                return bean.withTrashed();
            case ONLY_TRASHED:
                return bean.onlyTrashed();
            case WITHOUT_TRASHED:
            default:
                return bean.newQuery();
        }
    }

    /**
     * 软删除感知的链式作用域对象。
     * <p>
     * 复刻 gaarason {@code ModelOfQuery} 的 {@code updateOrCreate}/{@code findOrCreate}/
     * {@code findOrNew} 逻辑，但条件查询所用的 Builder 由 {@link #newScopedQuery(TrashScope)}
     * 按软删除范围构造，使这些「查不到则新建、查得到则更新/复用」的操作能够感知软删除记录。
     * 返回值均为 gaarason {@link Record}，可继续链式调用 {@code restore()}/{@code save()}/{@code delete()} 等。
     */
    public final class TrashedScope {

        private final TrashScope scope;

        private TrashedScope(TrashScope scope) {
            this.scope = scope;
        }

        /**
         * 在当前软删除范围内「更新或创建」，对齐 Laravel {@code updateOrCreate($condition, $complement)}。
         * <p>
         * 依据 {@code conditionEntity} 的非空字段作为匹配条件查询首条记录：
         * <ul>
         *   <li>命中：用 {@code complementEntity} 补全并 {@code save()}（执行 UPDATE），返回该记录；</li>
         *   <li>未命中：以 {@code conditionEntity + complementEntity} 组合新建并 {@code save()}（执行 INSERT），返回新记录。</li>
         * </ul>
         * 与 gaarason 原生方法的差异仅在于：条件查询是在 {@link TrashScope} 指定的范围（如包含软删除）内进行。
         *
         * @param conditionEntity  匹配条件实体（非空字段参与 WHERE）
         * @param complementEntity 补充/更新用实体
         * @return 更新或创建后的记录，可继续 {@code .restore()} 等链式操作
         */
        public Record<T, K> updateOrCreate(T conditionEntity, T complementEntity) {
            BaseModel<T, K> bean = (BaseModel<T, K>) SpringContext.bean(BaseModel.this.getClass());
            Record<T, K> first = newScopedQuery(scope)
                    .select(bean.getEntityClass())
                    .where(conditionEntity)
                    .first();
            if (first != null) {
                first.fillEntity(complementEntity);
                first.save();
                return first;
            }
            Record<T, K> theRecord = bean.newRecord();
            theRecord.getEntity(conditionEntity);
            theRecord.fillEntity(complementEntity);
            theRecord.save();
            return theRecord;
        }

        /**
         * 在当前软删除范围内「查询或新建」（不持久化），对齐 Laravel {@code firstOrNew}。
         * <p>
         * 命中则返回已绑定数据的记录；未命中则返回一个以 {@code entity} 填充、尚未持久化的新记录
         * （不写库，需自行 {@code save()}）。
         *
         * @param entity 匹配条件实体
         * @return 已存在或新建（未持久化）的记录
         */
        public Record<T, K> findOrNew(T entity) {
            BaseModel<T, K> bean = (BaseModel<T, K>) SpringContext.bean(BaseModel.this.getClass());
            Record<T, K> first = newScopedQuery(scope)
                    .select(bean.getEntityClass())
                    .where(entity)
                    .first();
            if (first != null) {
                return first;
            }
            Record<T, K> theRecord = bean.newRecord();
            theRecord.getEntity(entity);
            return theRecord;
        }

        /**
         * 在当前软删除范围内「查询或创建」，对齐 Laravel {@code firstOrCreate}。
         * <p>
         * 命中则直接返回；未命中则以 {@code entity} 新建并持久化后返回。
         *
         * @param entity 匹配条件 / 新建用实体
         * @return 已存在或新建（已持久化）的记录
         */
        public Record<T, K> findOrCreate(T entity) {
            Record<T, K> theRecord = findOrNew(entity);
            if (!theRecord.isHasBind()) {
                theRecord.save();
            }
            return theRecord;
        }

        /**
         * 在当前软删除范围内「查询或创建」（条件与补充分离），对齐 Laravel
         * {@code firstOrCreate($condition, $complement)}。
         *
         * @param conditionEntity  匹配条件实体
         * @param complementEntity 未命中时用于补全的实体
         * @return 已存在或新建（已持久化）的记录
         */
        public Record<T, K> findOrCreate(T conditionEntity, T complementEntity) {
            BaseModel<T, K> bean = (BaseModel<T, K>) SpringContext.bean(BaseModel.this.getClass());
            Record<T, K> first = newScopedQuery(scope)
                    .select(bean.getEntityClass())
                    .where(conditionEntity)
                    .first();
            if (first != null) {
                return first;
            }
            Record<T, K> theRecord = bean.newRecord();
            theRecord.getEntity(conditionEntity);
            theRecord.fillEntity(complementEntity);
            theRecord.save();
            return theRecord;
        }

        /**
         * 在当前软删除范围内按条件实体查询首条记录，命中返回记录，未命中返回 {@code null}。
         *
         * @param conditionEntity 匹配条件实体
         * @return 首条匹配记录或 {@code null}
         */
        public Record<T, K> first(T conditionEntity) {
            BaseModel<T, K> bean = (BaseModel<T, K>) SpringContext.bean(BaseModel.this.getClass());
            return newScopedQuery(scope)
                    .select(bean.getEntityClass())
                    .where(conditionEntity)
                    .first();
        }

        /**
         * 返回当前软删除范围对应的 Builder，便于在其上继续拼接自定义查询条件后再执行。
         * <p>
         * 例如：{@code self().withTrash().query().where("status", 1).get()}。
         *
         * @return 已附加对应软删除作用域的查询构造器
         */
        public Builder<QueryBuilder<T, K>, T, K> query() {
            return newScopedQuery(scope);
        }
    }
}
