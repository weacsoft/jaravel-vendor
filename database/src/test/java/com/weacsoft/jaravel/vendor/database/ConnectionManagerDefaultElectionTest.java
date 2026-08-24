package com.weacsoft.jaravel.vendor.database;

import com.weacsoft.jaravel.vendor.core.SpringContext;
import gaarason.database.contract.connection.GaarasonDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link ConnectionManager} 默认连接推选与原始数据源暴露的单元测试。
 *
 * <p>覆盖需求：
 * <ul>
 *   <li>标记 {@code defaultConnection = true} 的连接成为默认连接；</li>
 *   <li><b>没有任何连接标记默认时，第一个注册的连接自动成为默认连接</b>；</li>
 *   <li>默认连接的原始 {@link DataSource} 可被取出，供事务管理器 / JdbcTemplate 使用。</li>
 * </ul>
 */
class ConnectionManagerDefaultElectionTest {

    @BeforeEach
    void setUp() {
        ConnectionManager.clear();
        new SpringContext().setApplicationContext(null);
    }

    @AfterEach
    void tearDown() {
        ConnectionManager.clear();
        new SpringContext().setApplicationContext(null);
    }

    @Test
    @DisplayName("无连接时，默认原始数据源为 null 且 hasAnyConnection 为 false")
    void noConnectionAtAll() {
        assertFalse(ConnectionManager.hasAnyConnection());
        assertNull(ConnectionManager.defaultRawDataSource());
    }

    @Test
    @DisplayName("没有任何连接标记默认时，第一个注册的连接自动成为默认")
    void firstRegisteredBecomesDefault() {
        DataSource rawA = mock(DataSource.class);
        DataSource rawB = mock(DataSource.class);

        ConnectionManager.addConnection("reporting", mock(GaarasonDataSource.class), rawA);
        ConnectionManager.addConnection("archive", mock(GaarasonDataSource.class), rawB);

        assertTrue(ConnectionManager.hasAnyConnection());
        assertEquals("reporting", ConnectionManager.defaultConnectionName(),
                "第一个注册的连接应自动成为默认连接");
        assertSame(rawA, ConnectionManager.defaultRawDataSource());
    }

    @Test
    @DisplayName("显式标记默认的连接优先于「第一个注册」规则")
    void explicitDefaultWins() {
        DataSource rawA = mock(DataSource.class);
        DataSource rawB = mock(DataSource.class);

        ConnectionManager.addConnection("reporting", mock(GaarasonDataSource.class), rawA);
        ConnectionManager.addConnection("main", mock(GaarasonDataSource.class), rawB);
        ConnectionManager.setDefaultConnection("main");

        assertEquals("main", ConnectionManager.defaultConnectionName());
        assertSame(rawB, ConnectionManager.defaultRawDataSource());
    }

    @Test
    @DisplayName("显式默认声明后，后续注册的连接不会抢占默认位置")
    void laterRegistrationDoesNotOverrideExplicitDefault() {
        DataSource rawMain = mock(DataSource.class);
        ConnectionManager.setDefaultConnection("main");
        ConnectionManager.addConnection("main", mock(GaarasonDataSource.class), rawMain);
        ConnectionManager.addConnection("archive", mock(GaarasonDataSource.class), mock(DataSource.class));

        assertEquals("main", ConnectionManager.defaultConnectionName());
        assertSame(rawMain, ConnectionManager.defaultRawDataSource());
    }

    @Test
    @DisplayName("按别名取原始数据源")
    void rawDataSourceByAlias() {
        DataSource rawA = mock(DataSource.class);
        DataSource rawB = mock(DataSource.class);
        ConnectionManager.addConnection("main", mock(GaarasonDataSource.class), rawA);
        ConnectionManager.addConnection("archive", mock(GaarasonDataSource.class), rawB);

        assertSame(rawA, ConnectionManager.rawDataSource("main"));
        assertSame(rawB, ConnectionManager.rawDataSource("archive"));
        assertSame(rawA, ConnectionManager.rawDataSource(null), "传 null 表示默认连接");
    }

    @Test
    @DisplayName("clear 后默认标记也一并复位")
    void clearResetsExplicitFlag() {
        ConnectionManager.setDefaultConnection("main");
        ConnectionManager.clear();

        ConnectionManager.addConnection("first", mock(GaarasonDataSource.class), mock(DataSource.class));
        assertEquals("first", ConnectionManager.defaultConnectionName(),
                "clear 后应恢复「第一个注册即默认」的行为");
    }
}
