package com.weacsoft.jaravel.vendor.jblade.view;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 持有所有被 {@link RegisterView} 声明的 {@link View} 实现，并决定默认激活哪一个。
 * <p>
 * 默认选择优先级（与模块兜底规则一致：声明 → 配置 → 默认）：
 * <ol>
 *   <li>若设置了配置指定的默认名（{@link #setConfiguredDefault}），优先用它；</li>
 *   <li>否则用声明时标 {@code defaultView=true} 的那个；</li>
 *   <li>否则用名为 {@code blade} 的实现；</li>
 *   <li>否则用第一个注册的；</li>
 *   <li>都无则返回空（由 {@code ViewAutoConfiguration} 兜底注册 Blade）。</li>
 * </ol>
 */
public class ViewManager {

    private static final Logger log = LoggerFactory.getLogger(ViewManager.class);

    private final Map<String, View> views = new LinkedHashMap<>();
    private String configuredDefault;
    private String annotatedDefault;

    public void register(View view) {
        if (views.containsKey(view.name())) {
            log.warn("[view] 重复注册同名 View 实现，覆盖: {}", view.name());
        }
        views.put(view.name(), view);
        log.info("[view] 注册 View 实现: {} (共 {} 个)", view.name(), views.size());
    }

    public View get(String name) {
        return views.get(name);
    }

    public Map<String, View> all() {
        return views;
    }

    public void setConfiguredDefault(String name) {
        this.configuredDefault = name;
    }

    public void setAnnotatedDefault(String name) {
        this.annotatedDefault = name;
    }

    /**
     * 解析当前应激活的默认 View 实现。
     *
     * @return 默认 View，可能为空（尚未声明任何实现）
     */
    public Optional<View> defaultView() {
        if (configuredDefault != null && views.containsKey(configuredDefault)) {
            return Optional.of(views.get(configuredDefault));
        }
        if (annotatedDefault != null && views.containsKey(annotatedDefault)) {
            return Optional.of(views.get(annotatedDefault));
        }
        if (views.containsKey("blade")) {
            return Optional.of(views.get("blade"));
        }
        if (!views.isEmpty()) {
            return Optional.of(views.values().iterator().next());
        }
        return Optional.empty();
    }
}
