package com.weacsoft.jaravel.vendor.jblade;

import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;

public class BladeContext {

    /**
     * @parent 占位符（与 Laravel 的 parentPlaceholder 机制等价）。
     */
    public static final String PARENT_PLACEHOLDER = "@__jblade_parent__@";

    private final Map<String, Object> variables;
    private final Map<String, String> sections;
    private final Map<String, Consumer<Writer>> sectionRenderers;
    private final Stack<String> sectionStack;
    private final Map<String, Object> componentData;
    private final Map<String, String> componentSlots;
    private StringBuilder currentSectionContent;
    private String parentTemplate;
    private final java.util.LinkedHashSet<String> yieldedNames;
    private boolean inSection;
    private String currentComponent;
    private String currentSlot;
    private StringBuilder currentSlotContent;
    private boolean inSlot;

    /** 收集的外部资源(assets),按模板名为 key,保证同模板只收集一次 */
    private final Map<String, String> collectedAssets;
    /** 收集的脚本(script),按模板名为 key,每实例都执行 */
    private final Map<String, String> collectedScripts;

    public BladeContext() {
        this.variables = new HashMap<>();
        this.sections = new HashMap<>();
        this.sectionRenderers = new HashMap<>();
        this.sectionStack = new Stack<>();
        this.currentSectionContent = new StringBuilder();
        this.inSection = false;
        this.componentData = new HashMap<>();
        this.componentSlots = new HashMap<>();
        this.currentSlotContent = new StringBuilder();
        this.inSlot = false;
        this.yieldedNames = new java.util.LinkedHashSet<>();
        this.collectedAssets = new LinkedHashMap<>();
        this.collectedScripts = new LinkedHashMap<>();
    }

    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }

    public Object getVariable(String name) {
        return variables.get(name);
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setSection(String name, String content) {
        sections.put(name, content);
    }

    /**
     * Laravel 语义的 section 扩展：
     * 若已有同名 section（来自更下层的子模板），则用新内容（父模板内容）
     * 替换已有内容中的 @parent 占位符；否则直接注册。
     * 该机制天然支持不限层级的多重继承。
     */
    public void extendSection(String name, String content) {
        String existing = sections.get(name);
        if (existing != null) {
            content = existing.replace(PARENT_PLACEHOLDER, content);
        }
        sections.put(name, content);
    }

    /**
     * 获取 section 内容并清理残留的 @parent 占位符。
     */
    public String yieldSection(String name) {
        String content = sections.get(name);
        if (content == null) {
            return null;
        }
        return content.replace(PARENT_PLACEHOLDER, "");
    }

    /* ==================== $loop 支持 ==================== */

    private final Deque<LoopHelper> loopStack = new ArrayDeque<>();

    /**
     * 进入一层循环。count 未知时传 -1。
     */
    public LoopHelper pushLoop(int count) {
        LoopHelper parent = loopStack.peek();
        LoopHelper loop = new LoopHelper(count, loopStack.size() + 1, parent);
        loopStack.push(loop);
        return loop;
    }

    public void popLoop() {
        if (!loopStack.isEmpty()) {
            loopStack.pop();
        }
    }

    public LoopHelper currentLoop() {
        return loopStack.peek();
    }

    public String getSection(String name) {
        return sections.get(name);
    }

    public Map<String, String> getSections() {
        return sections;
    }

    public void setSectionRenderer(String name, Consumer<Writer> renderer) {
        sectionRenderers.put(name, renderer);
    }

    public Consumer<Writer> getSectionRenderer(String name) {
        return sectionRenderers.get(name);
    }

    public Map<String, Consumer<Writer>> getSectionRenderers() {
        return sectionRenderers;
    }

    public void reset() {
        variables.clear();
        sections.clear();
        sectionRenderers.clear();
        sectionStack.clear();
        componentData.clear();
        componentSlots.clear();
        yieldedNames.clear();
        currentSectionContent = new StringBuilder();
        parentTemplate = null;
        inSection = false;
        currentComponent = null;
        currentSlot = null;
        currentSlotContent = new StringBuilder();
        inSlot = false;
        loopStack.clear();
        collectedAssets.clear();
        collectedScripts.clear();
    }

    public void startSection(String name) {
        sectionStack.push(name);
        currentSectionContent = new StringBuilder();
        inSection = true;
    }

    public void appendSectionContent(String content) {
        if (inSection) {
            currentSectionContent.append(content);
        }
    }

    public void endSection() {
        if (!sectionStack.isEmpty()) {
            String name = sectionStack.pop();
            sections.put(name, currentSectionContent.toString());
            inSection = !sectionStack.isEmpty();
            if (inSection) {
                currentSectionContent = new StringBuilder(sections.get(sectionStack.peek()));
            }
        }
    }

    public String getCurrentSectionName() {
        return sectionStack.isEmpty() ? null : sectionStack.peek();
    }

    public String getParentTemplate() {
        return parentTemplate;
    }

    public void setParentTemplate(String parentTemplate) {
        this.parentTemplate = parentTemplate;
    }

    /**
     * 记录一个 @yield 区域名（渲染时由 yieldSection 调用，用于在编译期分析模板继承/区块关系）。
     *
     * @param name yield 区域名
     */
    public void recordYield(String name) {
        if (name != null && !name.isEmpty()) {
            yieldedNames.add(name);
        }
    }

    /**
     * 取得本模板（含继承链）渲染时实际输出的所有 @yield 区域名。
     *
     * @return yield 区域名集合（有序）
     */
    public java.util.LinkedHashSet<String> getYieldedNames() {
        return yieldedNames;
    }

    public boolean isInSection() {
        return inSection;
    }

    public String getCurrentSectionContent() {
        return currentSectionContent.toString();
    }

    public void setCurrentSectionContent(String content) {
        currentSectionContent = new StringBuilder(content);
    }

    public void startComponent(String componentName) {
        this.currentComponent = componentName;
        this.componentSlots.clear();
        this.currentSlot = null;
        this.currentSlotContent = new StringBuilder();
        this.inSlot = false;
    }

    public void endComponent() {
        this.currentComponent = null;
    }

    public String getCurrentComponent() {
        return currentComponent;
    }

    public void setComponentData(String key, Object value) {
        componentData.put(key, value);
    }

    public Object getComponentData(String key) {
        return componentData.get(key);
    }

    public Map<String, Object> getComponentData() {
        return componentData;
    }

    public void clearComponentData() {
        componentData.clear();
    }

    public void startSlot(String slotName) {
        this.currentSlot = slotName;
        this.currentSlotContent = new StringBuilder();
        this.inSlot = true;
    }

    public void endSlot() {
        if (currentSlot != null && inSlot) {
            componentSlots.put(currentSlot, currentSlotContent.toString());
            currentSlot = null;
            currentSlotContent = new StringBuilder();
            inSlot = false;
        }
    }

    public void appendSlotContent(String content) {
        if (inSlot) {
            currentSlotContent.append(content);
        }
    }

    public String getSlot(String slotName) {
        return componentSlots.get(slotName);
    }

    /**
     * 直接设置插槽内容（新版编译器组件渲染使用）。
     */
    public void setSlot(String slotName, String content) {
        componentSlots.put(slotName, content);
    }

    public Map<String, String> getComponentSlots() {
        return componentSlots;
    }

    public boolean isInSlot() {
        return inSlot;
    }

    public String getCurrentSlot() {
        return currentSlot;
    }

    /* ==================== @assets / @script 收集 ==================== */

    /**
     * 收集外部资源(@assets 块)。
     * <p>
     * 相同 key 时保留首次收集的内容(保证 CDN 资源只加载一次)。
     * 子模板优先收集(因为 initInheritanceChain 自下而上执行)。
     *
     * @param key     资源键(模板名)
     * @param content 资源 HTML 内容(如 script/link 标签)
     */
    public void collectAssets(String key, String content) {
        if (key != null && content != null && !content.isEmpty()) {
            collectedAssets.putIfAbsent(key, content);
        }
    }

    /**
     * 收集脚本(@script 块)。
     * <p>
     * 相同 key 时保留首次收集的内容(与 assets 一致,避免重复)。
     * 每实例都执行,不全局去重。
     *
     * @param key     脚本键(模板名)
     * @param content 脚本 HTML 内容(如 <script>...</script>)
     */
    public void collectScript(String key, String content) {
        if (key != null && content != null && !content.isEmpty()) {
            collectedScripts.putIfAbsent(key, content);
        }
    }

    /**
     * 获取所有收集的外部资源(按收集顺序)。
     */
    public Map<String, String> getCollectedAssets() {
        return collectedAssets;
    }

    /**
     * 获取所有收集的脚本(按收集顺序)。
     */
    public Map<String, String> getCollectedScripts() {
        return collectedScripts;
    }

    /**
     * 生成拼接后的外部资源 HTML。
     */
    public String renderCollectedAssets() {
        StringBuilder sb = new StringBuilder();
        for (String content : collectedAssets.values()) {
            sb.append(content).append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成拼接后的脚本 HTML。
     */
    public String renderCollectedScripts() {
        StringBuilder sb = new StringBuilder();
        for (String content : collectedScripts.values()) {
            sb.append(content).append("\n");
        }
        return sb.toString();
    }
}