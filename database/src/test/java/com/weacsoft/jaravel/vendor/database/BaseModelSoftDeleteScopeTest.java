package com.weacsoft.jaravel.vendor.database;

import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import gaarason.database.annotation.Table;
import gaarason.database.bootstrap.ContainerBootstrap;
import gaarason.database.connection.GaarasonDataSourceBuilder;
import gaarason.database.contract.connection.GaarasonDataSource;
import gaarason.database.contract.eloquent.Record;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 软删除<b>作用域过滤</b>的生效性测试（真实数据库，非 mock）。
 * <p>
 * <b>为什么必须有这个测试</b>：{@code BaseModel} 之前覆盖了 {@code scopeSoftDelete} 等
 * 一系列方法，看起来「软删除已经做好了」，但 {@code newQuery()} 里真正决定是否调用
 * {@code scopeSoftDelete} 的是 {@code softDeleting()}，而它没被覆盖、默认返回 {@code false}。
 * 结果是：<b>所有覆盖都成了死代码</b>，{@code newQuery()} 查出来含全部已删除数据，
 * 且不报任何错。这类失效只能靠「造一条软删除记录，看默认查询是否还能查到」才发现。
 * <p>
 * 用 H2 内存库跑真实 SQL：只有真库才能证明 {@code whereNull(deleted_at)} 真的拼进了语句。
 */
class BaseModelSoftDeleteScopeTest {

    private static GaarasonDataSource dataSource;

    /** 测试模型：列名与 jaravel 约定一致（deleted_at 时间戳）。 */
    @Table(name = "scope_user")
    public static class ScopeUser extends BaseModel<ScopeUser, Long> {
        @Primary
        @Column(name = "id")
        private Long id;

        @Column(name = "username")
        private String username;

        @Column(name = "deleted_at")
        private String deletedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getDeletedAt() { return deletedAt; }
        public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
    }

    @BeforeAll
    static void initDataSource() {
        JdbcDataSource raw = new JdbcDataSource();
        raw.setURL("jdbc:h2:mem:scopedb;MODE=MySQL;DB_CLOSE_DELAY=-1");
        raw.setUser("sa");
        raw.setPassword("");

        // gaarason 7.1.x 通过 ReflectionScan 发现 autoconfiguration（含 QueryBuilderConfig 注册），
        // 依赖 scan.packages；不设会因缺 QueryBuilderConfig 抛 ObjectNewInstanceException。
        System.setProperty("gaarason.database.scan.packages",
                BaseModelSoftDeleteScopeTest.class.getPackageName());

        ContainerBootstrap bootstrap = ContainerBootstrap.build();
        bootstrap.defaultRegister();
        bootstrap.bootstrapGaarasonAutoconfiguration();
        bootstrap.initialization();
        dataSource = GaarasonDataSourceBuilder.build(raw, bootstrap);
        ConnectionManager.setContainer(bootstrap);
    }

    @AfterAll
    static void tearDown() {
        ConnectionManager.clear();
    }

    @BeforeEach
    void resetSchema() {
        ScopeUser model = model();
        // H2 在 MODE=MySQL 下仍会把「未加引号」的标识符转大写，而 gaarason 拼 SQL 时
        // 用的是小写带引号的 "scope_user"。所以建表也用带引号的小写，否则查询报
        // Table "scope_user" not found (candidates are: "SCOPE_USER")。
        model.newQueryWithoutApply().execute("DROP TABLE IF EXISTS \"scope_user\"");
        model.newQueryWithoutApply().execute("CREATE TABLE \"scope_user\" ("
                + "\"id\" BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "\"username\" VARCHAR(64), "
                + "\"deleted_at\" VARCHAR(32))");
    }

    /**
     * 绑定测试数据源的模型实例（不走 Spring 单例，避免测试依赖容器）。
     * <p>
     * 必须同时覆盖两个方法：
     *   · {@code getGaarasonDataSource()} —— 给出连接；
     *   · {@code getSelf()} —— BaseModel 默认实现是 {@code SpringContext.bean(getClass())}，
     *     在无 Spring 的单测里会抛「SpringContext 尚未初始化」。这里返回 this 即可。
     * <p>
     * 注意匿名子类会让 {@code getClass()} 变成 {@code ScopeUser$1}，所以 getSelf 不能
     * 依赖 getClass() 去容器里找 —— 直接 return this 最稳。
     */
    private static final ScopeUser MODEL = new ScopeUser() {
        @Override
        public GaarasonDataSource getGaarasonDataSource() {
            return dataSource;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ScopeUser getSelf() {
            return this;
        }
    };

    private static ScopeUser model() {
        return MODEL;
    }

    /**
     * 造一条记录并把它标记为已软删除，返回主键。
     *
     * @param username 用户名
     * @return 主键
     */
    private Long createSoftDeleted(String username) {
        ScopeUser model = model();
        // 用 newQueryWithoutApply 绕过软删除作用域写入（建表/造数据都用它）
        Long id = model.newQueryWithoutApply().data("username", username).insertGetId();
        model.newQueryWithoutApply()
                .where("id", id)
                .data("deleted_at", "2026-01-01 00:00:00")
                .update();
        return id;
    }

    // ==================== 核心：默认作用域必须排除已删除 ====================

    @Test
    @DisplayName("softDeleting() 必须返回 true —— 否则一切软删除钩子都是死代码")
    void softDeletingIsEnabled() {
        ScopeUser model = model();
        assertThat(model.softDeleting())
                .as("softDeleting() 是 newQuery() 附加 deleted_at IS NULL 的总开关，必须为 true")
                .isTrue();
        assertThat(model.isSoftDeleting())
                .as("public 的 isSoftDeleting() 应与它同源")
                .isTrue();
    }

    @Test
    @DisplayName("默认 newQuery() 必须查不到已软删除记录（这是作用域真正生效的证明）")
    void defaultQueryExcludesSoftDeleted() {
        Long aliveId = createAlive("活着");
        Long deletedId = createSoftDeleted("已删除");

        // 前置：两条都在库里。
        // 注意不能用 newQueryWithoutApply() 做这个前置判断 —— 反编译 ModelOfQuery 可见
        // newQueryWithoutApply() 同样执行 `if (softDeleting()) scopeSoftDelete(...)`，
        // 它「不应用」的是 apply() 钩子，不是软删除作用域。要用 withTrashed() 才是真的不过滤。
        assertThat(model().withTrashed().where("id", deletedId).first())
                .as("前置条件：已删除记录确实存在库里").isNotNull();

        // 核心断言：默认作用域只能查到活着的那条
        Record<ScopeUser, Long> alive = model().newQuery().where("id", aliveId).first();
        assertThat(alive).as("未删除记录应能查到").isNotNull();

        Record<ScopeUser, Long> deleted = model().newQuery().where("id", deletedId).first();
        assertThat(deleted)
                .as("已软删除记录不应出现在默认查询里；为 null 才说明 whereNull(deleted_at) 真的生效了")
                .isNull();
    }

    @Test
    @DisplayName("withTrashed() 能查到已删除记录（作用域可临时关闭）")
    void withTrashedSeesDeleted() {
        Long deletedId = createSoftDeleted("已删除");

        Record<ScopeUser, Long> found = model().withTrashed().where("id", deletedId).first();
        assertThat(found).as("withTrashed 应能查到已软删除记录").isNotNull();
        assertThat(found.toObject().getDeletedAt()).as("deleted_at 应仍有值").isNotNull();
    }

    @Test
    @DisplayName("onlyTrashed() 只查到已删除记录，查不到正常记录")
    void onlyTrashedSeesOnlyDeleted() {
        Long aliveId = createAlive("活着");
        Long deletedId = createSoftDeleted("已删除");

        assertThat(model().onlyTrashed().where("id", deletedId).first())
                .as("onlyTrashed 应能查到已删除记录").isNotNull();
        assertThat(model().onlyTrashed().where("id", aliveId).first())
                .as("onlyTrashed 不应查到正常记录").isNull();
    }

    @Test
    @DisplayName("list 级别验证：默认查询的条数不含已删除，withTrashed 含全部")
    void listCountsReflectScope() {
        createAlive("a1");
        createAlive("a2");
        createSoftDeleted("d1");

        assertThat(model().newQuery().get().toObjectList())
                .as("默认查询应只返回 2 条正常记录").hasSize(2);
        assertThat(model().withTrashed().get().toObjectList())
                .as("withTrashed 应返回全部 3 条").hasSize(3);
    }

    /** 造一条正常（未删除）记录，返回主键。 */
    private Long createAlive(String username) {
        return model().newQueryWithoutApply().data("username", username).insertGetId();
    }
}
