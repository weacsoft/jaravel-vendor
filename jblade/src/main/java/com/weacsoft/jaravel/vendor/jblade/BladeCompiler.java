package com.weacsoft.jaravel.vendor.jblade;

import com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader;
import com.weacsoft.jaravel.vendor.utils.memory.MemoryFileManager;
import com.weacsoft.jaravel.vendor.utils.memory.SourceCodeJavaFileObject;
import org.springframework.core.io.ClassPathResource;

import javax.tools.*;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * jblade 模板编译器：将 Blade 模板编译为 Java 类源码，再经内存 javac 编译加载。
 * <p>
 * 支持的指令（语义对齐 Laravel Blade）：
 * <ul>
 *   <li>布局继承：@extends / @section（块与内联）/ @endsection / @stop / @show /
 *       @append / @overwrite / @parent / @yield / @hasSection / @sectionMissing —
 *       支持不限层级的多重继承</li>
 *   <li>流程控制：@if / @elseif / @else / @endif / @unless / @isset / @empty($x) /
 *       @switch 之外的 @foreach（含 $loop）/ @forelse / @empty / @for / @while /
 *       @continue / @break</li>
 *   <li>子视图：@include / @includeIf / @includeWhen / @component / @slot</li>
 *   <li>输出：{{ }}（HTML 转义）/ {!! !!}（原样）/ {{-- --}} 注释 / @{{ }} 与 @@ 转义 /
 *       @verbatim</li>
 *   <li>杂项：@php（内联与块）/ @csrf / @method / @json / @asset /
 *       @route（http 模块路由别名 → URL）/ @auth / @guest</li>
 *   <li>动态扩展：{@link BladeDirectives} 注册的条件指令与输出指令、
 *       {@link BladeFunctions} 注册的模板函数</li>
 * </ul>
 * 未注册的 @xxx（如 CSS 的 @media、@keyframes，JS/邮箱中的 @ 文本）原样输出，
 * 与 Laravel 行为一致。
 */
public class BladeCompiler {

    /** 默认模板文件后缀，使用 .blade.java 让常见 IDE 仍能识别为 Java 相关文件并提供提示 */
    public static final String DEFAULT_SUFFIX = ".blade.java";

    private final String templateDir;
    private final MemoryClassLoader classLoader;
    private final String suffix;

    public BladeCompiler(String templateDir, MemoryClassLoader classLoader) {
        this(templateDir, classLoader, DEFAULT_SUFFIX);
    }

    public BladeCompiler(String templateDir, MemoryClassLoader classLoader, String suffix) {
        this.templateDir = templateDir;
        this.classLoader = classLoader;
        this.suffix = (suffix != null && !suffix.isEmpty()) ? suffix : DEFAULT_SUFFIX;
    }

    /**
     * 获取当前模板文件后缀
     * @return 后缀字符串，如 ".blade.java"
     */
    public String getSuffix() {
        return suffix;
    }

    /**
     * 解析模板输入流，优先从文件系统 {@code ./resources/} 目录加载，回退到 ClassPath。
     *
     * @param templatePath 模板相对路径（如 {@code templates/layout.blade.java}）
     * @return 模板内容的输入流
     * @throws IOException 如果两个位置都找不到模板文件
     */
    private InputStream resolveTemplateStream(String templatePath) throws IOException {
        File file = new File("resources" + File.separator + templatePath);
        if (file.isFile()) {
            return new FileInputStream(file);
        }
        String cpPath = templatePath.replace(File.separator, "/");
        // 在 Spring Boot fat JAR 下，ClassPathResource 使用默认 ClassLoader 可能无法
        // 访问 BOOT-INF/classes 下的资源。使用线程上下文 ClassLoader（即 Spring
        // 的 LaunchedURLClassLoader），它能看到 fat JAR 内的所有资源。
        ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
        if (ctxLoader != null) {
            InputStream is = ctxLoader.getResourceAsStream(cpPath);
            if (is != null) return is;
        }
        ClassLoader appLoader = BladeCompiler.class.getClassLoader();
        if (appLoader != null) {
            InputStream is = appLoader.getResourceAsStream(cpPath);
            if (is != null) return is;
        }
        throw new IOException("Template not found on classpath: " + cpPath);
    }

    /**
     * 判断模板是否存在（文件系统 resources/ 或 ClassPath）。
     */
    public boolean templateExists(String templateName) {
        String templatePath = templateDir + File.separator
                + templateName.replace(".", File.separator) + suffix;
        File file = new File("resources" + File.separator + templatePath);
        if (file.isFile()) {
            return true;
        }
        String cpPath = templatePath.replace(File.separator, "/");
        ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
        if (ctxLoader != null && ctxLoader.getResource(cpPath) != null) {
            return true;
        }
        ClassLoader appLoader = BladeCompiler.class.getClassLoader();
        if (appLoader != null && appLoader.getResource(cpPath) != null) {
            return true;
        }
        return false;
    }

    /**
     * 编译一个模板文件。
     *
     * @param templateName 模板名（点分路径，如 "layouts.mdui.main"）
     * @return 编译后的类全限定名
     */
    public String compile(String templateName) throws IOException {
        String templatePath = templateDir + File.separator + templateName.replace(".", File.separator) + suffix;
        InputStream resource = resolveTemplateStream(templatePath);
        String content;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, java.nio.charset.StandardCharsets.UTF_8))) {
            content = reader.lines().collect(Collectors.joining("\n"));
        }
        return compileSource(templateName, content);
    }

    /**
     * 编译给定的模板内容（不读取文件，直接编译源码）。
     *
     * @param templateName 模板名（用于生成类名，如 "welcome"、"docs.index"）
     * @param content      模板文件内容
     * @return 编译后的类全限定名
     * @throws IOException 如果源代码为空
     */
    public String compileSource(String templateName, String content) throws IOException {
        String className = generateClassName(templateName);
        String sourceCode = generateJavaCode(className, templateName, content);
        if (sourceCode.isEmpty()) {
            throw new IOException("源代码不能为空");
        }
        String codeWithoutCommentsAndStrings = removeCommentsAndStrings(sourceCode);
        String packageName = extractPackageName(codeWithoutCommentsAndStrings);
        String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("无法获取Java编译器，请确保使用JDK而非JRE运行程序");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (MemoryFileManager fileManager = new MemoryFileManager(compiler.getStandardFileManager(diagnostics, null, null))) {
            List<JavaFileObject> compilationUnits = new ArrayList<>();
            compilationUnits.add(new SourceCodeJavaFileObject(fullClassName, sourceCode));
            // 必须显式传入 -classpath：以可执行 fat-jar（java -jar app.jar）方式运行时，
            // 依赖类位于 BOOT-INF/lib/*.jar 这类「嵌套 jar」中，javac 的标准文件管理器
            // 读不到它们，System.getProperty("java.class.path") 也只包含最外层 jar，
            // 会导致「程序包 com.weacsoft.jaravel.vendor.jblade 不存在」之类的编译错误。
            List<String> options = new ArrayList<>();
            String cp = resolveCompilerClasspath();
            if (cp != null && !cp.isEmpty()) {
                options.add("-classpath");
                options.add(cp);
            }
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics,
                    options.isEmpty() ? null : options, null, compilationUnits);
            Boolean success = task.call();
            if (success == null || !success) {
                StringBuilder errorMsg = new StringBuilder("模板 [" + templateName + "] 编译错误: ");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    errorMsg.append(String.format("%n第%d行: %s", diagnostic.getLineNumber(), diagnostic.getMessage(null)));
                }
                errorMsg.append("%n===== 生成的 Java 源码 =====%n").append(sourceCode);
                throw new RuntimeException(errorMsg.toString());
            }
            for (String name : fileManager.getGeneratedClassNames()) {
                classLoader.getCompiledClasses().put(name, fileManager.getGeneratedClass(name));
            }
        }
        return fullClassName;
    }

    /**
     * 获取关联的内存类加载器。
     */
    public MemoryClassLoader getClassLoader() {
        return classLoader;
    }

    /* =====================================================================
     * 运行时编译 classpath 解析
     * ---------------------------------------------------------------------
     * 直接委托给通用工具 RuntimeClasspath（位于 jaravel-vendor/utils），
     * 该工具兼容 Spring Boot 可执行 fat-jar（java -jar）场景：在检测到
     * fat-jar 时会把 BOOT-INF/lib 与 BOOT-INF/classes 展开到临时目录后
     * 拼入 classpath。jblade 自身不再重复实现 FAT-JAR 探测逻辑。
     * ===================================================================== */

    /**
     * 手动指定编译 classpath 的系统属性（与 {@code RuntimeClasspath} 保持一致）。
     */
    public static final String CLASSPATH_PROPERTY =
            com.weacsoft.jaravel.vendor.utils.runtime.RuntimeClasspath.CLASSPATH_PROPERTY;

    /**
     * 解析供 javac 使用的 classpath（兼容 Spring Boot fat-jar）。
     *
     * @return 以 {@link File#pathSeparator} 分隔的 classpath，可能为空字符串
     */
    private static String resolveCompilerClasspath() {
        return com.weacsoft.jaravel.vendor.utils.runtime.RuntimeClasspath.resolve();
    }

    private String generateClassName(String templateName) {
        return "Blade_" + templateName.replace(File.separator, "_").replace("/", "_")
                .replace("\\", "_").replace(".", "_").replace("-", "_");
    }

    /* =====================================================================
     * 词法分析：模板 → Token 序列
     * ===================================================================== */

    private static final int N_TEXT = 0;
    private static final int N_ECHO = 1;      // {{ }}
    private static final int N_RAW_ECHO = 2;  // {!! !!}
    private static final int N_DIRECTIVE = 3; // @xxx(...)

    private static class Tok {
        final int type;
        final String value;   // 文本内容 / 表达式 / 指令名
        final String args;    // 指令参数（无括号为 null）
        final String raw;     // 指令原始文本（未知指令回退输出用）

        Tok(int type, String value, String args, String raw) {
            this.type = type;
            this.value = value;
            this.args = args;
            this.raw = raw;
        }
    }

    /** 内置指令名集合（编译期识别） */
    private static final Set<String> KNOWN_DIRECTIVES = new HashSet<>(Arrays.asList(
        "extends", "section", "endsection", "stop", "show", "append", "overwrite", "parent",
        "yield", "hasSection", "sectionMissing",
        "if", "elseif", "else", "endif", "unless", "endunless", "isset", "endisset",
        "empty", "endempty",
        "foreach", "endforeach", "forelse", "endforelse", "for", "endfor",
        "while", "endwhile", "continue", "break",
        "php", "endphp",
        "include", "includeIf", "includeWhen", "includeUnless",
        "csrf", "method", "json", "route", "asset",
        "component", "endcomponent", "slot", "endslot",
        "auth", "endauth", "guest", "endguest",
        "verbatim", "endverbatim",
        "script", "endscript", "assets", "endassets"
));

    /**
     * 判断指令名是否可识别（内置 + 动态注册的条件/输出指令及其 else/end 变体）。
     */
    private boolean isKnownDirective(String name) {
        if (KNOWN_DIRECTIVES.contains(name)) {
            return true;
        }
        if (BladeDirectives.hasCondition(name) || BladeDirectives.hasDirective(name)) {
            return true;
        }
        if (name.startsWith("end") && BladeDirectives.hasCondition(name.substring(3))) {
            return true;
        }
        if (name.startsWith("else") && name.length() > 4 && BladeDirectives.hasCondition(name.substring(4))) {
            return true;
        }
        return false;
    }

    private List<Tok> tokenize(String content) {
        List<Tok> toks = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int i = 0;
        int n = content.length();
        while (i < n) {
            // 注释 {{-- --}}
            if (content.startsWith("{{--", i)) {
                int end = content.indexOf("--}}", i + 4);
                i = end < 0 ? n : end + 4;
                continue;
            }
            // @verbatim ... @endverbatim → 原样文本
            if (content.startsWith("@verbatim", i)) {
                int end = content.indexOf("@endverbatim", i + 9);
                String body = end < 0 ? content.substring(i + 9) : content.substring(i + 9, end);
                // 去掉首尾的一个换行（Laravel 行为近似）
                if (body.startsWith("\n")) {
                    body = body.substring(1);
                }
                text.append(body);
                i = end < 0 ? n : end + "@endverbatim".length();
                continue;
            }
            // @@xxx → 输出 @xxx
            if (content.startsWith("@@", i)) {
                text.append('@');
                i += 2;
                continue;
            }
            // @{{ ... }} → 原样输出 {{ ... }}
            if (content.startsWith("@{{", i)) {
                int end = content.indexOf("}}", i + 3);
                if (end >= 0) {
                    text.append(content, i + 1, end + 2);
                    i = end + 2;
                } else {
                    text.append(content.charAt(i));
                    i++;
                }
                continue;
            }
            // @{!! ... !!} → 原样输出
            if (content.startsWith("@{!!", i)) {
                int end = content.indexOf("!!}", i + 4);
                if (end >= 0) {
                    text.append(content, i + 1, end + 3);
                    i = end + 3;
                } else {
                    text.append(content.charAt(i));
                    i++;
                }
                continue;
            }
            // {!! ... !!}
            if (content.startsWith("{!!", i)) {
                int end = content.indexOf("!!}", i + 3);
                if (end >= 0) {
                    flushText(toks, text);
                    toks.add(new Tok(N_RAW_ECHO, content.substring(i + 3, end).trim(), null, null));
                    i = end + 3;
                    continue;
                }
            }
            // {{ ... }}
            if (content.startsWith("{{", i)) {
                int end = content.indexOf("}}", i + 2);
                if (end >= 0) {
                    flushText(toks, text);
                    toks.add(new Tok(N_ECHO, content.substring(i + 2, end).trim(), null, null));
                    i = end + 2;
                    continue;
                }
            }
            // @directive
            char c = content.charAt(i);
            if (c == '@' && i + 1 < n && Character.isLetter(content.charAt(i + 1))) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(content.charAt(j)) || content.charAt(j) == '_')) {
                    j++;
                }
                String name = content.substring(i + 1, j);
                // 括号参数（允许名字与括号间的空格/制表符）
                int k = j;
                while (k < n && (content.charAt(k) == ' ' || content.charAt(k) == '\t')) {
                    k++;
                }
                String args = null;
                int consumeEnd = j;
                if (k < n && content.charAt(k) == '(') {
                    int close = findMatchingParen(content, k + 1);
                    if (close > 0) {
                        args = content.substring(k + 1, close);
                        consumeEnd = close + 1;
                    }
                }
                if (isKnownDirective(name)) {
                    // @php 块形式（无参数）：捕获至 @endphp
                    if ("php".equals(name) && args == null) {
                        int end = content.indexOf("@endphp", consumeEnd);
                        String body = end < 0 ? content.substring(consumeEnd) : content.substring(consumeEnd, end);
                        flushText(toks, text);
                        toks.add(new Tok(N_DIRECTIVE, "phpblock", body, null));
                        i = end < 0 ? n : end + "@endphp".length();
                        continue;
                    }
                    flushText(toks, text);
                    toks.add(new Tok(N_DIRECTIVE, name, args,
                            content.substring(i, consumeEnd)));
                    i = consumeEnd;
                    continue;
                }
                // 未知指令：原样输出（CSS @media、邮箱、Vue @click 等）
                text.append(content, i, j);
                i = j;
                continue;
            }
            text.append(c);
            i++;
        }
        flushText(toks, text);
        return toks;
    }

    private void flushText(List<Tok> toks, StringBuilder text) {
        if (text.length() > 0) {
            toks.add(new Tok(N_TEXT, text.toString(), null, null));
            text.setLength(0);
        }
    }

    /**
     * 从 start（开括号之后）找到匹配的闭括号位置（引号感知）。
     */
    private int findMatchingParen(String expr, int start) {
        int depth = 1;
        char quote = 0;
        for (int i = start; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /* =====================================================================
     * 代码生成
     * ===================================================================== */

    /** 输出目标（render 主体 / section 体 / 组件默认插槽 / 具名插槽） */
    private static class Emitter {
        final String kind;        // render | section | component | slot
        final String name;        // section/slot 名
        final String writerVar;
        final StringBuilder code = new StringBuilder();
        String sectionMode = "extend"; // extend | append | overwrite
        // component 专用
        String slotsVar;
        String dataExpr;
        String componentName;
        String parentWriterVar;

        Emitter(String kind, String name, String writerVar) {
            this.kind = kind;
            this.name = name;
            this.writerVar = writerVar;
        }
    }

    /** 控制流栈帧 */
    private static class Frame {
        final String type;    // if | unless | isset | empty | foreach | forelse | for | while | auth | guest | custom | hasSection
        String closing;       // 关闭时输出的代码
        boolean forelseHasEmpty;

        Frame(String type, String closing) {
            this.type = type;
            this.closing = closing;
        }
    }

    private int varCounter;

    private String nextVar(String prefix) {
        return "__" + prefix + (varCounter++);
    }

    private String generateJavaCode(String className, String templateName, String content) {
        varCounter = 0;
        List<Tok> toks = tokenize(content);

        StringBuilder initCode = new StringBuilder();
        Emitter renderEmitter = new Emitter("render", null, "writer");
        Deque<Emitter> emitters = new ArrayDeque<>();
        emitters.push(renderEmitter);
        Deque<Frame> frames = new ArrayDeque<>();

        for (Tok tok : toks) {
            Emitter em = emitters.peek();
            switch (tok.type) {
                case N_TEXT:
                    emitText(em, tok.value);
                    break;
                case N_ECHO:
                    if (!tok.value.isEmpty()) {
                        // csrf_field() 返回 HTML（隐藏 input），必须原样输出而非 HTML 转义，
                        // 否则 {{ csrf_field() }} 会把 <input...> 当成文本显示。
                        boolean rawOut = tok.value.trim().startsWith("csrf_field(");
                        PhpExpressionTranslator.Expr e = translateExpr(tok.value, templateName);
                        if (rawOut) {
                            em.code.append("        echoRaw(").append(em.writerVar).append(", ")
                                    .append(e.asObject()).append(");\n");
                        } else {
                            em.code.append("        echo(").append(em.writerVar).append(", ")
                                    .append(e.asObject()).append(");\n");
                        }
                    }
                    break;
                case N_RAW_ECHO:
                    if (!tok.value.isEmpty()) {
                        PhpExpressionTranslator.Expr e = translateExpr(tok.value, templateName);
                        em.code.append("        echoRaw(").append(em.writerVar).append(", ")
                                .append(e.asObject()).append(");\n");
                    }
                    break;
                case N_DIRECTIVE:
                    handleDirective(tok, emitters, frames, initCode, renderEmitter, templateName);
                    break;
                default:
                    break;
            }
        }

        if (emitters.size() != 1) {
            Emitter unclosed = emitters.peek();
            throw new IllegalStateException("模板 [" + templateName + "] 存在未闭合的 @"
                    + unclosed.kind + (unclosed.name != null ? "('" + unclosed.name + "')" : ""));
        }
        if (!frames.isEmpty()) {
            throw new IllegalStateException("模板 [" + templateName + "] 存在未闭合的控制指令 @" + frames.peek().type);
        }

        StringBuilder javaCode = new StringBuilder();
        javaCode.append("import com.weacsoft.jaravel.vendor.jblade.*;\n");
        javaCode.append("import java.io.*;\n");
        javaCode.append("import java.util.*;\n");
        javaCode.append("import java.util.function.*;\n\n");
        javaCode.append("public class ").append(className).append(" extends BladeTemplate {\n\n");

        javaCode.append("    @Override\n");
        javaCode.append("    public void init() {\n");
        javaCode.append("        try {\n");
        javaCode.append("            BladeContext ctx = getContext();\n");
        javaCode.append(indent(initCode.toString(), "        "));
        javaCode.append("        } catch (Exception e) {\n");
        javaCode.append("            throw new RuntimeException(\"模板 init 失败: ")
                .append(escapeJava(templateName)).append("\", e);\n");
        javaCode.append("        }\n");
        javaCode.append("    }\n\n");

        javaCode.append("    @Override\n");
        javaCode.append("    public void render(Writer writer) throws Exception {\n");
        javaCode.append("        BladeContext ctx = getContext();\n");
        javaCode.append(renderEmitter.code);
        javaCode.append("    }\n");
        javaCode.append("}\n");
        return javaCode.toString();
    }

    private PhpExpressionTranslator.Expr translateExpr(String phpExpr, String templateName) {
        try {
            return PhpExpressionTranslator.translate(phpExpr);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("模板 [" + templateName + "] 表达式编译失败: " + phpExpr, ex);
        }
    }

    private void emitText(Emitter em, String text) {
        if (text.isEmpty()) {
            return;
        }
        // 分块输出，避免超长字符串常量
        int chunk = 4096;
        for (int off = 0; off < text.length(); off += chunk) {
            String part = text.substring(off, Math.min(text.length(), off + chunk));
            em.code.append("        write(").append(em.writerVar).append(", \"")
                    .append(escapeJava(part)).append("\");\n");
        }
    }

    /**
     * 必须携带 (...) 参数才有意义的指令。
     * 当模板文本中出现不带括号的同名词（如说明文案 "wire:click + @foreach"），
     * 不应被当作指令编译，而是按纯文本原样输出。
     */
    private static final Set<String> ARGS_REQUIRED_DIRECTIVES = new HashSet<>(Arrays.asList(
            "extends", "section", "yield", "hasSection", "sectionMissing",
            "if", "elseif", "unless", "isset",
            "foreach", "forelse", "for", "while",
            "include", "includeIf", "includeWhen", "includeUnless",
            "component", "slot", "json", "method", "route", "asset"
    ));

    private void handleDirective(Tok tok, Deque<Emitter> emitters, Deque<Frame> frames,
                                 StringBuilder initCode, Emitter renderEmitter, String templateName) {
        Emitter em = emitters.peek();
        String name = tok.value;
        String args = tok.args;
        StringBuilder code = em.code;

        // 必带参数的指令若未跟 (...)，视为普通文本（如文案中的 "@foreach"）
        if (args == null && ARGS_REQUIRED_DIRECTIVES.contains(name)) {
            emitText(em, "@" + name);
            return;
        }

        switch (name) {
            /* ---------- 布局继承 ---------- */
            case "extends": {
                String tpl = literalArg(args, templateName, "@extends");
                initCode.append("        ctx.setParentTemplate(\"").append(escapeJava(tpl)).append("\");\n");
                return;
            }
            case "section": {
                List<String> parts = PhpExpressionTranslator.splitTopLevel(args);
                String sectionName = literalArg(parts.get(0), templateName, "@section");
                if (parts.size() >= 2) {
                    // 内联 section：@section('title', 'xxx')
                    PhpExpressionTranslator.Expr valueExpr = translateExpr(parts.get(1), templateName);
                    initCode.append("        registerSection(\"").append(escapeJava(sectionName))
                            .append("\", ").append(valueExpr.asString()).append(");\n");
                    return;
                }
                String swVar = nextVar("sw");
                Emitter sectionEmitter = new Emitter("section", sectionName, swVar);
                emitters.push(sectionEmitter);
                return;
            }
            case "endsection":
            case "stop":
            case "append":
            case "overwrite":
            case "show": {
                if (!"section".equals(em.kind)) {
                    throw new IllegalStateException("模板 [" + templateName + "] @" + name + " 没有对应的 @section");
                }
                emitters.pop();
                String registerMethod = "registerSection";
                if ("append".equals(name)) {
                    registerMethod = "registerSectionAppend";
                } else if ("overwrite".equals(name)) {
                    registerMethod = "registerSectionOverwrite";
                }
                initCode.append("        {\n");
                initCode.append("            final java.io.StringWriter ").append(em.writerVar)
                        .append(" = new java.io.StringWriter();\n");
                initCode.append(indent(em.code.toString(), "    "));
                initCode.append("            ").append(registerMethod).append("(\"")
                        .append(escapeJava(em.name)).append("\", ").append(em.writerVar).append(".toString());\n");
                initCode.append("        }\n");
                if ("show".equals(name)) {
                    // @show：在当前位置输出该 section（含子模板覆盖与 @parent 合并）
                    emitters.peek().code.append("        yieldSection(").append(emitters.peek().writerVar)
                            .append(", \"").append(escapeJava(em.name)).append("\", null);\n");
                }
                return;
            }
            case "parent": {
                if (!"section".equals(em.kind)) {
                    throw new IllegalStateException("模板 [" + templateName + "] @parent 只能出现在 @section 内");
                }
                code.append("        write(").append(em.writerVar).append(", BladeContext.PARENT_PLACEHOLDER);\n");
                return;
            }
            case "yield": {
                List<String> parts = PhpExpressionTranslator.splitTopLevel(args);
                PhpExpressionTranslator.Expr nameExpr = translateExpr(parts.get(0), templateName);
                String defaultCode = "null";
                if (parts.size() >= 2) {
                    defaultCode = translateExpr(parts.get(1), templateName).asObject();
                }
                code.append("        yieldSection(").append(em.writerVar).append(", ")
                        .append(nameExpr.asString()).append(", ").append(defaultCode).append(");\n");
                return;
            }
            case "hasSection": {
                PhpExpressionTranslator.Expr nameExpr = translateExpr(args, templateName);
                code.append("        if (hasSection(").append(nameExpr.asString()).append(")) {\n");
                frames.push(new Frame("if", "        }\n"));
                return;
            }
            case "sectionMissing": {
                PhpExpressionTranslator.Expr nameExpr = translateExpr(args, templateName);
                code.append("        if (sectionMissing(").append(nameExpr.asString()).append(")) {\n");
                frames.push(new Frame("if", "        }\n"));
                return;
            }

            /* ---------- 条件 ---------- */
            case "if": {
                code.append("        if (").append(translateExpr(args, templateName).asBoolean()).append(") {\n");
                frames.push(new Frame("if", "        }\n"));
                return;
            }
            case "elseif": {
                code.append("        } else if (").append(translateExpr(args, templateName).asBoolean()).append(") {\n");
                return;
            }
            case "else": {
                code.append("        } else {\n");
                return;
            }
            case "endif":
            case "endunless":
            case "endisset":
            case "endauth":
            case "endguest": {
                popFrame(frames, code, templateName, name);
                return;
            }
            case "unless": {
                code.append("        if (!(").append(translateExpr(args, templateName).asBoolean()).append(")) {\n");
                frames.push(new Frame("unless", "        }\n"));
                return;
            }
            case "isset": {
                List<String> parts = PhpExpressionTranslator.splitTopLevel(args);
                StringBuilder cond = new StringBuilder();
                for (int i = 0; i < parts.size(); i++) {
                    if (i > 0) {
                        cond.append(" && ");
                    }
                    cond.append("isset(").append(translateExpr(parts.get(i), templateName).asObject()).append(")");
                }
                code.append("        if (").append(cond).append(") {\n");
                frames.push(new Frame("isset", "        }\n"));
                return;
            }
            case "empty": {
                if (args == null || args.trim().isEmpty()) {
                    // @forelse 的空分支
                    Frame f = findFrame(frames, "forelse");
                    if (f != null && !f.forelseHasEmpty) {
                        f.forelseHasEmpty = true;
                        code.append(f.closing);      // 关闭 for 循环 + popLoop + if 分支
                        code.append("        } else {\n");
                        f.closing = "        }\n        }\n"; // 关闭 else + 外层块
                        return;
                    }
                    // 无对应 forelse：按文本输出
                    emitText(em, "@empty");
                    return;
                }
                code.append("        if (empty(").append(translateExpr(args, templateName).asObject()).append(")) {\n");
                frames.push(new Frame("empty", "        }\n"));
                return;
            }
            case "endempty": {
                popFrame(frames, code, templateName, name);
                return;
            }
            case "auth": {
                code.append("        if (toBoolean(BladeFunctions.callOrDefault(\"auth_check\", Boolean.FALSE))) {\n");
                frames.push(new Frame("auth", "        }\n"));
                return;
            }
            case "guest": {
                code.append("        if (!toBoolean(BladeFunctions.callOrDefault(\"auth_check\", Boolean.FALSE))) {\n");
                frames.push(new Frame("guest", "        }\n"));
                return;
            }

            /* ---------- 循环 ---------- */
            case "foreach": {
                ForeachParts fp = parseForeach(args, templateName);
                String pairsVar = nextVar("it");
                String loopVar = nextVar("loop");
                String idxVar = nextVar("i");
                String entryVar = nextVar("e");
                code.append("        {\n");
                code.append("        java.util.List<Object[]> ").append(pairsVar)
                        .append(" = toPairs(").append(fp.collection).append(");\n");
                code.append("        LoopHelper ").append(loopVar)
                        .append(" = getContext().pushLoop(").append(pairsVar).append(".size());\n");
                code.append("        int ").append(idxVar).append(" = 0;\n");
                code.append("        for (Object[] ").append(entryVar).append(" : ").append(pairsVar).append(") {\n");
                code.append("        ").append(loopVar).append(".advance(").append(idxVar).append("++);\n");
                if (fp.keyVar != null) {
                    code.append("        setVar(\"").append(fp.keyVar).append("\", ").append(entryVar).append("[0]);\n");
                }
                code.append("        setVar(\"").append(fp.valueVar).append("\", ").append(entryVar).append("[1]);\n");
                frames.push(new Frame("foreach", "        }\n        getContext().popLoop();\n        }\n"));
                return;
            }
            case "endforeach": {
                popFrame(frames, code, templateName, name);
                return;
            }
            case "forelse": {
                ForeachParts fp = parseForeach(args, templateName);
                String pairsVar = nextVar("it");
                String loopVar = nextVar("loop");
                String idxVar = nextVar("i");
                String entryVar = nextVar("e");
                code.append("        {\n");
                code.append("        java.util.List<Object[]> ").append(pairsVar)
                        .append(" = toPairs(").append(fp.collection).append(");\n");
                code.append("        if (!").append(pairsVar).append(".isEmpty()) {\n");
                code.append("        LoopHelper ").append(loopVar)
                        .append(" = getContext().pushLoop(").append(pairsVar).append(".size());\n");
                code.append("        int ").append(idxVar).append(" = 0;\n");
                code.append("        for (Object[] ").append(entryVar).append(" : ").append(pairsVar).append(") {\n");
                code.append("        ").append(loopVar).append(".advance(").append(idxVar).append("++);\n");
                if (fp.keyVar != null) {
                    code.append("        setVar(\"").append(fp.keyVar).append("\", ").append(entryVar).append("[0]);\n");
                }
                code.append("        setVar(\"").append(fp.valueVar).append("\", ").append(entryVar).append("[1]);\n");
                Frame f = new Frame("forelse",
                        "        }\n        getContext().popLoop();\n");
                frames.push(f);
                return;
            }
            case "endforelse": {
                Frame f = frames.pop();
                if (!"forelse".equals(f.type)) {
                    throw new IllegalStateException("模板 [" + templateName + "] @endforelse 与 @" + f.type + " 不匹配");
                }
                if (f.forelseHasEmpty) {
                    code.append(f.closing);
                } else {
                    code.append(f.closing);   // 关闭 for + popLoop
                    code.append("        }\n");  // 关闭 if
                    code.append("        }\n");  // 关闭外层块
                }
                return;
            }
            case "for": {
                String[] parts = splitForParts(args);
                if (parts.length != 3) {
                    throw new IllegalStateException("模板 [" + templateName + "] @for 参数格式错误: " + args);
                }
                String init = parts[0].trim().isEmpty() ? "" : translateExpr(parts[0], templateName).code;
                String cond = parts[1].trim().isEmpty() ? "true" : translateExpr(parts[1], templateName).asBoolean();
                String update = parts[2].trim().isEmpty() ? "" : translateExpr(parts[2], templateName).code;
                code.append("        for (").append(init).append("; ").append(cond).append("; ")
                        .append(update).append(") {\n");
                frames.push(new Frame("for", "        }\n"));
                return;
            }
            case "endfor": {
                popFrame(frames, code, templateName, name);
                return;
            }
            case "while": {
                code.append("        while (").append(translateExpr(args, templateName).asBoolean()).append(") {\n");
                frames.push(new Frame("while", "        }\n"));
                return;
            }
            case "endwhile": {
                popFrame(frames, code, templateName, name);
                return;
            }
            case "continue": {
                if (args != null && !args.trim().isEmpty()) {
                    code.append("        if (").append(translateExpr(args, templateName).asBoolean())
                            .append(") { continue; }\n");
                } else {
                    code.append("        if (true) { continue; }\n");
                }
                return;
            }
            case "break": {
                if (args != null && !args.trim().isEmpty()) {
                    code.append("        if (").append(translateExpr(args, templateName).asBoolean())
                            .append(") { break; }\n");
                } else {
                    code.append("        if (true) { break; }\n");
                }
                return;
            }

            /* ---------- PHP 代码 ---------- */
            case "php": {
                // 内联形式 @php($x = 1)
                emitPhpStatements(code, args, templateName);
                return;
            }
            case "phpblock": {
                emitPhpStatements(code, args, templateName);
                return;
            }
            case "endphp":
                return; // 块形式在 tokenizer 已整体处理

            /* ---------- 子视图 ---------- */
            case "include":
            case "includeIf": {
                List<String> parts = PhpExpressionTranslator.splitTopLevel(args);
                PhpExpressionTranslator.Expr nameExpr = translateExpr(parts.get(0), templateName);
                String dataCode = "null";
                if (parts.size() >= 2) {
                    dataCode = "(java.util.Map<String,Object>)(Object)("
                            + translateExpr(parts.get(1), templateName).asObject() + ")";
                }
                String method = "include".equals(name) ? "includeTemplate" : "includeTemplateIf";
                code.append("        ").append(method).append("(").append(em.writerVar).append(", ")
                        .append(nameExpr.asString()).append(", ").append(dataCode).append(");\n");
                return;
            }
            case "includeWhen":
            case "includeUnless": {
                List<String> parts = PhpExpressionTranslator.splitTopLevel(args);
                String cond = translateExpr(parts.get(0), templateName).asBoolean();
                if ("includeUnless".equals(name)) {
                    cond = "!(" + cond + ")";
                }
                PhpExpressionTranslator.Expr nameExpr = translateExpr(parts.get(1), templateName);
                String dataCode = "null";
                if (parts.size() >= 3) {
                    dataCode = "(java.util.Map<String,Object>)(Object)("
                            + translateExpr(parts.get(2), templateName).asObject() + ")";
                }
                code.append("        if (").append(cond).append(") { includeTemplate(")
                        .append(em.writerVar).append(", ").append(nameExpr.asString()).append(", ")
                        .append(dataCode).append("); }\n");
                return;
            }

            /* ---------- 组件与插槽 ---------- */
            case "component": {
                List<String> parts = PhpExpressionTranslator.splitTopLevel(args);
                String componentName = literalArg(parts.get(0), templateName, "@component");
                String slotsVar = nextVar("slots");
                String defVar = nextVar("def");
                code.append("        {\n");
                code.append("        java.util.Map<String,String> ").append(slotsVar)
                        .append(" = new java.util.LinkedHashMap<>();\n");
                code.append("        java.io.StringWriter ").append(defVar)
                        .append(" = new java.io.StringWriter();\n");
                Emitter comp = new Emitter("component", componentName, defVar);
                comp.slotsVar = slotsVar;
                comp.componentName = componentName;
                comp.parentWriterVar = em.writerVar;
                comp.dataExpr = parts.size() >= 2
                        ? "(java.util.Map<String,Object>)(Object)("
                            + translateExpr(parts.get(1), templateName).asObject() + ")"
                        : "new java.util.HashMap<String,Object>()";
                emitters.push(comp);
                return;
            }
            case "endcomponent": {
                if (!"component".equals(em.kind)) {
                    throw new IllegalStateException("模板 [" + templateName + "] @endcomponent 没有对应的 @component");
                }
                emitters.pop();
                Emitter outer = emitters.peek();
                outer.code.append(em.code);
                outer.code.append("        ").append(em.slotsVar).append(".put(\"default\", ")
                        .append(em.writerVar).append(".toString());\n");
                outer.code.append("        renderComponent(").append(em.parentWriterVar).append(", \"")
                        .append(escapeJava(em.componentName)).append("\", ").append(em.dataExpr)
                        .append(", ").append(em.slotsVar).append(");\n");
                outer.code.append("        }\n");
                return;
            }
            case "slot": {
                if (!"component".equals(em.kind)) {
                    throw new IllegalStateException("模板 [" + templateName + "] @slot 只能出现在 @component 内");
                }
                String slotName = literalArg(PhpExpressionTranslator.splitTopLevel(args).get(0),
                        templateName, "@slot");
                String slVar = nextVar("sl");
                Emitter slot = new Emitter("slot", slotName, slVar);
                emitters.push(slot);
                return;
            }
            case "endslot": {
                if (!"slot".equals(em.kind)) {
                    throw new IllegalStateException("模板 [" + templateName + "] @endslot 没有对应的 @slot");
                }
                emitters.pop();
                Emitter comp = emitters.peek();
                comp.code.append("        java.io.StringWriter ").append(em.writerVar)
                        .append(" = new java.io.StringWriter();\n");
                comp.code.append(em.code);
                comp.code.append("        ").append(comp.slotsVar).append(".put(\"")
                        .append(escapeJava(em.name)).append("\", ").append(em.writerVar).append(".toString());\n");
                return;
            }

            /* ---------- 杂项 ---------- */
            case "csrf": {
                code.append("        write(").append(em.writerVar).append(", csrf());\n");
                return;
            }
            case "method": {
                code.append("        write(").append(em.writerVar).append(", methodField(")
                        .append(translateExpr(args, templateName).asObject()).append("));\n");
                return;
            }
            case "json": {
                List<String> parts = PhpExpressionTranslator.splitTopLevel(args);
                code.append("        echoRaw(").append(em.writerVar).append(", json_encode(")
                        .append(translateExpr(parts.get(0), templateName).asObject()).append("));\n");
                return;
            }
            case "route": {
                // @route('name') / @route('name', [...]) — http 模块路由别名 → URL
                // 编译为 BladeTemplate.route(name, params)，与 {{ route('name') }} 函数调用一致
                List<String> parts = PhpExpressionTranslator.splitTopLevel(args);
                String nameCode = translateExpr(parts.get(0), templateName).asObject();
                String paramsCode = parts.size() >= 2
                        ? translateExpr(parts.get(1), templateName).asObject() : "null";
                code.append("        write(").append(em.writerVar).append(", route(")
                        .append(nameCode).append(", ").append(paramsCode).append("));\n");
                return;
            }
            case "asset": {
                // asset 与 url 行为一致：按根路径拼接，不附加任何资源前缀
                String path = literalArgOrNull(args);
                if (path != null) {
                    code.append("        write(").append(em.writerVar).append(", asset(\"")
                            .append(escapeJava(path)).append("\"));\n");
                } else {
                    code.append("        write(").append(em.writerVar).append(", asset(String.valueOf(")
                            .append(translateExpr(args, templateName).asObject()).append(")));\n");
                }
                return;
            }
            /* ---------- 资源收集(@assets / @script) ---------- */
            case "assets": {
                String swVar = nextVar("sw");
                Emitter blockEmitter = new Emitter("assets", "assets", swVar);
                emitters.push(blockEmitter);
                return;
            }
            case "script": {
                String swVar = nextVar("sw");
                Emitter blockEmitter = new Emitter("script", "script", swVar);
                emitters.push(blockEmitter);
                return;
            }
            case "endassets": {
                if (!"assets".equals(emitters.peek().kind)) {
                    throw new IllegalStateException("模板 [" + templateName + "] @endassets 没有对应的 @assets");
                }
                emitters.pop();
                String key = escapeJava(templateName);
                initCode.append("        {\n");
                initCode.append("            final java.io.StringWriter ").append(em.writerVar)
                        .append(" = new java.io.StringWriter();\n");
                initCode.append(indent(em.code.toString(), "    "));
                initCode.append("            ctx.collectAssets(\"").append(key)
                        .append("\", ").append(em.writerVar).append(".toString());\n");
                initCode.append("        }\n");
                return;
            }
            case "endscript": {
                if (!"script".equals(emitters.peek().kind)) {
                    throw new IllegalStateException("模板 [" + templateName + "] @endscript 没有对应的 @script");
                }
                emitters.pop();
                String key = escapeJava(templateName);
                initCode.append("        {\n");
                initCode.append("            final java.io.StringWriter ").append(em.writerVar)
                        .append(" = new java.io.StringWriter();\n");
                initCode.append(indent(em.code.toString(), "    "));
                initCode.append("            ctx.collectScript(\"").append(key)
                        .append("\", ").append(em.writerVar).append(".toString());\n");
                initCode.append("        }\n");
                return;
            }

            default:
                break;
        }

        /* ---------- 动态注册的自定义指令 ---------- */
        if (BladeDirectives.hasCondition(name)) {
            code.append("        if (evalCondition(\"").append(escapeJava(name)).append("\"")
                    .append(argListCode(args, templateName)).append(")) {\n");
            frames.push(new Frame("custom:" + name, "        }\n"));
            return;
        }
        if (name.startsWith("else") && name.length() > 4 && BladeDirectives.hasCondition(name.substring(4))) {
            String base = name.substring(4);
            code.append("        } else if (evalCondition(\"").append(escapeJava(base)).append("\"")
                    .append(argListCode(args, templateName)).append(")) {\n");
            return;
        }
        if (name.startsWith("end") && BladeDirectives.hasCondition(name.substring(3))) {
            Frame f = frames.pop();
            if (!f.type.equals("custom:" + name.substring(3))) {
                throw new IllegalStateException("模板 [" + templateName + "] @" + name + " 与 @" + f.type + " 不匹配");
            }
            code.append(f.closing);
            return;
        }
        if (BladeDirectives.hasDirective(name)) {
            code.append("        write(").append(em.writerVar).append(", evalDirective(\"")
                    .append(escapeJava(name)).append("\"").append(argListCode(args, templateName)).append("));\n");
            return;
        }

        // 理论不可达（tokenizer 已过滤未知指令），兜底按原文输出
        emitText(em, tok.raw != null ? tok.raw : "@" + name);
    }

    private String argListCode(String args, String templateName) {
        if (args == null || args.trim().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PhpExpressionTranslator.Expr e : PhpExpressionTranslator.translateArgs(args)) {
            sb.append(", ").append(e.asObject());
        }
        return sb.toString();
    }

    private void popFrame(Deque<Frame> frames, StringBuilder code, String templateName, String directive) {
        if (frames.isEmpty()) {
            throw new IllegalStateException("模板 [" + templateName + "] @" + directive + " 没有对应的开始指令");
        }
        Frame f = frames.pop();
        code.append(f.closing);
    }

    private Frame findFrame(Deque<Frame> frames, String type) {
        for (Frame f : frames) {
            if (f.type.equals(type)) {
                return f;
            }
        }
        return null;
    }

    /** @php 内容：按顶层分号切分为语句并翻译 */
    private void emitPhpStatements(StringBuilder code, String phpSource, String templateName) {
        if (phpSource == null) {
            return;
        }
        for (String stmt : splitStatements(phpSource)) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            PhpExpressionTranslator.Expr e = translateExpr(trimmed, templateName);
            code.append("        { Object ").append(nextVar("php")).append(" = ")
                    .append(e.asObject()).append("; }\n");
        }
    }

    /** 按不在括号/引号内的分号切分 */
    private List<String> splitStatements(String src) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && i + 1 < src.length()) {
                    cur.append(src.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                cur.append(c);
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
                cur.append(c);
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                cur.append(c);
            } else if (c == ';' && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.toString().trim().length() > 0) {
            parts.add(cur.toString());
        }
        return parts;
    }

    /** @for 的三段式参数切分（引号/括号感知） */
    private String[] splitForParts(String args) {
        List<String> parts = splitStatements(args);
        return parts.toArray(new String[0]);
    }

    private static class ForeachParts {
        String collection;
        String keyVar;
        String valueVar;
    }

    private static final Pattern FOREACH_PATTERN =
            Pattern.compile("^(.+?)\\s+as\\s+(?:\\$(\\w+)\\s*=>\\s*)?\\$(\\w+)\\s*$", Pattern.DOTALL);

    private ForeachParts parseForeach(String args, String templateName) {
        Matcher m = FOREACH_PATTERN.matcher(args.trim());
        if (!m.matches()) {
            throw new IllegalStateException("模板 [" + templateName + "] @foreach 参数格式错误: " + args);
        }
        ForeachParts fp = new ForeachParts();
        fp.collection = translateExpr(m.group(1), templateName).asObject();
        fp.keyVar = m.group(2);
        fp.valueVar = m.group(3);
        return fp;
    }

    /** 提取编译期字符串字面量参数（section 名、模板名等） */
    private String literalArg(String argSource, String templateName, String directive) {
        PhpExpressionTranslator.Expr e = translateExpr(argSource, templateName);
        if (e.literalString == null) {
            throw new IllegalStateException("模板 [" + templateName + "] " + directive
                    + " 的名称参数必须是字符串字面量: " + argSource);
        }
        return e.literalString;
    }

    private String literalArgOrNull(String argSource) {
        try {
            PhpExpressionTranslator.Expr e = PhpExpressionTranslator.translate(argSource);
            return e.literalString;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String indent(String code, String prefix) {
        if (code.isEmpty()) {
            return code;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : code.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            sb.append(prefix).append(line).append("\n");
        }
        return sb.toString();
    }

    private String escapeJava(String str) {
        StringBuilder sb = new StringBuilder(str.length() + 16);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * 从处理后的源代码中提取包名（已移除注释和字符串）
     */
    private String extractPackageName(String processedSourceCode) {
        Pattern pattern = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
        Matcher matcher = pattern.matcher(processedSourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 移除源代码中的注释和字符串常量，避免干扰包名解析
     */
    private String removeCommentsAndStrings(String sourceCode) {
        StringBuilder result = new StringBuilder();
        int length = sourceCode.length();
        int i = 0;
        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;
        boolean inString = false;
        char stringDelimiter = '"';

        while (i < length) {
            char c = sourceCode.charAt(i);
            if (!inSingleLineComment && !inMultiLineComment && !inString) {
                if (c == '/' && i + 1 < length && sourceCode.charAt(i + 1) == '/') {
                    inSingleLineComment = true;
                    i += 2;
                    continue;
                } else if (c == '/' && i + 1 < length && sourceCode.charAt(i + 1) == '*') {
                    inMultiLineComment = true;
                    i += 2;
                    continue;
                } else if (c == '"' || c == '\'') {
                    inString = true;
                    stringDelimiter = c;
                    i++;
                    continue;
                }
            } else if (inSingleLineComment) {
                if (c == '\n' || c == '\r') {
                    inSingleLineComment = false;
                }
                i++;
                continue;
            } else if (inMultiLineComment) {
                if (c == '*' && i + 1 < length && sourceCode.charAt(i + 1) == '/') {
                    inMultiLineComment = false;
                    i += 2;
                    continue;
                }
                i++;
                continue;
            } else {
                if (c == stringDelimiter) {
                    if (i > 0 && sourceCode.charAt(i - 1) != '\\') {
                        inString = false;
                    }
                }
                i++;
                continue;
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }
}
