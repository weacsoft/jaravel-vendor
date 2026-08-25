package com.weacsoft.jaravel.vendor.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConfigRepository} 多层配置优先级与类型转换单元测试（零 Spring，P3 起外部配置层为函数注入）。
 */
class ConfigRepositoryTest {

    private Map<String, Object> external;
    private java.util.function.Function<String, Object> externalLookup;

    @BeforeEach
    void setUp() {
        external = new LinkedHashMap<>();
        external.put("app.env", "prod");              // 仅来自外部配置
        external.put("app.name", "from-env");         // 与 codeConfig 同名，验证 codeConfig 优先
        externalLookup = external::get;
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
        ConfigRepository repo = new ConfigRepository(externalLookup);
        repo.registerConfigDefinition(appConfig(Map.of("name", "from-code")));

        // 运行时覆盖优先级最高
        repo.set("app.name", "from-override");
        assertEquals("from-override", repo.get("app.name"));
    }

    @Test
    void codeConfigBeatsEnvironment() {
        ConfigRepository repo = new ConfigRepository(externalLookup);
        repo.registerConfigDefinition(appConfig(Map.of("name", "from-code")));

        // codeConfig 优先于外部配置
        assertEquals("from-code", repo.get("app.name"));
    }

    @Test
    void environmentIsFallbackWhenNoOverrideOrCodeConfig() {
        ConfigRepository repo = new ConfigRepository(externalLookup);
        // app.env 仅存在于外部配置
        assertEquals("prod", repo.get("app.env"));
    }

    @Test
    void defaultValueWhenMissing() {
        ConfigRepository repo = new ConfigRepository(externalLookup);
        assertEquals("default", repo.get("not.exist", "default"));
        assertFalse(repo.has("not.exist"));
        assertTrue(repo.has("app.env"));
    }

    @Test
    void typeConversionsGetIntAndGetBool() {
        ConfigRepository repo = new ConfigRepository(externalLookup);
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
    void nullExternalIsSupported() {
        // 外部配置源可为 null（构造器允许）
        ConfigRepository repo = new ConfigRepository(null);
        repo.set("k", "v");
        assertEquals("v", repo.get("k"));
        assertEquals("def", repo.get("missing", "def"));
        assertTrue(repo.has("k"));
    }
}
