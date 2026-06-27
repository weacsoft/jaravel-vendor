package com.weacsoft.jaravel.vendor.database;

import com.weacsoft.jaravel.vendor.core.SpringContext;
import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import gaarason.database.contract.connection.GaarasonDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * {@link BaseModel#getGaarasonDataSource()} 多数据源解析逻辑单元测试。
 * <p>
 * 覆盖三条解析路径：1) {@code @DataSource} 注解指定数据源 Bean；2) Spring 注入字段；
 * 3) 容器默认（@Primary）数据源回退。
 */
class BaseModelDataSourceResolutionTest {

    /** 标注 @DataSource("secondary") 的模型 */
    @DataSource("secondary")
    public static class SecondaryModel extends BaseModel<SecondaryModel, Long> {
        @Primary
        @Column(name = "id")
        private Long id;
    }

    /** 未标注 @DataSource 的模型，使用默认数据源 */
    public static class PlainModel extends BaseModel<PlainModel, Long> {
        @Primary
        @Column(name = "id")
        private Long id;
    }

    @AfterEach
    void tearDown() {
        // 复位静态上下文，避免影响其它测试
        new SpringContext().setApplicationContext(null);
    }

    private void useContext(Object... nameBeanPairs) {
        GenericApplicationContext ctx = new GenericApplicationContext();
        for (int i = 0; i < nameBeanPairs.length; i += 2) {
            ctx.getBeanFactory().registerSingleton((String) nameBeanPairs[i], nameBeanPairs[i + 1]);
        }
        ctx.refresh();
        new SpringContext().setApplicationContext(ctx);
    }

    @Test
    void annotationResolvesNamedDataSourceBean() {
        GaarasonDataSource secondary = mock(GaarasonDataSource.class);
        useContext("secondary", secondary);

        SecondaryModel model = new SecondaryModel();
        // @DataSource("secondary") -> SpringContext.bean("secondary", GaarasonDataSource.class)
        assertSame(secondary, model.getGaarasonDataSource());
    }

    @Test
    void injectedFieldTakesPrecedenceWhenNoAnnotation() throws Exception {
        GaarasonDataSource def = mock(GaarasonDataSource.class);
        useContext("default", def);

        PlainModel model = new PlainModel();
        GaarasonDataSource injected = mock(GaarasonDataSource.class);
        // 通过反射模拟 Spring 注入的 gaarasonDataSource 字段
        Field f = BaseModel.class.getDeclaredField("gaarasonDataSource");
        f.setAccessible(true);
        f.set(model, injected);

        // 未标注 @DataSource 且字段已注入 -> 返回注入字段
        assertSame(injected, model.getGaarasonDataSource());
    }

    @Test
    void fallsBackToContainerDefaultDataSource() {
        GaarasonDataSource def = mock(GaarasonDataSource.class);
        // 仅注册一个 GaarasonDataSource Bean，作为默认（@Primary）数据源
        useContext("default", def);

        PlainModel model = new PlainModel();
        // 无注解、字段未注入 -> 回退 SpringContext.bean(GaarasonDataSource.class)
        assertSame(def, model.getGaarasonDataSource());
    }
}
