package com.weacsoft.jaravel.vendor.jblade;

import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
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

    // ===== 预编译模式字段 =====
    /** 预编译模式：从打包文件加载 */
    private String precompiledPackagePath;
    /** 预编译模式：从目录加载 class 文件 */
    private String precompiledClassesDir;
    /** 是否使用预编译模式 */
    private boolean precompiledMode = false;
    /** 预编译模板名 -> 类全限定名映射 */
    private Map<String, String> precompiledTemplateMapping;

    /** 模板目录（用于 view:cache 扫描与预编译产物定位） */
    private final String templateDir;

    /**
     * PJAX 区域元数据缓存（编译期分析所得）。
     * <p>key = 模板名，value = 该模板的布局父链 + 实际输出的 @yield 区域名集合。
     * 在模板编译时填充（{@link #analyzeRegions}），PJAX 切换时按此推导需要更新的区域，无需重新渲染。</p>
     */
    private final Map<String, TemplateRegionMeta> regionMetaCache = new ConcurrentHashMap<>();

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
        // 父类加载器必须能解析模板编译产物所依赖的运行期类（如 jblade 的 BladeTemplate）。
        // 普通 -cp / spring-boot:run 下，BladeEngine 的类加载器即应用类加载器，天然可见这些类；
        // 但在 Spring Boot 可执行 fat-jar（java -jar）下，JVM 系统类加载器只包含最外层 jar，
        // 真正的依赖位于 BOOT-INF/lib（由 LaunchedURLClassLoader 加载）。若此处用默认系统类加载器作
        // 为父类，运行期加载编译后的模板类时会 NoClassDefFoundError: BladeTemplate。
        // 因此显式以本类所在的类加载器作为父类，fat-jar 与展开部署形态表现一致。
        ClassLoader runtimeParent = BladeEngine.class.getClassLoader();
        this.memoryClassLoader = memoryClassLoader != null ? memoryClassLoader
                : new MemoryClassLoader(new ConcurrentHashMap<>(), runtimeParent);
        this.templateDir = templateDir;
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
     * 获取当前模板目录
     * @return 模板目录（classpath 下相对路径，如 "templates"）
     */
    public String getTemplateDir() {
        return templateDir;
    }

    /**
     * 取得一级模板类缓存当前大小（供 ViewCache 统计）。
     * @return 已编译模板数量
     */
    public int templateClassCacheSize() {
        return templateClassCache.size();
    }

    /**
     * 判断模板是否存在（供 @includeIf 等指令使用）。
     */
    public boolean templateExists(String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            return false;
        }
        templateName = templateName.replace("'", "").replace("\"", "");
        if (templateClassCache.containsKey(templateName)) {
            return true;
        }
        if (precompiledMode) {
            return precompiledTemplateMapping != null && precompiledTemplateMapping.containsKey(templateName);
        }
        return compiler.templateExists(templateName);
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

    // ===== 预编译模式工厂方法 =====

    /**
     * 创建使用预编译打包文件的 BladeEngine。
     * <p>
     * 运行时从指定的 .jblade.zip 文件加载模板字节码，无需 JDK。
     * 预编译产物通过 {@link BladePrecompiler} 生成。
     *
     * @param packagePath 预编译打包文件路径（.jblade.zip）
     * @return 预编译模式的 BladeEngine
     */
    public static BladeEngine fromPrecompiledPackage(String packagePath) {
        BladeEngine engine = new BladeEngine("");
        engine.precompiledPackagePath = packagePath;
        engine.precompiledMode = true;
        engine.loadPrecompiled();
        return engine;
    }

    /**
     * 创建使用预编译 class 目录的 BladeEngine。
     * <p>
     * 运行时从指定目录加载 .class 文件，无需 JDK。
     * 预编译产物通过 {@link BladePrecompiler} 生成。
     *
     * @param classesDir 预编译 class 文件目录路径
     * @return 预编译模式的 BladeEngine
     */
    public static BladeEngine fromPrecompiledClasses(String classesDir) {
        BladeEngine engine = new BladeEngine("");
        engine.precompiledClassesDir = classesDir;
        engine.precompiledMode = true;
        engine.loadPrecompiled();
        return engine;
    }

    /**
     * 是否使用预编译模式。
     *
     * @return true 表示当前引擎使用预编译模式加载模板
     */
    public boolean isPrecompiledMode() {
        return precompiledMode;
    }

    /**
     * 获取预编译模板名->类名映射（预编译模式下非 null）。
     *
     * @return 模板名映射，非预编译模式返回 null
     */
    public Map<String, String> getPrecompiledTemplateMapping() {
        return precompiledTemplateMapping;
    }

    /**
     * 加载预编译产物到内存。
     * <p>
     * 将所有字节码加载到 MemoryClassLoader，并建立模板名->类名映射。
     * 在工厂方法中调用，完成后即可通过 loadTemplate 渲染模板。
     */
    private void loadPrecompiled() {
        try {
            PrecompiledTemplateLoader.PrecompiledBundle bundle;
            if (precompiledPackagePath != null) {
                bundle = PrecompiledTemplateLoader.loadBundleFromPackage(precompiledPackagePath);
            } else if (precompiledClassesDir != null) {
                bundle = PrecompiledTemplateLoader.loadBundleFromDirectory(precompiledClassesDir);
            } else {
                throw new IllegalStateException("预编译模式未配置加载路径");
            }
            // 将所有字节码加载到 MemoryClassLoader
            memoryClassLoader.getCompiledClasses().putAll(bundle.classBytecodes);
            this.precompiledTemplateMapping = bundle.templateToClassMapping;
        } catch (IOException e) {
            throw new RuntimeException("加载预编译模板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从预编译的字节码中加载模板类。
     *
     * @param templateName 模板名
     * @return 模板 Class 对象
     * @throws ClassNotFoundException 如果预编译产物中未找到该模板
     */
    private Class<?> loadFromPrecompiled(String templateName) throws ClassNotFoundException {
        String className = precompiledTemplateMapping.get(templateName);
        if (className == null) {
            throw new ClassNotFoundException(
                    "预编译模板中未找到模板: " + templateName +
                    "。可用模板: " + precompiledTemplateMapping.keySet()
            );
        }
        // 字节码已在 loadPrecompiled() 时加载到 memoryClassLoader
        return memoryClassLoader.loadClass(className);
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

        BladeTemplate root = initInheritanceChain(template, templateName, context);
        return root.render();
    }

    /**
     * 初始化模板及其完整继承链（不限层级）。
     * <p>
     * 沿继承链自下而上逐层初始化。所有模板共享同一个 BladeContext：
     * <ul>
     *   <li>变量对全链可见；</li>
     *   <li>子模板先注册 section，父模板注册同名 section 时通过
     *       {@link BladeContext#extendSection} 完成 @parent 占位符替换（Laravel 语义）；</li>
     *   <li>返回继承链根部模板（最顶层布局），由其执行 render() 输出。</li>
     * </ul>
     *
     * @param template     子模板（已 resetContext 并注入变量）
     * @param templateName 子模板名（用于循环继承检测）
     * @param context      共享上下文
     * @return 继承链根部模板
     */
    private BladeTemplate initInheritanceChain(BladeTemplate template, String templateName, BladeContext context)
            throws IOException, ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, InstantiationException, IllegalAccessException {
        template.init();
        template.setInitialized(true);

        BladeTemplate current = template;
        java.util.Set<String> visited = new java.util.LinkedHashSet<>();
        visited.add(templateName);
        // 保存子模板 @extends 声明的直接父模板名；循环内为终止继承会将其清空，
        // 但对外（如 PJAX 兼容性判定）这是必要的布局元数据，须在方法结束时还原。
        String originalParent = context.getParentTemplate();
        String parentName = originalParent;
        while (parentName != null && !parentName.isEmpty()) {
            if (!visited.add(parentName)) {
                throw new IllegalStateException("模板继承出现循环: " + visited + " -> " + parentName);
            }
            BladeTemplate parent = loadTemplate(parentName);
            parent.setEngine(this);
            // 清除当前层的 parent 标记，由父模板 init() 决定是否继续向上继承
            context.setParentTemplate(null);
            // 父模板共享同一 context（变量 + section 合并）
            parent.resetContext(context);
            parent.init();
            parent.setInitialized(true);
            current = parent;
            parentName = context.getParentTemplate();
        }
        // 还原直接父模板名，供调用方（renderPjax 的布局兼容性判定）读取
        context.setParentTemplate(originalParent);
        return current;
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

        // Wire 局部渲染同样需要初始化完整继承链：
        // 1. resetContext 后 section renderer 已被清空，必须重新 init（不能依赖 isInitialized 守卫）；
        // 2. section 可能定义在父模板中，或经 @parent 与父模板合并，需与整页渲染语义一致。
        initInheritanceChain(template, templateName, context);

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

        initInheritanceChain(template, templateName, context);

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

        initInheritanceChain(template, templateName, context);

        return new ArrayList<>(context.getSectionRenderers().keySet());
    }

    // ===== PJAX 区域分析与渲染 =====

    /**
     * PJAX 区域元数据（编译期分析所得）。
     */
    public static class TemplateRegionMeta {
        /** 布局父模板名（@extends 指向的模板），可能为 null（无布局） */
        public String parentTemplate;
        /** 本模板（含继承链）实际输出的所有 @yield 区域名（有序） */
        public java.util.LinkedHashSet<String> yieldNames = new java.util.LinkedHashSet<>();
        /** 本模板（含继承链）注册的 @section 名（有序） */
        public java.util.LinkedHashSet<String> sectionNames = new java.util.LinkedHashSet<>();

        /** 派生出需要参与切换判定的全部区域名（yield ∪ 已注册 section，去重保序） */
        public java.util.List<String> regionNames() {
            java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
            all.addAll(yieldNames);
            all.addAll(sectionNames);
            return new java.util.ArrayList<>(all);
        }
    }

    /**
     * 取得模板的区域元数据（编译期分析所得）。未分析则触发一次分析。
     *
     * @param templateName 模板名
     * @return 区域元数据（至少包含空集合，不会为 null）
     */
    public TemplateRegionMeta getRegionMeta(String templateName) throws Exception {
        TemplateRegionMeta meta = regionMetaCache.get(templateName);
        if (meta != null) {
            return meta;
        }
        analyzeRegions(templateName, loadTemplate(templateName).getClass().getName());
        return regionMetaCache.getOrDefault(templateName, new TemplateRegionMeta());
    }

    /**
     * 编译期区域分析：初始化继承链并完整渲染一次（输出丢弃），
     * 记录布局父链 + @yield 区域名 + 注册 @section 名，存入 regionMetaCache。
     * <p>失败（如模板需请求级数据）仅缺失元数据，PJAX 退化为整页渲染，不影响正常功能。</p>
     */
    private void analyzeRegions(String templateName, String className) {
        try {
            Class<?> cls = memoryClassLoader.loadClass(className);
            BladeTemplate template = (BladeTemplate) cls.getDeclaredConstructor().newInstance();
            template.setEngine(this);
            template.resetContext();
            BladeContext context = template.getContext();
            BladeTemplate root = initInheritanceChain(template, templateName, context);
            // 触发完整渲染以记录 @yield 区域名（输出丢弃）
            java.io.StringWriter sink = new java.io.StringWriter();
            root.render(sink);
            TemplateRegionMeta meta = new TemplateRegionMeta();
            meta.parentTemplate = context.getParentTemplate();
            meta.yieldNames = new java.util.LinkedHashSet<>(context.getYieldedNames());
            meta.sectionNames = new java.util.LinkedHashSet<>(context.getSectionRenderers().keySet());
            regionMetaCache.put(templateName, meta);
        } catch (Exception e) {
            // 分析失败不阻断编译/渲染
        }
    }

    /**
     * 清除区域元数据缓存（与模板类缓存同步失效）。
     */
    public void clearRegionMeta() {
        regionMetaCache.clear();
    }

    /**
     * PJAX 渲染结果持有者。
     */
    public static class PjaxRenderResult {
        public String html;
        public TemplateRegionMeta meta;
        public String title;
    }

    /**
     * 以 PJAX 模式渲染模板：在每个 @yield 区域外包裹 pjax 分段标记，
     * 并附带编译期分析得到的区域元数据与页面标题。
     *
     * @param templateName 模板名
     * @param variables    模板变量
     * @return 渲染结果（含带标记的 HTML、区域元数据、标题文本）
     */
    public PjaxRenderResult renderPjax(String templateName, Map<String, Object> variables) throws Exception {
        BladeTemplate template = loadTemplate(templateName);
        template.setEngine(this);
        template.resetContext();
        BladeContext context = template.getContext();
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }
        context.setVariable(BladeTemplate.PJAX_MODE_KEY, true);
        BladeTemplate root = initInheritanceChain(template, templateName, context);
        String html = root.render();
        PjaxRenderResult result = new PjaxRenderResult();
        result.html = html;
        // 从本次实际渲染（已带入模板变量，必然成功）的上下文提取区域元数据。
        // 注意：不可依赖 getRegionMeta 的无变量预热渲染——当模板需要请求级数据时，
        // 预热渲染会抛异常被吞掉，导致 parentTemplate 等永远为空，进而 PJAX 兼容性
        // 判定（isCompatible）始终失败、永远退化为整页刷新。
        result.meta = captureRegionMeta(templateName, context);
        String title = extractPjaxRegion(html, "title");
        result.title = title != null ? title : "";
        return result;
    }

    /**
     * 从实际渲染后的上下文提取区域元数据，并回写 regionMetaCache。
     *
     * <p>与 {@link #analyzeRegions} 不同，这里使用的是已成功渲染（带模板变量）的
     * 上下文，因此 {@code parentTemplate} / @yield 区域名 / 已注册 @section 名都真实可靠。
     * 这正是 PJAX 兼容性判定（布局相同 + 区域集合相同）所必需的信息。</p>
     */
    private TemplateRegionMeta captureRegionMeta(String templateName, BladeContext context) {
        TemplateRegionMeta meta = new TemplateRegionMeta();
        meta.parentTemplate = context.getParentTemplate();
        meta.yieldNames = new java.util.LinkedHashSet<>(context.getYieldedNames());
        meta.sectionNames = new java.util.LinkedHashSet<>(context.getSectionRenderers().keySet());
        regionMetaCache.put(templateName, meta);
        return meta;
    }

    /**
     * 从带 pjax 标记的 HTML 中提取指定区域名的内容（不含标记本身）。
     *
     * @param html 带 pjax 标记的 HTML
     * @param name 区域名
     * @return 区域内容；不存在返回 null
     */
    public static String extractPjaxRegion(String html, String name) {
        if (html == null || name == null) {
            return null;
        }
        String start = BladeTemplate.PJAX_SECTION_START_PREFIX + name + "-->";
        String end = BladeTemplate.PJAX_SECTION_END_PREFIX + name + "-->";
        int s = html.indexOf(start);
        if (s < 0) {
            return null;
        }
        int contentStart = s + start.length();
        int e = html.indexOf(end, contentStart);
        if (e < 0) {
            return html.substring(contentStart);
        }
        return html.substring(contentStart, e);
    }

    /**
     * 扫描模板目录下所有模板名（用于 view:cache 全量预编译）。
     * <p>优先扫描文件系统 {@code resources/<templateDir>}，便于开发期 artisan 命令直接预热；
     * 找不到时回退到 classpath 资源遍历（适用于 fat-jar 等打包形态）。</p>
     *
     * @return 点分式模板名列表
     */
    public java.util.List<String> scanTemplateNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        java.io.File dir = new java.io.File("resources" + java.io.File.separator + templateDir);
        if (dir.isDirectory()) {
            collectTemplateFiles(dir, dir, names);
        } else {
            try {
                java.net.URL url = BladeEngine.class.getClassLoader().getResource(templateDir.replace(java.io.File.separator, "/"));
                if (url != null) {
                    java.nio.file.Path base = java.nio.file.Paths.get(url.toURI());
                    if (java.nio.file.Files.isDirectory(base)) {
                        collectTemplatePaths(base, base, names);
                    }
                }
            } catch (Exception ignored) {
                // classpath 遍历失败，返回已扫描结果
            }
        }
        return names;
    }

    private void collectTemplateFiles(java.io.File baseDir, java.io.File current, java.util.List<String> names) {
        java.io.File[] files = current.listFiles();
        if (files == null) {
            return;
        }
        String suffix = compiler.getSuffix();
        for (java.io.File f : files) {
            if (f.isDirectory()) {
                collectTemplateFiles(baseDir, f, names);
            } else if (f.getName().endsWith(suffix)) {
                // URI#getPath 统一使用 '/' 分隔，不能用 File.separator（Windows 上为 '\'）替换
                String rel = baseDir.toURI().relativize(f.toURI()).getPath();
                String name = rel.substring(0, rel.length() - suffix.length()).replace('/', '.');
                names.add(name);
            }
        }
    }

    private void collectTemplatePaths(java.nio.file.Path baseDir, java.nio.file.Path current, java.util.List<String> names) {
        String suffix = compiler.getSuffix();
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(current)) {
            stream.filter(p -> p.toString().endsWith(suffix) && java.nio.file.Files.isRegularFile(p)).forEach(p -> {
                String rel = baseDir.relativize(p).toString();
                String name = rel.substring(0, rel.length() - suffix.length())
                        .replace('\\', '.')
                        .replace('/', '.');
                names.add(name);
            });
        } catch (Exception ignored) {
            // 遍历异常忽略
        }
    }

    /**
     * 加载（编译+缓存）模板。
     * <p>
     * 流程：
     * <ol>
     *   <li>查一级缓存（ConcurrentHashMap），命中则直接返回 Class；</li>
     *   <li>预编译模式：从已加载的预编译字节码获取 Class（无需 JDK）；</li>
     *   <li>非预编译模式：查二级缓存（CacheStore），命中则加载字节码到 MemoryClassLoader；</li>
     *   <li>非预编译模式且缓存未命中时调用 {@link BladeCompiler#compile} 编译模板（需要 JDK）；</li>
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
                    if (precompiledMode) {
                        // 预编译模式：从已加载的字节码获取类
                        templateClass = loadFromPrecompiled(templateName);
                    } else {
                        // 非预编译模式：原有逻辑
                        // 2. 查二级缓存（CacheStore）
                        if (useCacheStore) {
                            templateClass = loadFromCacheStore(templateName);
                        }
                        // 3. 缓存未命中，编译模板
                        if (templateClass == null) {
                            templateClass = compileAndCache(templateName);
                        }
                    }
                    // 存入一级缓存
                    templateClassCache.put(templateName, templateClass);
                }
            }
        }

        // 4. 每次创建新的模板实例。
        // 注意：不能复用共享单例实例——BladeTemplate 的 context 是实例字段，
        // 并发请求（尤其是 Wire 局部更新与整页渲染同时进行）下共享实例会导致
        // context 互相覆盖，输出错乱。类已缓存，实例创建开销可忽略。
        BladeTemplate template = (BladeTemplate) templateClass.getDeclaredConstructor().newInstance();
        template.setEngine(this);
        templateInstanceCache.put(templateName, template);

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
        // 检查 JDK 是否可用
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        if (javaCompiler == null) {
            throw new IllegalStateException(
                    "无法获取Java编译器。运行时环境为JRE而非JDK。\n" +
                    "解决方案：\n" +
                    "1. 使用JDK运行\n" +
                    "2. 使用预编译模式：BladeEngine.fromPrecompiledPackage() 或 fromPrecompiledClasses()\n" +
                    "3. 运行 BladePrecompiler 预编译模板后部署"
            );
        }
        // 编译模板（读取文件 + 生成源码 + JavaC 编译）
        String className = compiler.compile(templateName);
        // 从 MemoryClassLoader 加载 Class
        Class<?> templateClass = memoryClassLoader.loadClass(className);
        // 编译期区域分析（PJAX）：记录布局父链 + @yield 区域名集合
        analyzeRegions(templateName, className);

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
        regionMetaCache.clear();
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
        regionMetaCache.remove(templateName);
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
