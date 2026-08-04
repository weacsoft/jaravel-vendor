package com.weacsoft.jaravel.vendor.captcha.springboot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码场景注册表 —— 前端可选场景的<b>白名单</b>。
 * <p>
 * <b>安全边界</b>：这是前端唯一能影响验证码生成参数的入口，且只能「选择」不能「设值」。
 * <ul>
 *   <li>前端传 {@code scene=login} → 命中白名单 → 使用后端为该场景预设的配置；</li>
 *   <li>前端传 {@code scene=../../etc} 或任意伪造值 → 未命中 → 回落到全局默认配置；</li>
 *   <li>前端传 {@code tolerance=999} → 完全被忽略，控制器不再读取任何数值型参数。</li>
 * </ul>
 * <p>
 * 解析结果基于全局配置 {@code copy()} 后再叠加场景差异，
 * 因此场景中未声明的字段一律继承全局配置，不会出现「只传一个字段导致其余字段被重置」的问题。
 * <p>
 * 解析结果按场景名缓存，避免每次请求都重复构建配置对象。
 *
 * @see CaptchaSceneProperties
 */
public class CaptchaSceneRegistry {

    private static final Logger log = LoggerFactory.getLogger(CaptchaSceneRegistry.class);

    /** 场景名允许的字符：字母、数字、下划线、短横线，长度 1~32 */
    private static final int MAX_SCENE_NAME_LENGTH = 32;

    /** 全局默认配置（核心层对象） */
    private final com.weacsoft.jaravel.vendor.captcha.CaptchaProperties globalProperties;

    /** 场景定义（配置文件声明） */
    private final Map<String, CaptchaSceneProperties> scenes;

    /** 解析结果缓存：场景名 → 已合并的核心配置 */
    private final Map<String, com.weacsoft.jaravel.vendor.captcha.CaptchaProperties> cache =
            new ConcurrentHashMap<>();

    public CaptchaSceneRegistry(com.weacsoft.jaravel.vendor.captcha.CaptchaProperties globalProperties,
                                Map<String, CaptchaSceneProperties> scenes) {
        this.globalProperties = (globalProperties != null)
                ? globalProperties
                : com.weacsoft.jaravel.vendor.captcha.CaptchaProperties.createDefault();
        Map<String, CaptchaSceneProperties> copy = new LinkedHashMap<>();
        if (scenes != null) {
            scenes.forEach((name, def) -> {
                if (name != null && !name.isEmpty() && def != null) {
                    copy.put(name, def);
                }
            });
        }
        this.scenes = Collections.unmodifiableMap(copy);
        if (!this.scenes.isEmpty()) {
            log.info("[Captcha] 已注册 {} 个验证码场景: {}", this.scenes.size(), this.scenes.keySet());
        }
    }

    /**
     * 判断场景名是否在白名单内。
     *
     * @param scene 场景名（允许 null）
     * @return 命中返回 true
     */
    public boolean has(String scene) {
        return normalize(scene) != null && scenes.containsKey(normalize(scene));
    }

    /**
     * 解析场景对应的验证码配置。
     * <p>
     * 未命中白名单时返回 {@code null}，调用方应据此使用全局默认配置。
     * 这样既避免了「未知场景导致报错」，也避免了「未知场景被当成放宽难度的手段」。
     *
     * @param scene 前端传入的场景名（不可信输入）
     * @return 已合并的核心配置副本；未命中返回 null
     */
    public com.weacsoft.jaravel.vendor.captcha.CaptchaProperties resolve(String scene) {
        String name = normalize(scene);
        if (name == null) {
            return null;
        }
        CaptchaSceneProperties def = scenes.get(name);
        if (def == null) {
            log.debug("[Captcha] 未知场景 '{}'，回落到全局默认配置", name);
            return null;
        }
        return cache.computeIfAbsent(name, k -> {
            com.weacsoft.jaravel.vendor.captcha.CaptchaProperties merged = globalProperties.copy();
            def.applyTo(merged);
            log.debug("[Captcha] 场景 '{}' 配置已解析并缓存", k);
            return merged;
        });
    }

    /**
     * 返回所有已注册场景名（只读）。
     */
    public Set<String> names() {
        return scenes.keySet();
    }

    /**
     * 返回场景定义（只读），供文档 / 调试接口展示。
     */
    public Map<String, CaptchaSceneProperties> definitions() {
        return scenes;
    }

    /**
     * 规范化并校验场景名：去空白、限制长度与字符集。
     * <p>
     * 场景名来自前端，必须防止超长字符串与异常字符污染日志和缓存 key。
     *
     * @return 合法场景名；非法或为空返回 null
     */
    private String normalize(String scene) {
        if (scene == null) {
            return null;
        }
        String s = scene.trim();
        if (s.isEmpty() || s.length() > MAX_SCENE_NAME_LENGTH) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                return null;
            }
        }
        return s;
    }
}
