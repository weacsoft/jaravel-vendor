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

- **BaseModel（合并模型）**：对齐 Laravel Eloquent 的「单一类同时承担实体定义与查询职责」设计。业务 Model 继承 `BaseModel` 后，无需再拆分「User 实体 POJO」与「UserModel 查询类」，一个类即可完成实体定义、增删改查、查询构造。
- **@DataSource 注解（多数据源）**：对齐 Laravel Model 的 `$connection` 属性，通过注解指定 Model 使用的数据源 Bean 名称，支持多数据库。
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

> 注意：本模块使用 `gaarason/database-query`（不含 SpringBoot 自动配置），需手动 `@Bean` 创建 `GaarasonDataSource`。

---

## 类总览

```
com.weacsoft.jaravel.vendor.database
├── BaseModel              # Eloquent Model 基类（合并实体 + 查询）
├── DataSource             # @DataSource 注解（多数据源支持）
└── EloquentUserProvider   # 基于 Eloquent 的用户提供者（认证集成）
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
  ├── 1. 检查 @DataSource 注解
  │     ├── 存在 → SpringContext.bean(annotation.value(), GaarasonDataSource.class)
  │     └── 不存在 → 继续
  ├── 2. 检查 Spring 注入的数据源
  │     ├── gaarasonDataSource != null → 返回注入的数据源（默认 @Primary）
  │     └── 为 null → 继续
  └── 3. 回退：从容器获取默认数据源
        └── SpringContext.bean(GaarasonDataSource.class)
```

> **关键**：必须先检查 `@DataSource` 注解，否则 `@Autowired` 总是注入 `@Primary` 数据源。

### 实例方法

#### save()

持久化当前实体（新增），对齐 Laravel Eloquent 的 `$model->save()`。

```java
/**
 * 当前实例由 new 创建（非 Spring Bean），通过 SpringContext 取出本类的
 * Spring 单例执行 create(this)，返回保存后的实体（含生成的主键）。
 *
 * @return 保存后的实体；无记录时返回 null
 */
public T save()
```

示例：

```java
User user = new User();
user.setName("alice");
user.setNumber("1001");
User saved = user.save();  // 持久化，返回含主键的实体
System.out.println(saved.getId());  // 生成的主键
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
// 新增
User user = new User();
user.setName("alice");
user.save();                       // 持久化（新增），返回保存后的实体

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

// 复制（不含主键）
User clone = user.replicate();
clone.save();                      // 作为新记录保存
```

### 实现要点

`new User()` 创建的是普通实例（非 Spring Bean），调用 `save()` 等实例方法或静态查询时，统一通过 `SpringContext.bean(Class)` 取回本类的 Spring 单例来真正执行 gaarason 的查询/写入。Spring 单例上的 `gaarasonDataSource` 字段由容器注入，因此所有数据库操作均经由单例完成。

```
new User()  ──save()──→  SpringContext.bean(User.class)  ──→  gaarason create()
                              (Spring 单例，已注入数据源)
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

// 指定 secondaryDataSource 数据源
@DataSource("secondaryDataSource")
@Repository
@Table(name = "products")
public class Product extends BaseModel<Product, Long> {
    // ...
}

// 指定 logDataSource 数据源
@DataSource("logDataSource")
@Repository
@Table(name = "operation_logs")
public class OperationLog extends BaseModel<OperationLog, Long> {
    // ...
}
```

### 解析逻辑

`BaseModel.getGaarasonDataSource()` 优先检查 `@DataSource` 注解：

```java
@Override
public GaarasonDataSource getGaarasonDataSource() {
    // 1. 优先检查 @DataSource 注解
    DataSource dsAnnotation = this.getClass().getAnnotation(DataSource.class);
    if (dsAnnotation != null) {
        return SpringContext.bean(dsAnnotation.value(), GaarasonDataSource.class);
    }
    // 2. 未标注 @DataSource 时，使用 Spring 注入的数据源（默认为 @Primary）
    if (gaarasonDataSource != null) {
        return gaarasonDataSource;
    }
    // 3. 回退：从容器获取默认数据源
    return SpringContext.bean(GaarasonDataSource.class);
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

Database 模块本身不提供 `@ConfigurationProperties`，数据源需手动 `@Bean` 创建 `GaarasonDataSource`。

### 数据源配置

```yaml
spring:
  datasource:
    # 主数据源
    primary:
      url: jdbc:mysql://localhost:3306/main_db?useSSL=false&serverTimezone=UTC
      username: root
      password: secret
      driver-class-name: com.mysql.cj.jdbc.Driver
    # 次数据源
    secondary:
      url: jdbc:mysql://localhost:3306/secondary_db?useSSL=false&serverTimezone=UTC
      username: root
      password: secret
      driver-class-name: com.mysql.cj.jdbc.Driver
```

### 手动创建 GaarasonDataSource

```java
@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    public GaarasonDataSource primaryDataSource(
            @Value("${spring.datasource.primary.url}") String url,
            @Value("${spring.datasource.primary.username}") String username,
            @Value("${spring.datasource.primary.password}") String password,
            @Value("${spring.datasource.primary.driver-class-name}") String driver) {
        DruidDataSource druid = new DruidDataSource();
        druid.setUrl(url);
        druid.setUsername(username);
        druid.setPassword(password);
        druid.setDriverClassName(driver);
        return new GaarasonDataSource(druid);
    }

    @Bean("secondaryDataSource")
    public GaarasonDataSource secondaryDataSource(
            @Value("${spring.datasource.secondary.url}") String url,
            @Value("${spring.datasource.secondary.username}") String username,
            @Value("${spring.datasource.secondary.password}") String password,
            @Value("${spring.datasource.secondary.driver-class-name}") String driver) {
        DruidDataSource druid = new DruidDataSource();
        druid.setUrl(url);
        druid.setUsername(username);
        druid.setPassword(password);
        druid.setDriverClassName(driver);
        return new GaarasonDataSource(druid);
    }
}
```

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
@DataSource("secondaryDataSource")
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
// 新增
User user = new User();
user.setName("alice");
user.setNumber("1001");
user.setPassword(passwordEncoder.encode("secret"));
User saved = user.save();

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
        .delete();   // 返回受影响行数

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
