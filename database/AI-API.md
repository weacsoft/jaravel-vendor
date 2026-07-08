# database AI-API Reference

> Module: `database` | Package: `com.weacsoft.jaravel.vendor.database` | Version: 0.1.1

## Overview
database 模块提供 Laravel Eloquent 风格的 ORM 基础设施，基于 gaarason/database-all 构建。核心包含 BaseModel（Eloquent 模型基类，合并实体定义与查询职责于单一类）、@DataSource 注解（指定模型使用的数据源，对齐 Laravel `$connection`）和 EloquentUserProvider（基于 Eloquent 的用户提供者，对齐 Laravel EloquentUserProvider）。业务 Model 继承 BaseModel 并加 @Repository 即可像 Laravel 一样使用 find/all/query/save/replicate 等 API。

## Classes & Interfaces

### BaseModel
- **Type**: abstract class
- **Package**: `com.weacsoft.jaravel.vendor.database`
- **Description**: Eloquent 模型基类，对齐 Laravel `Illuminate\Database\Eloquent\Model`。业务 Model 继承本类并加 `@Repository`，泛型为「实体类型, 主键类型」，即可用单一类同时承担实体定义与查询职责，无需拆分实体 POJO 与查询类。数据源通过 `@Autowired @Lazy` 由 Spring 容器注入，支持多数据源（通过 @DataSource 注解指定）。
- **Extends**: `gaarason.database.eloquent.Model<QueryBuilder<T, K>, T, K>`
- **Annotations**: `@JsonAutoDetect(fieldVisibility = ANY, getterVisibility = NONE, ...)`

#### Instance Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `save` | 无 | `T` | 持久化当前实体（新增），返回保存后的实体（含生成的主键） |
| `replicate` | 无 | `T` | 复制当前实体（排除主键），可作为新记录再次 save() |
| `getGaarasonDataSource` | 无 | `GaarasonDataSource` | 获取数据源（优先检查 @DataSource 注解，否则使用注入的默认数据源） |

#### Static Methods (供业务 Model 静态方法委托)

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `find` | `Class<M> modelClass, K id` | `T` | 按主键查询实体，未找到返回 null |
| `all` | `Class<M> modelClass` | `List<T>` | 查询全部记录 |
| `query` | `Class<M> modelClass` | `QueryBuilder<T, K>` | 构造查询构造器 |

#### Usage Example
```java
@Repository
@Table(name = "users")
public class User extends BaseModel<User, Long> implements Authenticatable {
    @Primary @Column private Long id;
    @Column private String name;
    @Column private String email;
    @Column private String number;
    @Column private String password;

    // getter/setter ...

    // 静态查询方法（委托给 BaseModel，Java 静态方法不可继承故需手动声明）
    public static User find(Long id) { return BaseModel.find(User.class, id); }
    public static List<User> all() { return BaseModel.all(User.class); }
    public static QueryBuilder<User, Long> query() { return BaseModel.query(User.class); }

    // Authenticatable 实现
    @Override
    public Object getAuthIdentifier() { return id; }
}

// 使用方式
User user = new User();
user.setName("alice");
user.setEmail("alice@example.com");
User saved = user.save();              // 持久化，返回含主键的实体

User found = User.find(1L);            // 按主键查
List<User> all = User.all();           // 全部
User u = User.query()
    .where("name", "alice")
    .first()
    .toObject();                       // 条件查询
User clone = user.replicate();         // 复制（不含主键）
clone.setName("alice_copy");
clone.save();                          // 作为新记录保存
```

---

### @DataSource
- **Type**: annotation
- **Package**: `com.weacsoft.jaravel.vendor.database`
- **Description**: 指定 Model 使用的数据源 Bean 名称，对齐 Laravel Model 的 `$connection` 属性。标注在 Model 类上，未标注时使用默认（Primary）数据源。
- **Target**: `ElementType.TYPE`
- **Retention**: `RetentionPolicy.RUNTIME`

#### Elements

| Element | Type | Description |
|---------|------|-------------|
| `value` | `String` | 数据源 Bean 名称 |

#### Usage Example
```java
@DataSource("secondaryDataSource")
@Table(name = "products")
@Repository
public class Product extends BaseModel<Product, Long> {
    @Primary @Column private Long id;
    @Column private String name;
    @Column private BigDecimal price;
    // getter/setter ...

    public static Product find(Long id) { return BaseModel.find(Product.class, id); }
    public static List<Product> all() { return BaseModel.all(Product.class); }
    public static QueryBuilder<Product, Long> query() { return BaseModel.query(Product.class); }
}

// Product 使用 secondaryDataSource，User 使用默认数据源
Product p = Product.find(1L);
User u = User.find(1L);
```

---

### EloquentUserProvider
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.database`
- **Description**: 基于 gaarason/database-all 的用户提供者，对齐 Laravel `EloquentUserProvider`。仅负责通过 Eloquent Model 按主键/凭证取出用户，不负责校验密码（密码校验是应用层的责任）。
- **Implements**: `com.weacsoft.jaravel.vendor.auth.contract.UserProvider`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `EloquentUserProvider` | `Model<?, T, K> model, String credentialField` | 构造方法 | 创建用户提供者，指定 Model 和凭证字段名 |
| `retrieveById` | `Object identifier` | `Authenticatable` | 按主键取出用户，未找到返回 null |
| `retrieveByCredentials` | `Map<String, Object> credentials` | `Authenticatable` | 按凭证字段取出用户，未找到返回 null |

#### Usage Example
```java
// 应用启动时注册
@Autowired private UserModel userModel; // Spring 单例

UserProvider provider = new EloquentUserProvider<>(userModel, "number");
authManager.registerProvider("users", provider);

// 认证流程（Controller/Service 中）
Authenticatable user = provider.retrieveByCredentials(Map.of("number", "1001"));
// 应用层自行校验密码
if (user == null || !passwordEncoder.matches(inputPassword, user.getPassword())) {
    throw new RuntimeException("工号或密码错误");
}
// 登入
Auth.login(user);
```
