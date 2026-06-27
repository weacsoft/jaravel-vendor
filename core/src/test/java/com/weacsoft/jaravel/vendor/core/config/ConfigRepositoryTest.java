package com.weacsoft.jaravel.vendor.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConfigRepository} 多层配置优先级与类型转换单元测试。
 */
class ConfigRepositoryTest {

    private StandardEnvironment env;

    @BeforeEach
    void setUp() {
        env = new StandardEnvironment();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("app.env", "prod");                 // 仅来自 Environment
        props.put("app.name", "from-env");            // 与 codeConfig 同名，验证 codeConfig 优先
        env.getPropertySources().addFirst(new MapPropertySource("test-props", props));
    }

    private ConfigDefinition appConfig(Map<String, Object> values) {
        return new ConfigDefinition() {
            @Override
            public String namespace() {
                return "app";
            }

            @Override
            public Map<String, Object> values() {
                return values;
            }
        };
    }

    @Test
    void overrideBeatsCodeConfigAndEnvironment() {
        ConfigRepository repo = new ConfigRepository(env);
        repo.registerConfigDefinition(appConfig(Map.of("name", "from-code")));

        // 运行时覆盖优先级最高
        repo.set("app.name", "from-override");
        assertEquals("from-override", repo.get("app.name"));
    }

    @Test
    void codeConfigBeatsEnvironment() {
        ConfigRepository repo = new ConfigRepository(env);
        repo.registerConfigDefinition(appConfig(Map.of("name", "from-code")));

        // codeConfig 优先于 Environment
        assertEquals("from-code", repo.get("app.name"));
    }

    @Test
    void environmentIsFallbackWhenNoOverrideOrCodeConfig() {
        ConfigRepository repo = new ConfigRepository(env);
        // app.env 仅存在于 Environment
        assertEquals("prod", repo.get("app.env"));
    }

    @Test
    void defaultValueWhenMissing() {
        ConfigRepository repo = new ConfigRepository(env);
        assertEquals("default", repo.get("not.exist", "default"));
        assertFalse(repo.has("not.exist"));
        assertTrue(repo.has("app.env"));
    }

    @Test
    void typeConversionsGetIntAndGetBool() {
        ConfigRepository repo = new ConfigRepository(env);
        repo.set("app.port", "8080");
        repo.set("app.debug", "true");
        repo.set("app.flag", "1");
        repo.set("app.other", "yes");   // 仅 true / 1 视为真

        assertEquals(8080, repo.getInt("app.port", 0));
        assertEquals(0, repo.getInt("app.missing", 0));
        assertEquals(0, repo.getInt("app.other", 0)); // "yes" 无法解析为整数

        assertTrue(repo.getBool("app.debug", false));
        assertTrue(repo.getBool("app.flag", false));
        assertFalse(repo.getBool("app.other", false)); // "yes" 非真
        assertFalse(repo.getBool("app.missing", false));
    }

    @Test
    void nullEnvironmentIsSupported() {
        // environment 可为 null（构造器允许）
        ConfigRepository repo = new ConfigRepository(null);
        repo.set("k", "v");
        assertEquals("v", repo.get("k"));
        assertEquals("def", repo.get("missing", "def"));
        assertTrue(repo.has("k"));
    }
}
