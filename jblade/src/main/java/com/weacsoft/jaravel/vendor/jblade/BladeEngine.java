package com.weacsoft.jaravel.vendor.jblade;

import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Blade 模板引擎，负责编译和渲染 Blade 模板。
 * <p>
 * <b>缓存机制</b>：采用两级缓存，避免每次渲染都重新编译模板：
 * <ol>
 *   <li><b>一级缓存（内存）</b>：{@code ConcurrentHashMap} 缓存编译后的 {@code Class<?>} 对象，
 *       进程内有效，始终启用。这是主缓存，解决"每用一次就编译一次"的核心问题。</li>
 *   <li><b>二级缓存（可选）</b>：通过 {@link CacheStore} 缓存编译后的字节码（byte[]），
 *       支持跨进程/跨实例共享（如 Redis）。引入 cache 模块后自动启用，未引入时仅用一级缓存。</li>
 * </ol>
 * <p>
 * 当 {@link CacheStore} 为 null 时，仅使用一级内存缓存，不影响功能。
 */
public class BladeEngine {
    /** 默认模板文件后缀，与 BladeCompiler.DEFAULT_SUFFIX 保持一致 */
    public static final String DEFAULT_SUFFIX = BladeCompiler.DEFAULT_SUFFIX;

    private final BladeCompiler compiler;
    /** 一级缓存：模板名 → 编译后的 Class 对象（始终启用） */
    private final Map<String, Class<?>> templateClassCache = new ConcurrentHashMap<>();
    /** 模板实例缓存：模板名 → BladeTemplate 实例 */
    private final Map<String, BladeTemplate> templateInstanceCache = new ConcurrentHashMap<>();
    /** 二级缓存：可选，跨进程共享编译后的字节码 */
    private final CacheStore cacheStore;
    /** 缓存键前缀 */
    private static final String CACHE_KEY_PREFIX = "jblade:template:";
    private final MemoryClassLoader memoryClassLoader;
    /** 是否启用二级缓存 */
    private final boolean useCacheStore;

    public BladeEngine(String templateDir) {
        this(templateDir, DEFAULT_SUFFIX, null, null);
    }

    public BladeEngine(String templateDir, MemoryClassLoader memoryClassLoader) {
        this(templateDir, DEFAULT_SUFFIX, null, memoryClassLoader);
    }

    public BladeEngine(String templateDir, String suffix) {
        this(templateDir, suffix, null, null);
    }

    public BladeEngine(String templateDir, String suffix, MemoryClassLoader memoryClassLoader) {
        this(templateDir, suffix, null, memoryClassLoader);
    }

    /**
     * 创建 BladeEngine 并指定缓存 store。
     *
     * @param templateDir 模板目录
     * @param cacheStore  缓存 store（可为 null，null 时仅用内存缓存）
     */
    public BladeEngine(String templateDir, CacheStore cacheStore) {
        this(templateDir, DEFAULT_SUFFIX, cacheStore, null);
    }

    /**
     * 创建 BladeEngine 并指定缓存 store 和类加载器。
     *
     * @param templateDir       模板目录
     * @param cacheStore        缓存 store（可为 null）
     * @param memoryClassLoader 自定义类加载器
     */
    public BladeEngine(String templateDir, CacheStore cacheStore, MemoryClassLoader memoryClassLoader) {
        this(templateDir, DEFAULT_SUFFIX, cacheStore, memoryClassLoader);
    }

    /**
     * 创建 BladeEngine 并指定后缀和缓存 store。
     *
     * @param templateDir 模板目录
     * @param suffix      模板文件后缀
     * @param cacheStore  缓存 store（可为 null）
     */
    public BladeEngine(String templateDir, String suffix, CacheStore cacheStore) {
        this(templateDir, suffix, cacheStore, null);
    }

    /**
     * 全参数构造器。
     *
     * @param templateDir       模板目录
     * @param suffix            模板文件后缀
     * @param cacheStore        缓存 store（可为 null，null 时仅用内存缓存）
     * @param memoryClassLoader 自定义类加载器（可为 null，null 时创建新的）
     */
    public BladeEngine(String templateDir, String suffix, CacheStore cacheStore, MemoryClassLoader memoryClassLoader) {
        this.memoryClassLoader = memoryClassLoader != null ? memoryClassLoader : new MemoryClassLoader();
        this.compiler = new BladeCompiler(templateDir, this.memoryClassLoader, suffix);
        this.cacheStore = cacheStore;
        this.useCacheStore = cacheStore != null;
    }

    /**
     * 获取当前模板文件后缀
     * @return 后缀字符串，如 ".blade.java"
     */
    public String getSuffix() {
        return compiler.getSuffix();
    }

    /**
     * 获取当前缓存 store（可能为 null）
     */
    public CacheStore getCacheStore() {
        return cacheStore;
    }

    /**
     * 是否启用了二级缓存（CacheStore）
     */
    public boolean isUseCacheStore() {
        return useCacheStore;
    }

    public MemoryClassLoader getMemoryClassLoader() {
        return memoryClassLoader;
    }

    /**
     * 渲染模板。
     *
     * @param templateName 模板名（不含后缀，如 "welcome"、"docs.index"）
     * @param variables    模板变量
     * @return 渲染后的 HTML 字符串
     */
    public String render(String templateName, Map<String, Object> variables) throws Exception {
        BladeTemplate template = loadTemplate(templateName);
        template.setEngine(this);

        template.resetContext();
        BladeContext context = template.getContext();

        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }

        if (!template.isInitialized()) {
            synchronized (template) {
                if (!template.isInitialized()) {
                    template.init();
                    template.setInitialized(true);
                }
            }
        }

        String parentTemplate = context.getParentTemplate();
        if (parentTemplate != null && !parentTemplate.isEmpty()) {
            BladeTemplate parent = loadTemplate(parentTemplate);
            parent.setEngine(this);

            BladeContext parentContext = parent.getContext();
            parentContext.reset();

            if (!parent.isInitialized()) {
                synchronized (parent) {
                    if (!parent.isInitialized()) {
                        parent.init();
                        parent.setInitialized(true);
                    }
                }
            }

            for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
                parentContext.setVariable(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, String> entry : context.getSections().entrySet()) {
                parentContext.setSection(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, java.util.function.Consumer<java.io.Writer>> entry : context.getSectionRenderers().entrySet()) {
                parentContext.setSectionRenderer(entry.getKey(), entry.getValue());
            }

            return parent.render();
        }

        return template.render();
    }

    public String render(String templateName) throws Exception {
        return render(templateName, null);
    }

    // ===== Wire section 渲染方法 =====

    /**
     * 渲染指定 section 的内容（不渲染完整页面，不含布局）。
     * <p>
     * 加载子模板并调用 init() 注册 section renderer，
     * 然后只执行指定 section 的 renderer，不渲染完整页面。
     * 适用于 Wire 部分更新场景。
     *
     * @param templateName 模板名
     * @param sectionName  section 名
     * @param variables    模板变量
     * @return section 的 HTML 内容
     */
    public String renderSection(String templateName, String sectionName, Map<String, Object> variables) throws Exception {
        BladeTemplate template = loadTemplate(templateName);
        template.setEngine(this);
        template.resetContext();
        BladeContext context = template.getContext();

        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }

        if (!template.isInitialized()) {
            synchronized (template) {
                if (!template.isInitialized()) {
                    template.init();
                    template.setInitialized(true);
                }
            }
        }

        Consumer<Writer> renderer = context.getSectionRenderer(sectionName);
        if (renderer == null) {
            String sectionContent = context.getSection(sectionName);
            return sectionContent != null ? sectionContent : "";
        }

        StringWriter writer = new StringWriter();
        renderer.accept(writer);
        return writer.toString();
    }

    /**
     * 批量渲染多个 section（高效：只加载和初始化模板一次）。
     *
     * @param templateName 模板名
     * @param sectionNames 需要渲染的 section 名列表
     * @param variables    模板变量
     * @return section 名 → HTML 内容
     */
    public Map<String, String> renderSections(String templateName, List<String> sectionNames, Map<String, Object> variables) throws Exception {
        BladeTemplate template = loadTemplate(templateName);
        template.setEngine(this);
        template.resetContext();
        BladeContext context = template.getContext();

        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }

        if (!template.isInitialized()) {
            synchronized (template) {
                if (!template.isInitialized()) {
                    template.init();
                    template.setInitialized(true);
                }
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (String sectionName : sectionNames) {
            Consumer<Writer> renderer = context.getSectionRenderer(sectionName);
            if (renderer != null) {
                StringWriter writer = new StringWriter();
                renderer.accept(writer);
                result.put(sectionName, writer.toString());
            } else {
                String sectionContent = context.getSection(sectionName);
                result.put(sectionName, sectionContent != null ? sectionContent : "");
            }
        }
        return result;
    }

    /**
     * 获取模板中所有已注册的 section 名。
     *
     * @param templateName 模板名
     * @return section 名列表
     */
    public List<String> getSectionNames(String templateName) throws Exception {
        BladeTemplate template = loadTemplate(templateName);
        template.setEngine(this);
        template.resetContext();
        BladeContext context = template.getContext();

        if (!template.isInitialized()) {
            synchronized (template) {
                if (!template.isInitialized()) {
                    template.init();
                    template.setInitialized(true);
                }
            }
        }

        return new ArrayList<>(context.getSectionRenderers().keySet());
    }

    /**
     * 加载（编译+缓存）模板。
     * <p>
     * 流程：
     * <ol>
     *   <li>查一级缓存（ConcurrentHashMap），命中则直接返回 Class；</li>
     *   <li>查二级缓存（CacheStore），命中则加载字节码到 MemoryClassLoader；</li>
     *   <li>缓存未命中时调用 {@link BladeCompiler#compile} 编译模板；</li>
     *   <li>编译后将字节码存入二级缓存（如果启用）；</li>
     *   <li>Class 存入一级缓存；</li>
     *   <li>创建或获取 BladeTemplate 实例（实例缓存）。</li>
     * </ol>
     * <p>
     * <b>关键修复</b>：不再在每次调用时都执行 compile()，仅缓存未命中时才编译。
     *
     * @param templateName 模板名
     * @return BladeTemplate 实例
     */
    public BladeTemplate loadTemplate(String templateName) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        templateName = templateName.replace("'", "").replace("\"", "");

        // 1. 查一级缓存（内存）
        Class<?> templateClass = templateClassCache.get(templateName);

        if (templateClass == null) {
            synchronized (this) {
                templateClass = templateClassCache.get(templateName);
                if (templateClass == null) {
                    // 2. 查二级缓存（CacheStore）
                    if (useCacheStore) {
                        templateClass = loadFromCacheStore(templateName);
                    }
                    // 3. 缓存未命中，编译模板
                    if (templateClass == null) {
                        templateClass = compileAndCache(templateName);
                    }
                    // 存入一级缓存
                    templateClassCache.put(templateName, templateClass);
                }
            }
        }

        // 4. 获取或创建模板实例
        BladeTemplate template = templateInstanceCache.get(templateName);
        if (template == null) {
            synchronized (this) {
                template = templateInstanceCache.get(templateName);
                if (template == null) {
                    template = (BladeTemplate) templateClass.getDeclaredConstructor().newInstance();
                    template.setEngine(this);
                    templateInstanceCache.put(templateName, template);
                }
            }
        }

        return template;
    }

    /**
     * 从 CacheStore 加载编译后的字节码。
     *
     * @param templateName 模板名
     * @return 加载的 Class，未命中返回 null
     */
    private Class<?> loadFromCacheStore(String templateName) {
        try {
            String cacheKey = CACHE_KEY_PREFIX + templateName;
            // 尝试获取字节码和类名
            Object cached = cacheStore.get(cacheKey);
            if (cached instanceof CompiledTemplateData) {
                CompiledTemplateData data = (CompiledTemplateData) cached;
                // 将字节码加载到 MemoryClassLoader
                memoryClassLoader.getCompiledClasses().put(data.className, data.bytecode);
                return memoryClassLoader.loadClass(data.className);
            }
        } catch (Exception e) {
            // 缓存读取失败，降级为重新编译
        }
        return null;
    }

    /**
     * 编译模板并缓存字节码。
     *
     * @param templateName 模板名
     * @return 编译后的 Class
     */
    private Class<?> compileAndCache(String templateName) throws IOException, ClassNotFoundException {
        // 编译模板（读取文件 + 生成源码 + JavaC 编译）
        String className = compiler.compile(templateName);
        // 从 MemoryClassLoader 加载 Class
        Class<?> templateClass = memoryClassLoader.loadClass(className);

        // 将字节码存入二级缓存
        if (useCacheStore) {
            try {
                byte[] bytecode = memoryClassLoader.getCompiledClasses().get(className);
                if (bytecode != null) {
                    String cacheKey = CACHE_KEY_PREFIX + templateName;
                    cacheStore.put(cacheKey, new CompiledTemplateData(className, bytecode), 0);
                }
            } catch (Exception e) {
                // 缓存写入失败，不影响功能
            }
        }

        return templateClass;
    }

    /**
     * 清除所有缓存（一级+二级+实例缓存）。
     * <p>
     * <b>安全清理</b>：仅清除本引擎管理的模板缓存键（前缀 {@code jblade:template:}），
     * 不会调用 {@code CacheStore.flush()} 清空整个 store，避免影响其他模块的缓存。
     */
    public void clearCache() {
        // 清除一级缓存中所有模板的二级缓存条目（按 key 逐个 forget）
        if (useCacheStore) {
            for (String templateName : templateClassCache.keySet()) {
                cacheStore.forget(CACHE_KEY_PREFIX + templateName);
            }
        }
        templateClassCache.clear();
        clearTemplateInstanceCache();
    }

    /**
     * 清除指定模板的所有缓存（一级 Class + 二级 CacheStore + 实例缓存）。
     * <p>
     * 适用于模板文件更新后仅刷新该模板的场景，不影响其他已编译模板。
     *
     * @param templateName 模板名
     */
    public void clearTemplate(String templateName) {
        templateName = templateName.replace("'", "").replace("\"", "");
        templateClassCache.remove(templateName);
        templateInstanceCache.remove(templateName);
        if (useCacheStore) {
            cacheStore.forget(CACHE_KEY_PREFIX + templateName);
        }
    }

    /**
     * 清除模板实例缓存。
     */
    public void clearTemplateInstanceCache() {
        for (BladeTemplate template : templateInstanceCache.values()) {
            template.resetContext();
        }
        templateInstanceCache.clear();
    }

    /**
     * 获取模板实例缓存大小。
     */
    public int getTemplateInstanceCacheSize() {
        return templateInstanceCache.size();
    }

    /**
     * 获取一级缓存中的模板数量。
     */
    public int getClassCacheSize() {
        return templateClassCache.size();
    }

    /**
     * 编译模板数据的序列化包装类，用于 CacheStore 存储字节码和类名。
     */
    public static class CompiledTemplateData implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        final String className;
        final byte[] bytecode;

        public CompiledTemplateData(String className, byte[] bytecode) {
            this.className = className;
            this.bytecode = bytecode;
        }

        public String getClassName() {
            return className;
        }

        public byte[] getBytecode() {
            return bytecode;
        }
    }
}
