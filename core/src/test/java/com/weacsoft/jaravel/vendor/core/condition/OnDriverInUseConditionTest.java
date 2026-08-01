package com.weacsoft.jaravel.vendor.core.condition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OnDriverInUseCondition} 测试：验证「用上了才装配」的判定逻辑。
 */
class OnDriverInUseConditionTest {

    /** 模拟 redis session 驱动条件。 */
    static class RedisSessionCondition extends OnDriverInUseCondition {
        RedisSessionCondition() {
            super("redis", "jaravel.session.stores.", ".driver", "jaravel.session.driver");
            enableKey("jaravel.session.redis.auto-register");
        }
    }

    /** 模拟 database 缓存驱动条件。 */
    static class DatabaseCacheCondition extends OnDriverInUseCondition {
        DatabaseCacheCondition() {
            super("database", "jaravel.cache.stores.", ".driver", "jaravel.cache.driver");
        }
    }

    /**
     * 轻量 {@link ConditionContext} 桩：只有 {@code getEnvironment()} 有意义，
     * 其余方法本条件用不到，返回 {@code null} 即可，避免引入 mock 框架。
     */
    private static class StubContext implements ConditionContext {
        private final Environment environment;

        StubContext(Environment environment) {
            this.environment = environment;
        }

        @Override
        public BeanDefinitionRegistry getRegistry() {
            return null;
        }

        @Override
        public ConfigurableListableBeanFactory getBeanFactory() {
            return null;
        }

        @Override
        public Environment getEnvironment() {
            return environment;
        }

        @Override
        public ResourceLoader getResourceLoader() {
            return null;
        }

        @Override
        public ClassLoader getClassLoader() {
            return getClass().getClassLoader();
        }
    }

    /**
     * 用给定属性构造判定上下文。
     *
     * @param props 属性键值
     * @return ConditionContext
     */
    private ConditionContext contextWith(Map<String, Object> props) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", props));
        return new StubContext(env);
    }

    /** 本条件不读取注解元数据，传 {@code null} 即可。 */
    private final AnnotatedTypeMetadata metadata = null;

    @Test
    @DisplayName("未配置任何驱动时不装配——安装 != 启用")
    void notMatchWhenNothingConfigured() {
        assertFalse(new RedisSessionCondition().matches(contextWith(new HashMap<>()), metadata),
                "仅把 session-redis 放进 classpath 不应触发装配");
    }

    @Test
    @DisplayName("session driver 配成 file 时 redis 模块不装配")
    void notMatchWhenOtherDriverSelected() {
        Map<String, Object> props = new HashMap<>();
        props.put("jaravel.session.driver", "file");
        assertFalse(new RedisSessionCondition().matches(contextWith(props), metadata));
    }

    @Test
    @DisplayName("session driver 配成 redis 时装配")
    void matchWhenDriverSelected() {
        Map<String, Object> props = new HashMap<>();
        props.put("jaravel.session.driver", "redis");
        assertTrue(new RedisSessionCondition().matches(contextWith(props), metadata));
    }

    @Test
    @DisplayName("驱动名判定忽略大小写")
    void matchIsCaseInsensitive() {
        Map<String, Object> props = new HashMap<>();
        props.put("jaravel.session.driver", "REDIS");
        assertTrue(new RedisSessionCondition().matches(contextWith(props), metadata));
    }

    @Test
    @DisplayName("auto-register=true 强制启用，优先级最高")
    void enableKeyForcesMatch() {
        Map<String, Object> props = new HashMap<>();
        props.put("jaravel.session.driver", "file");
        props.put("jaravel.session.redis.auto-register", "true");
        assertTrue(new RedisSessionCondition().matches(contextWith(props), metadata));
    }

    @Test
    @DisplayName("auto-register=false 强制关闭，优先级最高")
    void enableKeyForcesNoMatch() {
        Map<String, Object> props = new HashMap<>();
        props.put("jaravel.session.driver", "redis");
        props.put("jaravel.session.redis.auto-register", "false");
        assertFalse(new RedisSessionCondition().matches(contextWith(props), metadata));
    }

    @Test
    @DisplayName("映射式配置 stores.*.driver 命中即装配")
    void matchByMapStyleKey() {
        Map<String, Object> props = new HashMap<>();
        props.put("jaravel.cache.stores.db.driver", "database");
        props.put("jaravel.cache.stores.mem.driver", "array");
        assertTrue(new DatabaseCacheCondition().matches(contextWith(props), metadata));
    }

    @Test
    @DisplayName("映射式配置里全是 array/file 时不装配 database 驱动")
    void notMatchWhenNoDatabaseStore() {
        Map<String, Object> props = new HashMap<>();
        props.put("jaravel.cache.stores.mem.driver", "array");
        props.put("jaravel.cache.stores.disk.driver", "file");
        assertFalse(new DatabaseCacheCondition().matches(contextWith(props), metadata));
    }
}
