package com.weacsoft.jaravel.vendor.database;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * 框架托管的默认 {@link DataSource}，是 {@link ConnectionManager} 默认连接的<b>惰性委托</b>。
 *
 * <h3>为什么需要它</h3>
 * 连接改用 {@link RegisterConnection @RegisterConnection} 声明后，业务工程不再需要
 * 手写 {@code @Bean DataSource}。但 Spring 生态中大量组件（{@code DataSourceTransactionManager}、
 * {@code JdbcTemplate}、以及第三方 starter 的 {@code @ConditionalOnBean(DataSource.class)}）
 * 依赖容器里存在一个 {@code DataSource} 类型的 Bean。
 * <p>
 * 因此框架自动把「默认连接」以本类的形式暴露为 Spring Bean：
 * <ul>
 *   <li>被标记 {@code defaultConnection = true} 的连接即默认连接；</li>
 *   <li>若没有任何连接标记默认，则<b>第一个注册的连接</b>自动成为默认连接。</li>
 * </ul>
 *
 * <h3>惰性的意义</h3>
 * 本 Bean 在容器早期即可创建，但直到真正调用 {@link #getConnection()} 等方法时，
 * 才会去 {@link ConnectionManager} 取真实数据源。这样就避免了
 * 「{@code @RegisterConnection} 尚未扫描完成 ⇄ 事务管理器已需要 DataSource」的先后顺序死结。
 *
 * @see ConnectionManager#defaultRawDataSource()
 */
public class JaravelDataSource implements DataSource {

    /**
     * 解析出当前默认连接底层的原始 {@link DataSource}。
     *
     * @return 原始数据源
     * @throws SQLException 尚无任何连接被注册时抛出
     */
    private DataSource delegate() throws SQLException {
        DataSource target = ConnectionManager.defaultRawDataSource();
        if (target == null) {
            throw new SQLException("尚未注册任何数据库连接。请使用 @RegisterConnection 声明至少一个连接，"
                    + "或执行 `artisan vendor:publish --tag=database` 生成默认配置。");
        }
        return target;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegate().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate().getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        try {
            return delegate().getParentLogger();
        } catch (SQLException e) {
            throw new SQLFeatureNotSupportedException(e.getMessage(), e);
        }
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate().isWrapperFor(iface);
    }

    @Override
    public String toString() {
        return "JaravelDataSource -> " + ConnectionManager.defaultConnectionName();
    }
}
