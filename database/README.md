# Database 模块（Eloquent ORM）

> 包名：`com.weacsoft.jaravel.vendor.database`
> 对齐 Laravel 特性：`Illuminate\Database\Eloquent`（Model 基类、多数据源、EloquentUserProvider）

## 目录

- [模块概述](#模块概述)
- [Maven 依赖](#maven-依赖)
- [类总览](#类总览)
- [BaseModel](#basemodel)
- [DataSource 注解](#datasource-注解)
- [EloquentUserProvider](#eloquentuserprovider)
- [配置项（application.yml）](#配置项applicationyml)
- [完整使用示例](#完整使用示例)
- [线程安全说明](#线程安全说明)

---

## 模块概述

Database 模块是 Jaravel 框架的数据库 ORM 层，基于 `gaarason/database-query`（Laravel 风格的 Java ORM），对齐 Laravel 的 `Illuminate\Database\Eloquent`。它提供了三大核心能力：

- **BaseModel（合并模型）**：对齐 Laravel Eloquent 的「单一类同时承担实体定义与查询职责」设计。业务 Model 继承 `BaseModel` 后，无需再拆分「User 实体 POJO」与「UserModel 查询类」，一个类即可完成实体定义、增删改查、查询构造。内置软删除支持（基于 `deleted_at` 列，对齐 Laravel `SoftDeletes` trait）。
- **@DataSource 注解 + getConnectionAlias() 方法（多数据源）**：对齐 Laravel Model 的 `$connection` 属性。通过 `@DataSource` 注解指定数据源 Bean 名称，或重写 `BaseModel.getConnectionAlias()` 方法以编程方式切换数据库连接，支持多数据库。
- **EloquentUserProvider（认证集成）**：对齐 Laravel 的 `EloquentUserProvider`，基于 Eloquent Model 实现 `UserProvider` 契约，仅负责按主键/凭证取出用户，不校验密码。

### 与 Laravel 的对齐关系

| Laravel | Jaravel Database 模块 |
|---|---|
| `Illuminate\Database\Eloquent\Model` | `BaseModel` |
| Model 的 `$connection` 属性 | `@DataSource` 注解 |
| `EloquentUserProvider` | `EloquentUserProvider` |
| `$model->save()` | `BaseModel.save()` |
| `Model::find($id)` | `BaseModel.find(Class, id)` |
| `Model::all()` | `BaseModel.all(Class)` |
| `Model::query()` | `BaseModel.query(Class)` |
| `$model->replicate()` | `BaseModel.replicate()` |
| `orderBy('col', 'desc')` | `orderBy("col", "desc")` / `orderBy("col", OrderBy.DESC)` |
| `Model::query()->where()->delete()` | `query().where("id", id).delete()` |
| `->get()->toObjectList()` | `query().where().get().toObjectList()` |
| `SoftDeletes` trait | `BaseModel` 软删除作用域方法（覆盖 gaarason 内置） |
| `deleted_at` 列（SoftDeletes） | `deleted_at` 列（默认，可通过 `getSoftDeleteColumnName()` 自定义） |
| `$model->delete()`（软删除） | `softDelete()` 设置 `deleted_at = now()` |
| `$model->restore()` | `softDeleteRestore()` 设置 `deleted_at = NULL` |
| `Model::withTrashed()` | `scopeSoftDeleteWithTrashed` 查询包含已删除记录 |
| `Model::onlyTrashed()` | `scopeSoftDeleteOnlyTrashed` 仅查询已删除记录 |
| `Model::withTrashed()->updateOrCreate(...)` | `withTrash().updateOrCreate(...)`（软删除感知的更新或创建，返回可 `restore()` 的 `Record`） |

### 关键设计决策

#### 1. 合并 Model 与 Entity

Laravel Eloquent 的核心特性是「一个类既是实体又是查询入口」。Java 传统 ORM（如 MyBatis）通常需要拆分为「实体 POJO」+「Mapper/DAO」两个类。本模块通过 `BaseModel` 实现了 Laravel 风格的合并模型：

```java
// 一个类搞定一切
@Repository
@Table(name = "users")
public class User extends BaseModel<User, Long> {
    @Primary @Column private Long id;
    @Column private String name;

    // 实例方法：save()、replicate()
    // 静态方法：find()、all()、query()
}
```

#### 2. EloquentUserProvider 简化

对齐 auth 模块的设计决策，`EloquentUserProvider` 仅负责通过 Eloquent Model 按主键/凭证**取出**用户，**不**负责校验密码。不再包含 `CredentialMatcher` 与 `validateCredentials`。

---

## Maven 依赖

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>database</artifactId>
    <version>0.1.2</version>
</dependency>
```

该模块传递依赖：
- `core` 模块（提供 `SpringContext`）
- `auth` 模块（提供 `Authenticatable`、`UserProvider` 契约）
- `io.github.gaarason:database-query`（Laravel 风格 ORM 核心，依赖 `database-core` + Druid）
- `com.alibaba:druid`（数据源）
- `spring-boot-starter-jdbc` / `spring-boot-starter-aop`（供 gaarason 核心使用）

> 注意：本模块使用 `gaarason/database-query`（不含 SpringBoot 自动配置），
> 连接由 `config/DatabaseConfig.java` 中的 `@RegisterConnection` 声明，
> 执行 `artisan vendor:publish --tag=database` 生成。

---

## 类总览

```
com.weacsoft.jaravel.vendor.database
├── BaseModel                          # Eloquent Model 基类（合并实体 + 查询）
├── DataSource                         # @DataSource 注解（按别名选择连接）
├── RegisterConnection                 # @RegisterConnection 注解（别名注册连接，替代 @Bean）
├── ConnectionManager                  # 连接注册表 + 全局唯一 ContainerBootstrap 持有者
├── EloquentUserProvider               # 基于 Eloquent 的用户提供者（认证集成）
└── autoconfigure/
    ├── ConnectionRegistrar            # 扫描 @RegisterConnection 并注册到 ConnectionManager
    └── DatabasePublishableConfig      # vendor:publish --tag=database 的模板
```

---

## BaseModel

Eloquent Model 基类，对齐 Laravel 的 `Illuminate\Database\Eloquent\Model`。

业务 Model 继承本类并加 `@Repository`，泛型为「实体类型, 主键类型」，即可像 Laravel 一样用**单一类**同时承担实体定义与查询职责。

### 类定义

```java
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public abstract class BaseModel<T, K> extends Model<QueryBuilder<T, K>, T, K>
```

| 泛型参数 | 说明 |
|---|---|
| `T` | 实体类型（业务 Model 自身类型） |
| `K` | 主键类型 |

### JSON 序列化

通过 `@JsonAutoDetect` 仅序列化字段（不通过 getter），避免 gaarason 父类的内部属性（数据源、线程池等）被 Jackson 序列化。

### 字段说明

| 字段 | 注解 | 说明 |
|---|---|---|
| `gaarasonDataSource` | `@Autowired @Lazy @Column(inDatabase=false) @JsonIgnore` | 数据源（由 Spring 容器懒注入），非数据库字段 |
| `modelShadow` | `@Column(inDatabase=false) @JsonIgnore` | 覆盖父类字段，排除 ORM 映射 |

### 数据源解析：getGaarasonDataSource()

覆盖父类的 `getGaarasonDataSource()` 方法，实现多数据源支持。解析顺序：

```
getGaarasonDataSource()
  ├── 1. 调用 getConnectionAlias() 取得连接别名（业务 Model 可重写此方法）
  │     ├── 别名存在且可解析 → SpringContext.bean(alias, GaarasonDataSource.class)
  │     └── 别名为 "primary" 但未单独注册 → 回退到 @Primary 数据源
  ├── 2. 检查 @DataSource 注解（getConnectionAlias() 默认即返回其 value）
  │     ├── 存在 → SpringContext.bean(annotation.value(), GaarasonDataSource.class)
  │     └── 不存在 → 继续
  ├── 3. 检查 Spring 注入的数据源
  │     ├── gaarasonDataSource != null → 返回注入的数据源（默认 @Primary）
  │     └── 为 null → 继续
  └── 4. 回退：从容器获取默认数据源
        └── SpringContext.bean(GaarasonDataSource.class)
```

`getConnectionAlias()`（默认实现）：若类标注了 `@DataSource` 注解则返回其 `value()`，否则返回 `"primary"`。
**业务 Model 可直接重写 `getConnectionAlias()` 来选择不同的数据库连接**，优先级高于 `@DataSource` 注解，
且与迁移接口的 `Migration#connection()` 命名一致（同一数据库上的「表结构迁移」与「ORM 读写」使用相同别名）。

```java
@Repository
@Table(name = "products")
public class Product extends BaseModel<Product, Long> {
    // 方法式连接切换，优先级高于 @DataSource 注解
    @Override
    protected String getConnectionAlias() {
        return "mysql";   // 使用名为 mysql 的 GaarasonDataSource bean
    }
}
```

> **关键**：必须先检查 `getConnectionAlias()` / `@DataSource` 注解，否则 `@Autowired` 总是注入 `@Primary` 数据源。

### 实例方法

#### save()

持久化当前实体（新增或更新），对齐 Laravel Eloquent 的 `$model->save()`。当主键为 `null` 时执行 INSERT（新增），当主键已设置时执行 UPDATE（更新）。

```java
/**
 * 当前实例由 new 创建（非 Spring Bean），通过 SpringContext 取出本类的
 * Spring 单例执行持久化：主键为 null 时执行 INSERT（新增），主键已设置时
 * 执行 UPDATE（更新），返回保存后的实体。
 *
 * @return 保存后的实体；无记录时返回 null
 */
public T save()
```

示例：

```java
// 新增：主键为 null，执行 INSERT
User user = new User();
user.setName("alice");
user.setNumber("1001");
User saved = user.save();  // 持久化（新增），返回含主键的实体
System.out.println(saved.getId());  // 生成的主键

// 更新：主键已设置，执行 UPDATE
User found = User.find(1L);
found.setName("alice_updated");
User updated = found.save();  // 持久化（更新），返回更新后的实体
```

#### replicate()

复制当前实体（排除主键），对齐 Laravel Eloquent 的 `$model->replicate()`。

```java
/**
 * 反射创建同类型新实例，拷贝业务字段，跳过 @Primary 主键字段，
 * 使其可作为新记录再次 save()。
 *
 * @return 不含主键的副本
 */
public T replicate()
```

复制规则：
- 反射创建同类型新实例
- 拷贝业务字段
- 跳过 `@Primary` 主键字段
- 跳过 `static` / `transient` 字段
- 仅遍历业务子类层级（不含 BaseModel 自身的缓存字段）

示例：

```java
User original = User.find(1L);
User copy = original.replicate();  // 复制（不含主键）
copy.setName("alice_copy");
User saved = copy.save();          // 作为新记录保存
```

### 软删除

`BaseModel` 覆盖了 gaarason 内置的软删除作用域方法，使用 `deleted_at`（可空 `LocalDateTime`）列实现软删除，对齐 Laravel 的 `SoftDeletes` trait。开启后，普通查询会自动排除已软删除的记录，删除操作变为「设置 `deleted_at`」而非物理删除。

> **与 Laravel 默认实现的差异**：Laravel 的 `SoftDeletes` trait 默认即使用 `deleted_at` 时间戳列，语义完全一致；本模块沿用相同的列名与语义，区别在于底层是通过覆盖 gaarason 的作用域方法（gaarason 内置可能使用不同的默认列名）来统一为 `deleted_at`，使业务代码与 Laravel 习惯保持一致。配合 migration 模块的 `table.softDeletes()` 可直接生成同名列。

#### 软删除列

- 默认列名：`deleted_at`
- 类型：可空 `LocalDateTime`（`NULL` 表示未删除，非 `NULL` 表示已软删除）
- 自定义：业务 Model 可覆盖 `getSoftDeleteColumnName()` 修改列名

#### 方法说明

| 方法 | 可见性 | 说明 |
|------|--------|------|
| `getSoftDeleteColumnName` | `protected` | 返回软删除列名，默认 `deleted_at`，业务 Model 可覆盖以自定义列名 |
| `scopeSoftDelete` | `protected`（覆盖） | 默认查询作用域，仅查询未删除记录（`WHERE deleted_at IS NULL`） |
| `scopeSoftDeleteOnlyTrashed` | `protected`（覆盖） | 仅查询已软删除的记录（`WHERE deleted_at IS NOT NULL`） |
| `scopeSoftDeleteWithTrashed` | `protected`（覆盖） | 查询包含已删除的全部记录（不加软删除过滤条件） |
| `softDelete` | `protected`（覆盖） | 执行软删除，设置 `deleted_at = LocalDateTime.now()`，返回受影响行数 |
| `softDeleteRestore` | `protected`（覆盖） | 撤销软删除，设置 `deleted_at = NULL`，返回受影响行数 |

> 这些方法为 gaarason 软删除钩子，参数 `Builder<?, T, K>` 来自 `gaarason.database.contract.eloquent.Builder`，由 gaarason 查询构造器在对应场景下自动调用（如 `onlyTrashed()`、`withTrashed()`、`delete()`、`restore()` 等），业务代码通常无需直接调用。

#### 使用示例

```java
// 默认查询自动排除已软删除的记录（WHERE deleted_at IS NULL）
List<User> activeUsers = User.query().get().toObjectList();

// 仅查询已软删除的记录（触发 scopeSoftDeleteOnlyTrashed）
List<User> trashedUsers = User.query().onlyTrashed().get().toObjectList();

// 查询包含已删除的全部记录（触发 scopeSoftDeleteWithTrashed）
List<User> allUsers = User.query().withTrashed().get().toObjectList();

// 软删除：调用 delete() 时触发 softDelete()，设置 deleted_at 而非物理删除
int affected = User.query().where("id", 1L).delete();

// 恢复软删除的记录：deleted_at 置 NULL（触发 softDeleteRestore）
int restored = User.query().onlyTrashed().where("id", 1L).restore();
```

#### 软删除感知的「更新或创建」（withTrash + updateOrCreate + restore）

gaarason 原生的 `updateOrCreate` / `findOrCreate` / `findOrNew` 定义在 Model 上，其内部固定使用 `newQuery()`，而 `newQuery()` 会自动附加默认软删除作用域（`WHERE deleted_at IS NULL`）；同时 `withTrashed()` / `onlyTrashed()` 返回的是 `Builder`，其上并没有 `updateOrCreate` 等方法。二者无法链式组合，因此原生 API **无法**表达「在包含软删除数据的范围内 updateOrCreate」。

由此带来的问题是：对一条**已被软删除**的记录再次 `updateOrCreate` 时，因默认作用域查不到该记录而误判为「不存在」，从而**重复 INSERT**（甚至触发唯一键冲突）。

`BaseModel` 为此提供了软删除感知的入口方法，复刻 gaarason 的 updateOrCreate 逻辑但将条件查询所用 Builder 替换为对应软删除作用域，返回 gaarason `Record`，可继续链式调用 `restore()` / `save()` / `delete()`：

| 入口方法 | 对齐 Laravel | 说明 |
| --- | --- | --- |
| `withTrash()` | `Model::withTrashed()` | 在**包含已软删除**记录的范围内进行条件匹配 |
| `onlyTrash()` | `Model::onlyTrashed()` | 在**仅已软删除**记录的范围内进行条件匹配 |
| `withoutTrash()` | 默认行为 | 在**仅未删除**记录的范围内进行条件匹配 |

三个入口均返回 `TrashedScope`，其上提供 `updateOrCreate` / `findOrCreate` / `findOrNew` / `first` / `query` 方法。

```java
// 更新或创建，并在命中的是「已软删除记录」时立即恢复
// 对齐 Laravel：Admin::withTrashed()->updateOrCreate($condition, $complement)->restore();
Admin condition = new Admin();
condition.setUsername("root");
Admin complement = new Admin();
complement.setNickname("超级管理员");

Admin.self().withTrash()
     .updateOrCreate(condition, complement)   // 在含软删除范围内匹配：命中则 UPDATE，未命中则 INSERT
     .restore();                              // 若命中的是已软删除记录，则恢复（deleted_at 置 NULL）

// 仅在回收站中查询或创建
Admin.self().onlyTrash().findOrCreate(condition);

// 在含软删除范围内查询或新建（不持久化），随后自行处理
Record<Admin, Long> rec = Admin.self().withTrash().findOrNew(condition);

// 获取带软删除作用域的 Builder 继续拼接自定义条件
List<Admin> list = Admin.self().withTrash().query()
        .where("status", 1)
        .get().toObjectList();
```

> 注：链式末端的 `restore()` 是 gaarason `Record` 上的方法（针对单条记录做软删除恢复），因此仅在该记录确为软删除状态时才产生恢复效果；若命中的是正常记录或新建记录，`restore()` 为幂等的无害操作。

#### 业务 Model 自定义软删除列名

默认列名为 `deleted_at`，若数据库使用其他列名（如 `removed_at`），业务 Model 覆盖 `getSoftDeleteColumnName()` 即可：

```java
@Repository
@Table(name = "users")
public class User extends BaseModel<User, Long> {
    @Primary @Column private Long id;
    @Column private String name;
    @Column private LocalDateTime removedAt;  // 自定义软删除列

    // 覆盖默认列名（默认为 "deleted_at"）
    @Override
    protected String getSoftDeleteColumnName() {
        return "removed_at";
    }

    // 静态查询方法、getter/setter ...
}
```

### 静态工具方法

> Java 静态方法不可被子类继承，故业务 Model 需自行声明静态方法并委托给 `BaseModel` 的静态方法。

#### find()

按主键查询，对齐 Laravel Eloquent 的 `Model::find($id)`。

```java
public static <M extends BaseModel<T, K>, T, K> T find(Class<M> modelClass, K id)
```

| 参数 | 说明 |
|---|---|
| `modelClass` | 业务 Model 类（需为 Spring Bean） |
| `id` | 主键值 |
| 返回值 | 实体，未找到返回 `null` |

#### all()

查询全部记录，对齐 Laravel Eloquent 的 `Model::all()`。

```java
public static <M extends BaseModel<T, K>, T, K> List<T> all(Class<M> modelClass)
```

| 参数 | 说明 |
|---|---|
| `modelClass` | 业务 Model 类（需为 Spring Bean） |
| 返回值 | 实体列表，无记录返回空列表 |

#### query()

构造查询构造器，对齐 Laravel Eloquent 的 `Model::query()`。

```java
public static <M extends BaseModel<T, K>, T, K> QueryBuilder<T, K> query(Class<M> modelClass)
```

| 参数 | 说明 |
|---|---|
| `modelClass` | 业务 Model 类（需为 Spring Bean） |
| 返回值 | 查询构造器 |

### 业务 Model 声明模板

```java
@Repository
@Table(name = "users")
public class User extends BaseModel<User, Long> {
    @Primary
    @Column
    private Long id;

    @Column
    private String name;

    @Column
    private String number;

    // 静态查询方法（委托给 BaseModel 工具方法，Java 静态方法不可继承故需手动声明）
    public static User find(Long id) {
        return BaseModel.find(User.class, id);
    }

    public static List<User> all() {
        return BaseModel.all(User.class);
    }

    public static QueryBuilder<User, Long> query() {
        return BaseModel.query(User.class);
    }

    // getter/setter ...
}
```

### 使用方式

```java
// 新增（主键为 null 时执行 INSERT）
User user = new User();
user.setName("alice");
user.save();                       // 持久化（新增或更新），返回保存后的实体

// 更新（主键已设置时执行 UPDATE）
User found = User.find(1L);
found.setName("alice_updated");
found.save();                      // 持久化（更新），返回更新后的实体

// 按主键查
User found = User.find(1L);

// 全部
List<User> all = User.all();

// 条件查询
User u = User.query().where("name", "alice").first().toObject();

// 排序 + 列表查询（支持字符串方向或 OrderBy 枚举）
List<User> users = User.query()
        .where("status", 1)
        .orderBy("created_at", "desc")           // 字符串方向
        .get()
        .toObjectList();

// 类型安全排序（gaarason.database.appointment.OrderBy 枚举）
import gaarason.database.appointment.OrderBy;
List<User> users2 = User.query()
        .where("status", 1)
        .orderBy("created_at", OrderBy.DESC)     // 枚举方向
        .get()
        .toObjectList();

// 通过查询构造器删除
int deleted = User.query().where("id", 1L).delete();  // 返回受影响行数

// 软删除：调用 delete() 时设置 deleted_at（而非物理删除），默认查询自动排除已删除记录
User.query().where("id", 1L).delete();                          // 软删除
List<User> trashed = User.query().onlyTrashed().get().toObjectList();  // 仅查已删除
List<User> withTrashed = User.query().withTrashed().get().toObjectList(); // 含已删除
User.query().onlyTrashed().where("id", 1L).restore();           // 恢复软删除

// 复制（不含主键）
User clone = user.replicate();
clone.save();                      // 作为新记录保存
```

### 实现要点

`new User()` 创建的是普通实例（非 Spring Bean），调用 `save()` 等实例方法或静态查询时，统一通过 `SpringContext.bean(Class)` 取回本类的 Spring 单例来真正执行 gaarason 的查询/写入。Spring 单例上的 `gaarasonDataSource` 字段由容器注入，因此所有数据库操作均经由单例完成。

```
new User()  ──save()──→  SpringContext.bean(User.class)  ──→  gaarason create()/update()
                              (Spring 单例，已注入数据源)
                              主键为 null → create()（INSERT）
                              主键已设置 → update()（UPDATE）
```

---

## DataSource 注解

指定 Model 使用的数据源 Bean 名称，对齐 Laravel Model 的 `$connection` 属性。

### 定义

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataSource {
    /** 数据源 Bean 名称 */
    String value();
}
```

### 使用示例

```java
// 使用默认（Primary）数据源
@Repository
@Table(name = "users")
public class User extends BaseModel<User, Long> {
    // ...
}

// 指定 secondary 连接（对应 @RegisterConnection("secondary")）
@DataSource("secondary")
@Repository
@Table(name = "products")
public class Product extends BaseModel<Product, Long> {
    // ...
}

// 指定 log 连接（对应 @RegisterConnection("log")）
@DataSource("log")
@Repository
@Table(name = "operation_logs")
public class OperationLog extends BaseModel<OperationLog, Long> {
    // ...
}
```

> `@DataSource` 填的是**连接别名**，不是 Spring bean name。别名在
> `config/DatabaseConfig.java` 中用 `@RegisterConnection("别名")` 声明。

### 解析逻辑

`BaseModel.getGaarasonDataSource()` 先由 `getConnectionAlias()` 决定别名（默认读 `@DataSource`，
业务 Model 可重写），随后**先查 `ConnectionManager` 注册表，找不到再回退 Spring 容器**：

```java
@Override
public GaarasonDataSource getGaarasonDataSource() {
    String alias = getConnectionAlias();

    // 1) 别名优先走注册表（@RegisterConnection）
    if (alias != null && !alias.isEmpty() && ConnectionManager.hasConnection(alias)) {
        return ConnectionManager.connection(alias);
    }

    // 2) 注册表未命中：非默认别名交由 ConnectionManager 回退 Spring 解析
    //    （同名 GaarasonDataSource bean → 同名 DataSource bean，后者自动用全局 Container 包装）
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
 * 连接别名，默认：标注 @DataSource 时返回其 value，否则返回 "primary"。
 * 业务 Model 可重写以切换到其它数据库。
 */
protected String getConnectionAlias() {
    DataSource dsAnnotation = this.getClass().getAnnotation(DataSource.class);
    if (dsAnnotation != null) {
        return dsAnnotation.value();
    }
    return "primary";
}
```

> 未标注此注解的 Model 使用默认（Primary）数据源。

---

## EloquentUserProvider

基于 `gaarason/database-all` 的用户提供者，对齐 Laravel 的 `EloquentUserProvider`。

### 核心设计

仅负责通过 Eloquent Model 按主键/凭证**取出**用户，**不**负责校验密码。密码校验是应用层的责任（在 Controller / Service 中完成），因此本类不再包含 `CredentialMatcher` 与 `validateCredentials`。

### 类定义

```java
public class EloquentUserProvider<T extends Authenticatable, K> implements UserProvider
```

| 泛型参数 | 说明 |
|---|---|
| `T` | 用户实体类型（需实现 `Authenticatable`） |
| `K` | 主键类型 |

### 构造器

```java
public EloquentUserProvider(Model<?, T, K> model, String credentialField)
```

| 参数 | 说明 |
|---|---|
| `model` | Eloquent Model（Spring 单例） |
| `credentialField` | 凭证字段名（如 `"number"`），用于 `retrieveByCredentials` |

### 方法文档

#### retrieveById()

```java
/**
 * 按主键取出用户。
 * @param identifier 主键值
 * @return 用户实体，未找到返回 null
 */
@Override
public Authenticatable retrieveById(Object identifier)
```

实现逻辑：

```java
Record<T, K> record = model.find(identifier);
return record == null ? null : record.toObject();
```

#### retrieveByCredentials()

```java
/**
 * 按凭证取出用户，仅用于查询，不校验密码。
 * @param credentials 查询凭证（字段名 -> 值）
 * @return 用户实体，未找到返回 null
 */
@Override
public Authenticatable retrieveByCredentials(Map<String, Object> credentials)
```

实现逻辑：

```java
Object value = credentials.get(credentialField);
if (value == null) return null;
Record<T, K> record = model.newQuery().where(credentialField, value).first();
return record == null ? null : record.toObject();
```

> 仅使用构造时指定的 `credentialField` 从凭证 Map 中取值查询。

### 认证流程示例

```java
// 1. 应用层按凭证查出用户
User user = (User) provider.retrieveByCredentials(Map.of("number", "1001"));

// 2. 应用层自行校验密码（生产环境应使用 BCrypt）
if (user == null || !encoder.matches(inputPassword, user.getPassword())) {
    throw new RuntimeException("工号或密码错误");
}

// 3. 登入（Auth 以主键比对，不涉及密码）
Auth.login(user);
```

---

## 配置项（application.yml）

数据库配置类由 `artisan vendor:publish --tag=database` 生成：

```bash
artisan vendor:publish --tag=database
```

生成 `config/DatabaseConfig.java`，内含全局唯一的 `ContainerBootstrap` 与 `@RegisterConnection` 连接注册。

### 数据源配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/main_db?useSSL=false&serverTimezone=UTC
    username: root
    password: secret
    driver-class-name: com.mysql.cj.jdbc.Driver

# 额外连接（供 @RegisterConnection 读取，键名自定）
jaravel:
  database:
    log:
      url: jdbc:mysql://localhost:3306/log_db?useSSL=false&serverTimezone=UTC
      username: root
      password: secret
```

### ContainerBootstrap：必须全局唯一

gaarason 在 SpringBoot 环境下，**每个 `GaarasonDataSource` 都必须携带 `ContainerBootstrap`**，
且所有数据源必须**共用同一个实例**，否则 Model 注册表、类型转换器会分裂到不同容器，导致查询报错。

发布出来的 `DatabaseConfig` 已处理好这件事：`containerBootstrap()` 创建唯一实例并存入
`ConnectionManager`，其余连接方法只需声明 `ContainerBootstrap` 参数即可复用，**切勿另行 `build()`**。

```java
@Configuration
public class DatabaseConfig {

    /** 创建并初始化全局唯一的 gaarason Container。 */
    @Bean
    public ContainerBootstrap containerBootstrap(@Autowired Environment env) {
        String scanPackages = env.getProperty("gaarason.database.scan.packages",
                "com.example.demo.app.model");
        if (System.getProperty("gaarason.database.scan.packages") == null) {
            System.setProperty("gaarason.database.scan.packages", scanPackages);
        }

        ContainerBootstrap bootstrap = ContainerBootstrap.build();
        bootstrap.defaultRegister();

        ModelInstanceProvider modelInstanceProvider = bootstrap.getBean(ModelInstanceProvider.class);
        modelInstanceProvider.register(modelClass -> SpringContext.bean(modelClass));

        bootstrap.bootstrapGaarasonAutoconfiguration();
        bootstrap.initialization();

        // 存入框架门面，保证全框架自始至终使用同一个 ContainerBootstrap
        ConnectionManager.setContainer(bootstrap);
        return bootstrap;
    }

    /** 默认连接（别名 primary）：Model 未标注 @DataSource 时使用。 */
    @RegisterConnection(value = "primary", defaultConnection = true)
    public GaarasonDataSource primaryConnection(Environment env, ContainerBootstrap bootstrap) {
        DruidDataSource druid = new DruidDataSource();
        druid.setUrl(env.getProperty("spring.datasource.url"));
        druid.setDriverClassName(env.getProperty("spring.datasource.driver-class-name"));
        druid.setUsername(env.getProperty("spring.datasource.username"));
        druid.setPassword(env.getProperty("spring.datasource.password"));
        return GaarasonDataSourceBuilder.build(druid, bootstrap);
    }

    /** 额外连接（别名 log）：Model 上写 @DataSource("log") 即可使用。 */
    @RegisterConnection("log")
    public GaarasonDataSource logConnection(Environment env, ContainerBootstrap bootstrap) {
        DruidDataSource druid = new DruidDataSource();
        druid.setUrl(env.getProperty("jaravel.database.log.url"));
        druid.setDriverClassName("com.mysql.cj.jdbc.Driver");
        druid.setUsername(env.getProperty("jaravel.database.log.username"));
        druid.setPassword(env.getProperty("jaravel.database.log.password"));
        // 复用同一个 bootstrap，切勿另行 build()
        return GaarasonDataSourceBuilder.build(druid, bootstrap);
    }
}
```

### @RegisterConnection：别名 + 注解（而非 @Bean）

与 auth 的 `@RegisterGuard`、cache 的 `@RegisterCacheStore` 是同一套机制：

| 对比项 | `@Bean("mysql")` | `@RegisterConnection("mysql")` |
|--------|------------------|-------------------------------|
| 别名与 bean name | 耦合，全局唯一 | 解耦，可自由取名 |
| 同名 bean 冲突 | 抛 `BeanDefinitionOverrideException` | 不会冲突 |
| 是否进 Spring 容器 | 是 | 否，登记到 `ConnectionManager` |
| 方法参数注入 | 支持 | 支持（按类型从容器解析） |

由 `ConnectionRegistrar` 在所有单例就绪后扫描并注册。若方法返回裸 `DataSource`，
框架会自动用全局唯一的 `ContainerBootstrap` 包装。

### 默认连接会自动暴露为 Spring 的 DataSource Bean

连接改用注解声明后，业务工程**不需要**再手写 `@Bean DataSource`。
但 Spring 生态中大量组件仍依赖容器里存在 `DataSource` 类型的 Bean：

- `DataSourceTransactionManager`（`@Transactional` 事务管理器）
- `JdbcTemplate`
- 第三方 starter 的 `@ConditionalOnBean(DataSource.class)`

因此 `DatabaseAutoConfiguration` 会把**默认连接**以 `JaravelDataSource` 的形式
注册为 `@Primary` 的 Spring Bean：

```java
@Bean
@Primary
@ConditionalOnMissingBean(javax.sql.DataSource.class)
public JaravelDataSource jaravelDataSource() {
    return new JaravelDataSource();
}
```

**默认连接的确定规则**：

1. 标记了 `@RegisterConnection(value = "x", defaultConnection = true)` 的连接；
2. **若一个都没标记，则第一个注册的连接自动成为默认连接。**

**为什么是惰性的**：`JaravelDataSource` 只是一个委托，构造时不解析任何东西，
直到真正 `getConnection()` 才去 `ConnectionManager` 取默认连接。
这样就避免了"`@RegisterConnection` 尚未扫描完成 ⇄ 事务管理器已需要 DataSource"的先后顺序死结。

**向后兼容**：若业务工程自己定义了 `DataSource` Bean（历史写法），
`@ConditionalOnMissingBean` 会让框架的 Bean 自动让位。

### 别名解析顺序：先注册表，后 Spring

Model 上 `@DataSource("别名")` 填写的是**连接别名**，不是 Spring bean name。解析顺序为：

1. `ConnectionManager` 注册表（`@RegisterConnection` 声明的别名）；
2. 回退 Spring 容器中同名的 `GaarasonDataSource` bean；
3. 回退 Spring 容器中同名的 `javax.sql.DataSource` bean（自动用全局 Container 包装）；
4. 均未命中 → 抛出异常并列出全部可用别名。

因此历史的 `@Bean` 写法依然兼容，迁移无破坏性。迁移脚本（`Migration#connection()`）
使用完全相同的一套别名语义。

---

## 完整使用示例

### 1. 定义 Model

```java
import com.weacsoft.jaravel.vendor.database.BaseModel;
import com.weacsoft.jaravel.vendor.database.DataSource;
import com.weacsoft.jaravel.vendor.auth.contract.Authenticatable;
import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import gaarason.database.query.QueryBuilder;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@jakarta.persistence.Table(name = "users")
public class User extends BaseModel<User, Long> implements Authenticatable {

    @Primary
    @Column
    private Long id;

    @Column
    private String name;

    @Column
    private String number;

    @Column
    private String password;

    @Column
    private LocalDateTime deletedAt;   // 软删除列（配合 BaseModel 软删除作用域）

    // ---- 静态查询方法（委托给 BaseModel） ----
    public static User find(Long id) {
        return BaseModel.find(User.class, id);
    }

    public static List<User> all() {
        return BaseModel.all(User.class);
    }

    public static QueryBuilder<User, Long> query() {
        return BaseModel.query(User.class);
    }

    // ---- Authenticatable 实现 ----
    @Override
    public Object getAuthIdentifier() {
        return id;
    }

    // getter/setter ...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
```

### 2. 多数据源 Model

```java
@Repository
@DataSource("secondary")
@jakarta.persistence.Table(name = "products")
public class Product extends BaseModel<Product, Long> {

    @Primary @Column private Long id;
    @Column private String name;
    @Column private Double price;

    public static Product find(Long id) {
        return BaseModel.find(Product.class, id);
    }

    public static List<Product> all() {
        return BaseModel.all(Product.class);
    }

    public static QueryBuilder<Product, Long> query() {
        return BaseModel.query(Product.class);
    }

    // getter/setter ...
}
```

### 3. CRUD 操作

```java
// 新增（主键为 null 时执行 INSERT）
User user = new User();
user.setName("alice");
user.setNumber("1001");
user.setPassword(passwordEncoder.encode("secret"));
User saved = user.save();  // 主键为 null → INSERT

// 更新（主键已设置时执行 UPDATE）
User found = User.find(1L);
found.setName("alice_updated");
User updated = found.save();  // 主键已设置 → UPDATE

// 查询
User found = User.find(1L);
List<User> all = User.all();

// 条件查询
User alice = User.query()
        .where("name", "alice")
        .first()
        .toObject();

// 排序查询（字符串方向）
List<User> activeUsers = User.query()
        .where("status", 1)
        .orderBy("created_at", "desc")
        .get()
        .toObjectList();

// 排序查询（类型安全枚举 OrderBy.DESC）
import gaarason.database.appointment.OrderBy;

List<User> recentUsers = User.query()
        .where("status", 1)
        .orderBy("created_at", OrderBy.DESC)
        .get()
        .toObjectList();

// 删除（通过查询构造器）
int deletedCount = User.query()
        .where("id", 1L)
        .delete();   // 返回受影响行数（软删除时为设置 deleted_at 的行数）

// 软删除操作（基于 deleted_at 列，对齐 Laravel SoftDeletes）
User.query().where("id", 1L).delete();                                 // 软删除（设置 deleted_at）
List<User> trashed = User.query().onlyTrashed().get().toObjectList();   // 仅查已软删除
List<User> allIncTrashed = User.query().withTrashed().get().toObjectList(); // 含已软删除
User.query().onlyTrashed().where("id", 1L).restore();                  // 恢复（deleted_at 置 NULL）

// 复制
User copy = found.replicate();
copy.setName("alice_copy");
copy.save();  // 作为新记录保存
```

### 4. 认证集成

```java
@Configuration
public class AuthConfig {

    @Bean
    public UserProvider userProvider(UserModel userModel) {
        // credentialField 为 "number"，retrieveByCredentials 按此字段查询
        return new EloquentUserProvider(userModel, "number");
    }

    @Bean
    public ApplicationRunner authRegistrar(AuthManager authManager, UserProvider userProvider) {
        return args -> {
            authManager.registerProvider("users", userProvider);
            authManager.registerGuard("web", "session", "users");
        };
    }
}
```

```java
@PostMapping("/login")
public Response login(@RequestBody LoginRequest req, UserProvider provider) {
    // 1. 按凭证查出用户
    User user = (User) provider.retrieveByCredentials(Map.of("number", req.getNumber()));
    // 2. 应用层校验密码
    if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
        return ResponseBuilder.error(401, "工号或密码错误");
    }
    // 3. 登入
    Auth.login(user);
    return ResponseBuilder.success("登录成功");
}
```

---

## 线程安全说明

### 1. BaseModel 与 Spring 单例

业务 Model 标注 `@Repository` 后为 Spring 单例。`gaarasonDataSource` 字段由容器注入（`@Lazy` 懒加载），构造后不再变更。

- **数据库操作经由单例完成**：`new User()` 创建的普通实例调用 `save()` 时，通过 `SpringContext.bean(User.class)` 取回 Spring 单例执行，单例上的数据源字段已由容器注入。
- **`getGaarasonDataSource()` 无状态**：仅读取注解与注入字段，无副作用，线程安全。

### 2. @DataSource 注解读取

`getGaarasonDataSource()` 通过 `this.getClass().getAnnotation(DataSource.class)` 读取注解，注解元数据为 JVM 级不可变，多线程读取安全。

### 3. EloquentUserProvider

`EloquentUserProvider` 持有 `model`（Spring 单例）与 `credentialField`（不可变 String），均为构造后不可变字段。`retrieveById` 与 `retrieveByCredentials` 委托给 gaarason 的 `Model.find` / `QueryBuilder`，后者自身保证线程安全。

### 4. gaarason QueryBuilder

gaarason 的 `QueryBuilder` 为每次查询创建新实例（非共享），线程安全。

### 线程安全总结

| 组件 | 类型 | 线程安全机制 |
|---|---|---|
| `BaseModel` 子类（Spring 单例） | 单例 | `gaarasonDataSource` 注入后不变；`getGaarasonDataSource()` 无状态读取 |
| `@DataSource` 注解 | 元数据 | JVM 级不可变，多线程读取安全 |
| `EloquentUserProvider` | 无状态 | 不可变字段 + 委托 gaarason 线程安全组件 |
| `QueryBuilder` | 每次查询新建 | 实例不共享，天然线程安全 |
| `GaarasonDataSource` | 单例 | Druid 数据源自身线程安全 |

> **注意**：`BaseModel` 的实例方法（`save()`、`replicate()`）可在 `new` 创建的普通实例上调用，这些实例为线程局部对象，不应跨线程共享。所有实际的数据库操作均委托给 Spring 单例完成，单例本身线程安全。

---

## 已知问题与兼容说明

### SQLite 分页 `count()` 强转异常（已内置兼容）

**现象**：使用 SQLite（或其他将 `COUNT()` 以 `Integer` 返回的 JDBC 驱动）时，调用模型的 `paginate()` 触发如下异常：

```
java.lang.ClassCastException: class java.lang.Integer cannot be cast to class java.lang.Long
	at gaarason.database.contract.builder.Aggregates.count(Aggregates.java:36)
	at gaarason.database.query.ExecuteLevel3Builder.paginate(ExecuteLevel3Builder.java:139)
```

**根因**：gaarason 的 `Aggregates.count()` 内部通过 `ObjectUtils.typeCast` 把聚合结果直接强转为 `Long`。SQLite JDBC 驱动对 `COUNT()` 返回 `Integer`，强转失败。`count()` 的声明返回类型为 `Long`，但底层结果类型取决于数据库驱动。

**处理**：jaravel 的 `BaseModel.newQuery()` 已返回 `JaravelQueryBuilder`（对 gaarason `QueryBuilder` 的安全包装）。它在 `count()` 中捕获 `ClassCastException` 并回退到数值兼容的结果转换（`Number.longValue()` 兜底），对业务代码完全透明——`list.paginate(page, size)` 无需任何改动即可在 SQLite 下正常工作。

> 该兼容为临时兜底方案。彻底的修复应在 gaarason `database-all` 的 `AbstractBuilder.aggregate` 中对聚合结果统一做 `Number` 兼容转换（而非裸强转），届时 jaravel 可移除该包装。

---

## 分页标准层与模块解耦

`BaseModel.paginate(page, size)` 及其重载返回的是 **`core.pagination.Paginator`**（位于 `core` 标准层），
而非 `jblade` 的实现。这意味着：

- **`database` 不再硬依赖 `jblade`**：仅使用 ORM 与分页、不想引入模板引擎的项目可单独依赖 `database`。
- 分页器提供 Laravel 风格 API：`hasPages()` / `onFirstPage()` / `hasMorePages()` / `previousPageUrl()` /
  `nextPageUrl()` / `url(n)` / `appends()` / `elements()` / `links()`，并实现 `Iterable`
  （模板中可直接 `@foreach($list as $item)`）与 `Htmlable`（实现后 `{{ $list }}` 免转义输出 HTML）。
- **`links(viewName)` 的渲染解耦**：`Paginator` 仅依赖 `core.view.View`（标准接口）。视图引擎（jblade）
  在启动时通过 `ViewFacade.bind()` 注入默认 `View`；若未引入 jblade（无默认视图），
  `links()` 安全降级为空串——即「没有分页视图时该方法等同于未执行」。

```java
// 返回 core.pagination.Paginator，不耦合模板引擎
Paginator<User> list = User.self().paginate(page, 15).setPath("/users");
// 模板中：
//   @foreach($list as $u) ... @endforeach
//   {{ $list->links('layouts.mdui.pageinator') }}
```

> 历史上 `Paginator` / `Htmlable` / `HtmlString` / `View` 曾位于 `jblade` 包内；现已统一上提到
> `core` 标准层，`jblade` 仅作为 `core.view.View` 的实现方（`BladeView` 实现 `core.view.View`）。

