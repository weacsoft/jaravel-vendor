package com.weacsoft.jaravel.vendor.springboot.database;

import com.weacsoft.jaravel.vendor.database.BaseModel;
import gaarason.database.contract.connection.GaarasonDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 为所有 {@link BaseModel} Bean 绑定 {@code GaarasonDataSource}（D2 承接类）。
 * <p>
 * D2 前 {@code BaseModel} 的数据源字段上直接标注 {@code @Autowired @Lazy}（Spring 字段注入）；
 * D2 起 database 模块零 Spring，该字段改为纯 Java setter 注入，
 * 本后处理器在 Spring 宿主侧等价恢复原注入语义：
 * <ul>
 *   <li>容器存在 {@code GaarasonDataSource} Bean → 绑定到每个 BaseModel Bean；</li>
 *   <li>不存在 → 跳过（原 {@code @Autowired} 场景下此类应用必然提供该 Bean；
 *       兜底一致性由 {@code BaseModel.getGaarasonDataSource()} 的
 *       注册表/默认连接解析路径保障）。</li>
 * </ul>
 */
public class BaseModelDataSourceBindingPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(BaseModelDataSourceBindingPostProcessor.class);

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof BaseModel)) {
            return bean;
        }
        try {
            GaarasonDataSource dataSource = applicationContext.getBean(GaarasonDataSource.class);
            ((BaseModel<?, ?>) bean).setGaarasonDataSource(dataSource);
            log.debug("[database] 绑定 Model 数据源: {} -> {}", beanName, dataSource.getClass().getSimpleName());
        } catch (BeansException e) {
            log.debug("[database] 容器无 GaarasonDataSource Bean，跳过 {} 的数据源绑定（按别名/默认连接经 ConnectionManager 解析）", beanName);
        }
        return bean;
    }
}
