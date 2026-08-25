package com.weacsoft.jaravel.vendor.springboot.jblade;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 由 {@link RegisterView} 经 {@link Import} 引入。
 * <p>
 * 真正的 {@link ViewManager} Bean 与收集逻辑统一由 {@code ViewAutoConfiguration} 的
 * {@code @Bean viewManager} 完成，因此本注册器无需在 BeanDefinition 阶段做任何事，
 * 仅作为"导入触发器"存在，确保 {@code @RegisterView} 标注能引导视图模块的自动装配被加载。
 * 这样避免与 {@code ViewAutoConfiguration} 中同名的 {@code viewManager} BeanDefinition 产生冲突。
 * </p>
 */
public class ViewRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        // 故意为空：ViewManager 由 ViewAutoConfiguration 暴露，View 实例由 ObjectProvider 收集。
    }
}
