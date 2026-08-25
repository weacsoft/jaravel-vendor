package com.weacsoft.jaravel.vendor.database;

import com.weacsoft.jaravel.vendor.core.lookup.GlobalLookup;

import com.weacsoft.jaravel.vendor.core.SpringContext;
import gaarason.database.contract.connection.GaarasonDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JaravelDataSource} 惰性委托测试。
 *
 * <p>它是「{@code @RegisterConnection} 声明的默认连接」暴露给 Spring 的桥梁，
 * 使 {@code DataSourceTransactionManager}、{@code JdbcTemplate} 等无需业务方手写
 * {@code @Bean DataSource} 也能正常工作。
 */
class JaravelDataSourceTest {

    @BeforeEach
    void setUp() {
        ConnectionManager.clear();
        GlobalLookup.uninstall();
    }

    @AfterEach
    void tearDown() {
        ConnectionManager.clear();
        GlobalLookup.uninstall();
    }

    @Test
    @DisplayName("Bean 可以在没有任何连接时创建——不阻断启动")
    void canBeConstructedWithoutAnyConnection() {
        assertNotNull(new JaravelDataSource(), "构造期不得触发连接解析");
    }

    @Test
    @DisplayName("真正取连接时才解析；无连接则抛出可操作的提示")
    void throwsHelpfulErrorWhenNoConnection() {
        JaravelDataSource ds = new JaravelDataSource();
        SQLException ex = assertThrows(SQLException.class, ds::getConnection);
        assertTrue(ex.getMessage().contains("@RegisterConnection"),
                "错误信息应提示如何注册连接，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("委托到 ConnectionManager 的默认连接")
    void delegatesToDefaultConnection() throws SQLException {
        DataSource raw = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(raw.getConnection()).thenReturn(conn);

        ConnectionManager.addConnection("sqlite", mock(GaarasonDataSource.class), raw);

        JaravelDataSource ds = new JaravelDataSource();
        assertSame(conn, ds.getConnection(), "应委托到默认连接的原始数据源");
    }

    @Test
    @DisplayName("连接在 Bean 创建之后才注册也能正常工作（惰性的意义）")
    void resolvesConnectionRegisteredAfterConstruction() throws SQLException {
        JaravelDataSource ds = new JaravelDataSource();

        DataSource raw = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(raw.getConnection()).thenReturn(conn);
        ConnectionManager.addConnection("late", mock(GaarasonDataSource.class), raw);

        assertSame(conn, ds.getConnection());
    }

    @Test
    @DisplayName("unwrap 支持自身类型")
    void unwrapSelf() throws SQLException {
        JaravelDataSource ds = new JaravelDataSource();
        assertSame(ds, ds.unwrap(JaravelDataSource.class));
        assertTrue(ds.isWrapperFor(DataSource.class));
    }
}
