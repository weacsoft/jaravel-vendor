package com.weacsoft.jaravel.vendor.jblade;

import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Blade 模板引擎，负责编译和渲染 Blade 模板。
 * <p>
 * <b>缓存机制（全部 JVM 堆内存，不序列化、不落盘）</b>：
 * <ol>
 *   <li><b>一级缓存——模板类</b>：{@code ConcurrentHashMap<String, Class<?>>} 缓存编译后的
 *       {@code Class<?>} 对象，进程内有效，始终启用。</li>
 *   <li><b>内存字节码缓存</b>：{@code ConcurrentHashMap<String, byte[]>} 缓存模板源码→javac 编译
 *       后的字节码（模板名→byte[]），命中时直接 {@code defineClass} 载入，跳过源码生成与 javac
 *       编译。这是 {@code view:cache} 命令预热的主目标，也是运行时缓存的最终形态——模板源码
 *       （.blade.java）无需缓存，可现场编译；字节码才是值得缓存的对象。</li>
 *   <li><b>类名索引</b>：{@code ConcurrentHashMap<String, String>} 模板名→编译后的类全限定名，
 *       配合字节码缓存使用。</li>
 *   <li><b>可选外部 CacheStore</b>：通过 {@link CacheStore}（如 Redis）可选的跨进程共享，
 *       默认不启用。仅当显式配置外部 store 时才可能涉及序列化；默认 demo 中解析为内存 store。</li>
 * </ol>
 */
public class BladeEngine {
    /** 默认模板文件后缀，与 BladeCompiler.DEFAULT_SUFFIX 保持一致 */
    public static final String DEFAULT_SUFFIX = BladeCompiler.DEFAULT_SUFFIX;

    private final BladeCompiler compiler;
    /** 一级缓存：模板名 → 编译后的 Class 对象（始终启用） */
    private final Map<String, Class<?>> templateClassCache = new ConcurrentHashMap<>();
    /** 模板实例缓存：模板名 → BladeTemplate 实例 */
    private final Map<String, BladeTemplate> templateInstanceCache = new ConcurrentHashMap<>();
    /** 内存字节码缓存：模板名 → 编译后的字节码（view:cache 预热的主目标） */
    private final Map<String, byte[]> templateBytecodeCache = new ConcurrentHashMap<>();
    /** 类名索引：模板名 → 编译后的类全限定名（配合字节码缓存使用） */
    private final Map<String, String> templateClassNameCache = new ConcurrentHashMap<>();
    /** 可选外部 CacheStore：跨进程共享（默认不启用） */
    private final CacheStore cacheStore;
    /** 缓存键前缀 */
    private static final String CACHE_KEY_PREFIX = "jblade:template:";
    private final MemoryClassLoader memoryClassLoader;
    /** 是否启用外部 CacheStore（仅当显式配置非内存 store 时才为 true） */
    private final boolean useCacheStore;
    /** 模板目录 */
    private final String templateDir;

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
     * 取得模板缓存统计信息（供 ViewCache 统计）。
     * @return 内存字节码缓存中的模板数量
     */
    public int templateClassCacheSize() {
        return templateBytecodeCache.size();
    }

    /**
     * 判断模板是否存在（供 @includeIf 等指令使用）。
     */
    public boolean templateExists(String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            return false;
        }
        templateName = templateName.replace("'", "").replace("\"", "").replace("/", ".");
        if (templateClassCache.containsKey(templateName)) {
            return true;
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
        String html = WireAnchorRewriter.rewrite(root.render());
        // 自动注入收集到的 @assets(在 </head> 前) 和 @script(在 </body> 前)
        return injectCollectedAssetsAndScripts(html, context);
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
        String originalParent = context.getParentTemplate();
        String parentName = overrideParentIfNeeded(templateName, originalParent);
        while (parentName != null && !parentName.isEmpty()) {
            if (!visited.add(parentName)) {
                throw new IllegalStateException("模板继承出现循环: " + visited + " -> " + parentName);
            }
            BladeTemplate parent = loadTemplate(parentName);
            parent.setEngine(this);
            context.setParentTemplate(null);
            parent.resetContext(context);
            parent.init();
            parent.setInitialized(true);
            current = parent;
            parentName = overrideParentIfNeeded(parentName, context.getParentTemplate());
        }
        context.setParentTemplate(originalParent);
        return current;
    }

    /**
     * 在继承链每一步尝试读取 WireParentOverride 的父模板覆盖。
     * <p>
     * 通过反射调用(jblade 不依赖 wire 模块),找不到类或方法时忽略,保持原语义。
     */
    private String overrideParentIfNeeded(String templateName, String declaredParent) {
        try {
            Class<?> clazz = Class.forName(
                    "com.weacsoft.jaravel.vendor.wire.WireParentOverride");
            Object result = clazz.getMethod("get", String.class).invoke(null, templateName);
            if (result != null) {
                return (String) result;
            }
        } catch (Exception ignored) {
            // WireParentOverride 不存在或调用失败,忽略
        }
        return declaredParent;
    }

    public String render(String templateName) throws Exception {
        return render(templateName, null);
    }

    /**
     * 自动将收集到的 @assets 注入到 </head> 前,将 @script 注入到 </body> 前。
     * <p>
     * 注入策略:
     * <ul>
     *   <li>@assets 注入到 </head> 标签前(继承链按子→父顺序收集,子先注入)</li>
     *   <li>@script 注入到 </body> 标签前</li>
     * </ul>
     * 若无对应标签则追加到文档末尾。
     *
     * @param html    已渲染的 HTML
     * @param context 渲染后的上下文(含收集结果)
     * @return 注入后的 HTML
     */
    private String injectCollectedAssetsAndScripts(String html, BladeContext context) {
        if (html == null) {
            return html;
        }
        String assetsHtml = context.renderCollectedAssets();
        String scriptsHtml = context.renderCollectedScripts();
        if (assetsHtml.isEmpty() && scriptsHtml.isEmpty()) {
            return html;
        }
        String lowerHtml = html.toLowerCase();
        int headEnd = lowerHtml.lastIndexOf("</head>");
        int bodyEnd = lowerHtml.lastIndexOf("</body>");

        if (!assetsHtml.isEmpty() && headEnd >= 0) {
            html = html.substring(0, headEnd) + "\n" + assetsHtml + "\n" + html.substring(headEnd);
            // 重新定位 body 位置(因为前面插入了 assets)
            lowerHtml = html.toLowerCase();
            bodyEnd = lowerHtml.lastIndexOf("</body>");
        }
        if (!scriptsHtml.isEmpty() && bodyEnd >= 0) {
            html = html.substring(0, bodyEnd) + "\n" + scriptsHtml + "\n" + html.substring(bodyEnd);
        } else if (!scriptsHtml.isEmpty()) {
            html = html + "\n" + scriptsHtml;
        }
        if (!assetsHtml.isEmpty() && headEnd < 0) {
            html = html + "\n" + assetsHtml;
        }
        return html;
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
        return WireAnchorRewriter.rewrite(writer.toString());
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
                result.put(sectionName, WireAnchorRewriter.rewrite(writer.toString()));
            } else {
                String sectionContent = context.getSection(sectionName);
                result.put(sectionName, sectionContent != null ? WireAnchorRewriter.rewrite(sectionContent) : "");
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

    // ===== 扫描模板 =====

    /**
     * 扫描模板目录下所有模板名（用于 view:cache 全量预热）。
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
     *   <li>查内存字节码缓存（view:cache 预热成果），命中后直接 defineClass 载入；</li>
     *   <li>查外部 CacheStore（可选），命中则加载字节码到 MemoryClassLoader；</li>
     *   <li>缓存未命中时调用 {@link BladeCompiler#compile} 编译模板（需要 JDK），编译后写入内存字节码缓存；</li>
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
        templateName = templateName.replace("'", "").replace("\"", "").replace("/", ".");

        // 1. 查一级缓存（内存）
        Class<?> templateClass = templateClassCache.get(templateName);

        if (templateClass == null) {
            synchronized (this) {
                templateClass = templateClassCache.get(templateName);
                if (templateClass == null) {
                    // 2. 查内存字节码缓存（templateName → byte[]，view:cache 预热的主目标）
                    String cachedClassName = templateClassNameCache.get(templateName);
                    byte[] cachedBytecode = templateBytecodeCache.get(templateName);
                    if (cachedClassName != null && cachedBytecode != null) {
                        // 直接 defineClass 载入，跳过源码生成与 javac 编译
                        memoryClassLoader.getCompiledClasses().put(cachedClassName, cachedBytecode);
                        templateClass = memoryClassLoader.loadClass(cachedClassName);
                    }
                    // 3. 查外部 CacheStore（可选）
                    if (templateClass == null && useCacheStore) {
                        templateClass = safeLoadFromCacheStore(templateName);
                    }
                    // 4. 缓存未命中，编译模板（需要 JDK）
                    if (templateClass == null) {
                        templateClass = safeCompileAndCache(templateName);
                    }
                    if (templateClass == null) {
                        throw new RuntimeException("Failed to load template: " + templateName);
                    }
                }
                templateClassCache.put(templateName, templateClass);
            }
        }

        // 5. 创建或获取模板实例（按模板名缓存）
        final Class<?> finalTemplateClass = templateClass;
        return templateInstanceCache.computeIfAbsent(templateName, name -> {
            try {
                return (BladeTemplate) finalTemplateClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create template instance for: " + name, e);
            }
        });
    }

    /**
     * 从外部 CacheStore 加载模板字节码（异常安全版本）。
     */
    private Class<?> safeLoadFromCacheStore(String templateName) {
        try {
            String cacheKey = CACHE_KEY_PREFIX + templateName;
            Object cached = cacheStore.get(cacheKey);
            if (!(cached instanceof byte[])) {
                return null;
            }
            byte[] bytecode = (byte[]) cached;
            // 尝试从字节码中提取类名（简化：使用 templateName 推断类名）
            String className = compiler.getClassLoader().getCompiledClasses().keySet().stream()
                    .filter(k -> k.contains(templateName.replace(".", "_")))
                    .findFirst().orElse(null);
            if (className == null) {
                // 直接尝试加载，字节码中应包含完整类信息
                memoryClassLoader.getCompiledClasses().put("Template_" + templateName.replace(".", "_"), bytecode);
                return memoryClassLoader.loadClass("Template_" + templateName.replace(".", "_"));
            }
            memoryClassLoader.getCompiledClasses().put(className, bytecode);
            return memoryClassLoader.loadClass(className);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 编译模板并存入缓存（异常安全版本）。
     */
    private Class<?> safeCompileAndCache(String templateName) {
        try {
            // 编译模板（读取文件 + 生成源码 + JavaC 编译）
            String className = compiler.compile(templateName);
            // 从 MemoryClassLoader 加载 Class
            Class<?> templateClass = memoryClassLoader.loadClass(className);

            // 将字节码存入内存缓存（view:cache 预热的主目标）
            byte[] bytecode = memoryClassLoader.getCompiledClasses().get(className);
            if (bytecode != null) {
                templateClassNameCache.put(templateName, className);
                templateBytecodeCache.put(templateName, bytecode);
            }

            // 将字节码存入外部 CacheStore（可选）
            if (useCacheStore && bytecode != null) {
                try {
                    String cacheKey = CACHE_KEY_PREFIX + templateName;
                    cacheStore.put(cacheKey, bytecode, 0);
                } catch (Exception e) {
                    // 缓存写入失败，不影响功能
                }
            }

            return templateClass;
        } catch (Exception e) {
            System.err.println("BladeEngine: failed to compile template [" + templateName + "] — " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 清理模板缓存。
     * <p>
     * 清理以下缓存：
     * <ul>
     *   <li>templateClassCache：模板名 → Class 对象缓存（一级缓存）；</li>
     *   <li>templateBytecodeCache：模板名 → 字节码缓存（内存字节码缓存）；</li>
     *   <li>templateClassNameCache：模板名 → 类名缓存（类名索引）；</li>
     *   <li>templateInstanceCache：模板实例缓存。</li>
     * </ul>
     */
    public void clearCache() {
        templateClassCache.clear();
        templateBytecodeCache.clear();
        templateClassNameCache.clear();
        templateInstanceCache.clear();
        if (cacheStore != null) {
            try {
                cacheStore.flush();
            } catch (Exception e) {
                // 缓存清理失败，不影响功能
            }
        }
    }

    /**
     * 预填充预编译模板缓存（用于 JRE 环境，构建时预编译、运行时直接加载）。
     * <p>
     * 从预编译包中读取所有模板的字节码和类名，批量写入 bytecode cache 和 class name cache，
     * 并将字节码填入 MemoryClassLoader 的 classBytes map，使后续 loadTemplate() 直接命中缓存。
     *
     * @param bundle 预编译包（由 BladePrecompilerMain 生成）
     */
    public void populatePrecompiledBundle(PrecompiledTemplateLoader.PrecompiledBundle bundle) {
        // 注意：必须迭代 templateToClassMapping（模板名→类名），而不是 classBytecodes（类名→字节码）
        if (bundle == null || bundle.templateToClassMapping == null || bundle.templateToClassMapping.isEmpty()) {
            return;
        }
        Map<String, byte[]> classBytes = memoryClassLoader.getCompiledClasses();
        for (Map.Entry<String, String> templateEntry : bundle.templateToClassMapping.entrySet()) {
            String templateName = templateEntry.getKey();
            String className = templateEntry.getValue();
            byte[] bytecode = bundle.classBytecodes.get(className);
            if (bytecode != null) {
                templateBytecodeCache.put(templateName, bytecode);
                templateClassNameCache.put(templateName, className);
                // 直接写入 MemoryClassLoader，使 findClass() 能立即找到
                classBytes.put(className, bytecode);
            }
        }
    }
}
