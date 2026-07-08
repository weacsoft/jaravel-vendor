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

public class BladeCompiler {
    //注释
    private static final Pattern COMMENT_PATTERN = Pattern.compile("\\{\\{--.*?--\\}\\}", Pattern.DOTALL);
    //输出
    private static final Pattern ECHO_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*\\}\\}");
    //命令 — 指令名必须以字母开头，避免误匹配 URL 中的 @2、@3 等
    private static final Pattern DIRECTIVE_PATTERN = Pattern.compile("@([a-zA-Z]\\w*)\\s*(?:\\((.*?)\\))?");
    //参数
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$(\\w+)");

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
     * <p>
     * 加载顺序：
     * <ol>
     *   <li>文件系统：{@code ./resources/{templatePath}}（如果文件存在）</li>
     *   <li>ClassPath：{@code classpath:{templatePath}}（JAR 内置资源）</li>
     * </ol>
     * 这样可以在不重新打包 JAR 的情况下，通过在运行目录下放置 {@code resources/} 文件夹
     * 来覆盖或新增模板，实现前端独立更新。
     *
     * @param templatePath 模板相对路径（如 {@code templates/layout.blade.java}）
     * @return 模板内容的输入流
     * @throws IOException 如果两个位置都找不到模板文件
     */
    private InputStream resolveTemplateStream(String templatePath) throws IOException {
        // 1. 优先从文件系统 ./resources/ 目录加载
        File file = new File("resources" + File.separator + templatePath);
        if (file.isFile()) {
            return new FileInputStream(file);
        }
        // 2. 回退到 ClassPath（JAR 内置资源）
        return new ClassPathResource(templatePath).getInputStream();
    }

    /**
     * 编译一个文件的代码
     * <p>
     * 资源加载优先级：
     * <ol>
     *   <li>文件系统 {@code ./resources/{templateDir}/...} 目录（支持热更新，无需重新打包）</li>
     *   <li>ClassPath classpath:{templateDir}/... （JAR 内置资源）</li>
     * </ol>
     *
     * @param templateName 模板文件名
     */
    public String compile(String templateName) throws IOException {
        String templatePath = templateDir + File.separator + templateName.replace(".", File.separator) + suffix;
        // 优先从文件系统 ./resources/ 目录加载（支持前端独立更新）
        InputStream resource = resolveTemplateStream(templatePath);
        String employees = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource))) {
            employees = reader
                    .lines()
                    .collect(Collectors.joining("\n"));
        }
        String content = employees;
        String className = generateClassName(templateName);
        String sourceCode = generateJavaCode(className, content);
        if (sourceCode.isEmpty()) {
            throw new IOException("源代码不能为空");
        }
        // 处理源代码，拼接成标准的类名加包名
        String codeWithoutCommentsAndStrings = removeCommentsAndStrings(sourceCode);
        String packageName = extractPackageName(codeWithoutCommentsAndStrings);
        String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;
        // 获取系统编译器
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("无法获取Java编译器，请确保使用JDK而非JRE运行程序");
        }
        // 创建诊断监听器，捕获编译错误
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        // 内存编译和加载
        try (MemoryFileManager fileManager = new MemoryFileManager(compiler.getStandardFileManager(diagnostics, null, null))) {
            List<JavaFileObject> compilationUnits = new ArrayList<>();
            compilationUnits.add(new SourceCodeJavaFileObject(fullClassName, sourceCode));
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null, null, compilationUnits);
            Boolean success = task.call();
            // 检查编译错误
            if (success == null || !success) {
                StringBuilder errorMsg = new StringBuilder("编译错误: ");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    errorMsg.append(String.format("\n第%d行: %s", diagnostic.getLineNumber(), diagnostic.getMessage(null)));
                }
                throw new RuntimeException(errorMsg.toString());
            }
            // 从内存文件管理器中获取编译后的类字节码，并加入到搜索器里
            for (String name : fileManager.getGeneratedClassNames()) {
                classLoader.getCompiledClasses().put(name, fileManager.getGeneratedClass(name));
            }
        }
        return fullClassName;
    }

    private String generateClassName(String templateName) {
        return "Blade_" + templateName.replace(File.separator, "_").replace("/", "_").replace("\\", "_").replace(".", "_");
    }

    private String generateJavaCode(String className, String content) {
        StringBuilder javaCode = new StringBuilder();
        javaCode.append("import com.weacsoft.jaravel.vendor.jblade.*;\n");
        javaCode.append("import java.io.*;\n");
        javaCode.append("import java.util.*;\n");
        javaCode.append("import java.util.function.*;\n\n");
        javaCode.append("public class ").append(className).append(" extends BladeTemplate {\n\n");

        Map<String, String> sections = extractSections(content);
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            String sectionName = entry.getKey();
            String sectionContent = entry.getValue();
            javaCode.append("    private void renderSection_").append(sectionName).append("(Writer writer) throws Exception {\n");
            javaCode.append("        BladeContext ctx = getContext();\n");
            String sectionCode = compileSectionContent(sectionContent);
            javaCode.append(sectionCode);
            javaCode.append("    }\n\n");
        }

        javaCode.append("    @Override\n");
        javaCode.append("    public void init() {\n");
        javaCode.append("        try {\n");
        javaCode.append("            BladeContext ctx = getContext();\n");

        for (String sectionName : sections.keySet()) {
            javaCode.append("            ctx.setSectionRenderer(\"").append(sectionName).append("\", writer -> {\n");
            javaCode.append("                try {\n");
            javaCode.append("                    renderSection_").append(sectionName).append("(writer);\n");
            javaCode.append("                } catch (Exception e) {\n");
            javaCode.append("                    e.printStackTrace();\n");
            javaCode.append("                }\n");
            javaCode.append("            });\n");
        }

        String initCode = processInitDirectives(content);
        javaCode.append(initCode);

        javaCode.append("        } catch (Exception e) {\n");
        javaCode.append("            e.printStackTrace();\n");
        javaCode.append("        }\n");
        javaCode.append("    }\n\n");

        javaCode.append("    @Override\n");
        javaCode.append("    public void render(Writer writer) throws Exception {\n");
        javaCode.append("        BladeContext ctx = getContext();\n");

        String renderCode = processRenderDirectives(content, sections.keySet());
        javaCode.append(renderCode);

        String componentCode = processComponents(content);
        javaCode.append(componentCode);

        javaCode.append("    }\n");
        javaCode.append("}\n");

        return javaCode.toString();
    }

    private Map<String, String> extractSections(String content) {
        Map<String, String> sections = new HashMap<>();
        String[] lines = content.split("\r?\n");
        boolean inSection = false;
        String currentSection = null;
        StringBuilder sectionContent = new StringBuilder();

        for (String line : lines) {
            String trimmedLine = line.trim();
            Matcher matcher = DIRECTIVE_PATTERN.matcher(trimmedLine);

            if (matcher.find()) {
                String directive = matcher.group(1);
                String args = matcher.group(2) != null ? matcher.group(2) : "";

                if (directive.equals("section")) {
                    if (!args.contains(",")) {
                        inSection = true;
                        currentSection = args.replace("'", "").replace("\"", "");
                        sectionContent = new StringBuilder();
                    }
                } else if (directive.equals("endsection")) {
                    if (inSection) {
                        sections.put(currentSection, sectionContent.toString());
                        inSection = false;
                        currentSection = null;
                    }
                } else if (inSection) {
                    sectionContent.append(line).append("\n");
                }
            } else if (inSection) {
                sectionContent.append(line).append("\n");
            }
        }

        return sections;
    }

    private String compileSectionContent(String content) {
        StringBuilder code = new StringBuilder();
        String[] lines = content.split("\r?\n");
        Stack<DirectiveContext> directiveStack = new Stack<>();
        Set<String> localVars = new HashSet<>();

        for (String line : lines) {
            String trimmedLine = line.trim();
            Matcher directiveMatcher = DIRECTIVE_PATTERN.matcher(trimmedLine);

            if (directiveMatcher.find()) {
                String directive = directiveMatcher.group(1);
                String args = directiveMatcher.group(2) != null ? directiveMatcher.group(2) : "";

                switch (directive) {
                    case "if":
                        code.append("        if (").append(compileConditionExpression(args, localVars)).append(") {\n");
                        directiveStack.push(new DirectiveContext("if", 0));
                        break;
                    case "elseif":
                        code.append("        } else if (").append(compileConditionExpression(args, localVars)).append(") {\n");
                        break;
                    case "else":
                        code.append("        } else {\n");
                        break;
                    case "endif":
                        code.append("        }\n");
                        if (!directiveStack.isEmpty() && directiveStack.peek().type.equals("if")) {
                            directiveStack.pop();
                        }
                        break;
                    case "for":
                        String[] forParts = args.split(";");
                        if (forParts.length == 3) {
                            String initPart = forParts[0].trim();
                            List<String> forVars = new ArrayList<>();
                            if (initPart.contains("=")) {
                                String varDecl = initPart.split("=")[0].trim();
                                String[] varParts = varDecl.split("\\s+");
                                if (varParts.length >= 2) {
                                    String varName = varParts[1];
                                    localVars.add(varName);
                                    forVars.add(varName);
                                }
                            }
                            code.append("        for (").append(compileOutputExpression(forParts[0], localVars)).append("; ")
                                    .append(compileOutputExpression(forParts[1], localVars)).append("; ")
                                    .append(compileOutputExpression(forParts[2], localVars)).append(") {\n");
                            directiveStack.push(new DirectiveContext("for", forVars));
                        }
                        break;
                    case "endfor":
                        code.append("        }\n");
                        if (!directiveStack.isEmpty() && directiveStack.peek().type.equals("for")) {
                            DirectiveContext ctx = directiveStack.pop();
                            for (String var : ctx.forVars) {
                                localVars.remove(var);
                            }
                        }
                        break;
                    case "foreach":
                        String[] foreachParts = args.split(" as ");
                        if (foreachParts.length == 2) {
                            String collectionExpr = foreachParts[0].trim();
                            String collection;
                            if (collectionExpr.startsWith("$")) {
                                String varName = collectionExpr.substring(1);
                                if (localVars.contains(varName)) {
                                    collection = varName;
                                } else {
                                    collection = "ctx.getVariable(\"" + varName + "\")";
                                }
                            } else {
                                collection = compileConditionExpression(collectionExpr, localVars);
                            }
                            String var = foreachParts[1].trim().replace("$", "");
                            code.append("        for (Object ").append(var).append(" : (Iterable) ").append(collection).append(") {\n");
                            localVars.add(var);
                            List<String> foreachVars = new ArrayList<>();
                            foreachVars.add(var);
                            directiveStack.push(new DirectiveContext("foreach", foreachVars));
                        }
                        break;
                    case "endforeach":
                        code.append("        }\n");
                        if (!directiveStack.isEmpty() && directiveStack.peek().type.equals("foreach")) {
                            DirectiveContext ctx = directiveStack.pop();
                            for (String var : ctx.forVars) {
                                localVars.remove(var);
                            }
                        }
                        break;
                    default:
                        // 未知指令作为普通内容输出
                        processLineWithEcho(code, line, localVars);
                        code.append("        write(writer, \"\\n\");\n");
                        break;
                }
            } else {
                processLineWithEcho(code, line, localVars);
                code.append("        write(writer, \"\\n\");\n");
            }
        }

        return code.toString();
    }

    private String processInitDirectives(String content) {
        StringBuilder code = new StringBuilder();
        String[] lines = content.split("\n");
        boolean inSection = false;
        String currentSection = null;
        StringBuilder sectionContent = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            Matcher matcher = DIRECTIVE_PATTERN.matcher(line);

            if (matcher.find()) {
                String directive = matcher.group(1);
                String args = matcher.group(2) != null ? matcher.group(2) : "";

                switch (directive) {
                    case "extends":
                        code.append("            ctx.setParentTemplate(\"").append(args.replace("'", "").replace("\"", "")).append("\");\n");
                        break;
                    case "section":
                        if (args.contains(",")) {
                            String[] parts = args.split(",", 2);
                            String sectionName = parts[0].trim().replace("'", "").replace("\"", "");
                            String sectionValue = parts[1].trim();
                            // 判断值是字符串字面量（引号包裹）还是表达式（如 $title）
                            if (sectionValue.startsWith("'") || sectionValue.startsWith("\"")) {
                                // 字符串字面量：去引号后直接使用
                                sectionValue = sectionValue.replaceAll("^['\"]|['\"]$", "");
                                code.append("            ctx.setSection(\"").append(sectionName).append("\", \"").append(escapeJava(sectionValue)).append("\");\n");
                            } else {
                                // 表达式（如 $title, $title ?? 'default'）：编译为 Blade 表达式
                                String compiledExpr = compileExpression(sectionValue, new HashSet<>());
                                code.append("            ctx.setSection(\"").append(sectionName).append("\", String.valueOf(").append(compiledExpr).append("));\n");
                            }
                        } else {
                            inSection = true;
                            currentSection = args.replace("'", "").replace("\"", "");
                            sectionContent = new StringBuilder();
                        }
                        break;
                    case "endsection":
                        if (inSection) {
                            inSection = false;
                            currentSection = null;
                        }
                        break;
                }
            } else if (inSection) {
                sectionContent.append(line).append("\n");
            }
        }

        return code.toString();
    }

    private String processRenderDirectives(String content, Set<String> sectionNames) {
        StringBuilder code = new StringBuilder();
        String[] lines = content.split("\n");
        Set<String> localVars = new HashSet<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            Matcher directiveMatcher = DIRECTIVE_PATTERN.matcher(trimmedLine);

            if (directiveMatcher.find()) {
                String directive = directiveMatcher.group(1);
                String args = directiveMatcher.group(2) != null ? directiveMatcher.group(2) : "";
                int dirStart = directiveMatcher.start();
                int dirEnd = directiveMatcher.end();

                switch (directive) {
                    case "extends":
                    case "section":
                    case "endsection":
                        break;
                    case "yield":
                        // 输出指令前的文本（如 <title>）
                        if (dirStart > 0) {
                            processLineWithEcho(code, trimmedLine.substring(0, dirStart), localVars);
                        }
                        // 解析 @yield('name') 或 @yield('name', 'default value')
                        String[] yieldParts = args.split(",", 2);
                        String yieldName = yieldParts[0].trim().replace("'", "").replace("\"", "");
                        String yieldDefault = (yieldParts.length == 2) ? yieldParts[1].trim().replace("'", "").replace("\"", "") : null;
                        code.append("        {\n");
                        code.append("            Consumer<Writer> renderer = ctx.getSectionRenderer(\"").append(yieldName).append("\");\n");
                        code.append("            if (renderer != null) {\n");
                        code.append("                renderer.accept(writer);\n");
                        code.append("            } else {\n");
                        code.append("                String yieldContent = ctx.getSection(\"").append(yieldName).append("\");\n");
                        code.append("                if (yieldContent != null) {\n");
                        code.append("                    write(writer, yieldContent);\n");
                        if (yieldDefault != null && !yieldDefault.isEmpty()) {
                            code.append("                } else {\n");
                            code.append("                    write(writer, \"").append(escapeJava(yieldDefault)).append("\");\n");
                        }
                        code.append("                }\n");
                        code.append("            }\n");
                        code.append("        }\n");
                        // 输出指令后的文本（如 </title>）
                        if (dirEnd < trimmedLine.length()) {
                            processLineWithEcho(code, trimmedLine.substring(dirEnd), localVars);
                        }
                        break;
                    case "foreach":
                        String[] foreachParts = args.split(" as ");
                        if (foreachParts.length == 2) {
                            String collectionExpr = foreachParts[0].trim();
                            String collection;
                            if (collectionExpr.startsWith("$")) {
                                String varName = collectionExpr.substring(1);
                                if (localVars.contains(varName)) {
                                    collection = varName;
                                } else {
                                    collection = "ctx.getVariable(\"" + varName + "\")";
                                }
                            } else {
                                collection = compileConditionExpression(collectionExpr, localVars);
                            }
                            String var = foreachParts[1].trim().replace("$", "");
                            code.append("        Object[] ").append(var).append("Array = ").append(collection).append(" instanceof Object[] ? (Object[]) ").append(collection).append(" : new Object[]{").append(collection).append("};\n");
                            code.append("        for (int ").append(var).append("Index = 0; ").append(var).append("Index < ").append(var).append("Array.length; ").append(var).append("Index++) {\n");
                            code.append("            Object ").append(var).append(" = ").append(var).append("Array[").append(var).append("Index];\n");
                            localVars.add(var);
                        }
                        break;
                    case "endforeach":
                        code.append("        }\n");
                        break;
                    case "asset":
                        // 输出指令前的文本
                        if (dirStart > 0) {
                            processLineWithEcho(code, trimmedLine.substring(0, dirStart), localVars);
                        }
                        // @asset('css/app.css') → 生成静态资源 URL
                        String assetPath = args.trim().replace("'", "").replace("\"", "");
                        code.append("        write(writer, BladeAssetHelper.url(\"").append(escapeJava(assetPath)).append("\"));\n");
                        // 输出指令后的文本
                        if (dirEnd < trimmedLine.length()) {
                            processLineWithEcho(code, trimmedLine.substring(dirEnd), localVars);
                        }
                        break;
                    case "component":
                    case "endcomponent":
                    case "slot":
                    case "endslot":
                        break;
                    default:
                        // 未知指令（如 CSS @media、@keyframes 等）作为普通内容输出
                        processLineWithEcho(code, line, localVars);
                        break;
                }
            } else {
                processLineWithEcho(code, line, localVars);
            }
        }

        return code.toString();
    }

    private String processComponents(String content) {
        StringBuilder code = new StringBuilder();
        String[] lines = content.split("\n");
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];
            String trimmedLine = line.trim();
            Matcher directiveMatcher = DIRECTIVE_PATTERN.matcher(trimmedLine);

            if (directiveMatcher.find()) {
                String directive = directiveMatcher.group(1);
                String args = directiveMatcher.group(2) != null ? directiveMatcher.group(2) : "";

                if (directive.equals("component")) {
                    args = args.trim();
                    String componentTemplate;
                    String componentParams = "";

                    if (args.startsWith("'") || args.startsWith("\"")) {
                        int endQuote = args.indexOf(args.charAt(0), 1);
                        if (endQuote > 0) {
                            componentTemplate = args.substring(1, endQuote);
                            if (endQuote + 1 < args.length()) {
                                componentParams = args.substring(endQuote + 1).trim();
                            }
                        } else {
                            componentTemplate = args.substring(1);
                        }
                    } else {
                        componentTemplate = args.split("\\s+")[0];
                    }

                    code.append("        {\n");
                    code.append("            Map<String, Object> componentData = new java.util.HashMap<>();\n");
                    code.append("            Map<String, String> componentSlots = new java.util.HashMap<>();\n");
                    code.append("            StringBuilder slotContent = new StringBuilder();\n");
                    code.append("            String currentSlot = null;\n");
                    code.append("            boolean inSlot = false;\n");

                    code.append(parseComponentParams(componentParams));

                    i++;
                    int componentDepth = 1;

                    while (i < lines.length && componentDepth > 0) {
                        String componentLine = lines[i];
                        String componentTrimmed = componentLine.trim();
                        Matcher componentDirectiveMatcher = DIRECTIVE_PATTERN.matcher(componentTrimmed);

                        if (componentDirectiveMatcher.find()) {
                            String componentDirective = componentDirectiveMatcher.group(1);

                            if (componentDirective.equals("component")) {
                                componentDepth++;
                            } else if (componentDirective.equals("endcomponent")) {
                                componentDepth--;
                                if (componentDepth == 0) {
                                    i++;
                                    break;
                                }
                            } else if (componentDirective.equals("slot")) {
                                String slotName = componentDirectiveMatcher.group(2) != null ?
                                        componentDirectiveMatcher.group(2).replace("'", "").replace("\"", "").split("\\s+")[0] : "default";
                                code.append("            if (currentSlot != null && inSlot) {\n");
                                code.append("                componentSlots.put(currentSlot, slotContent.toString());\n");
                                code.append("            }\n");
                                code.append("            currentSlot = \"").append(slotName).append("\";\n");
                                code.append("            slotContent = new StringBuilder();\n");
                                code.append("            inSlot = true;\n");
                                i++;
                                continue;
                            } else if (componentDirective.equals("endslot")) {
                                code.append("            if (currentSlot != null && inSlot) {\n");
                                code.append("                componentSlots.put(currentSlot, slotContent.toString());\n");
                                code.append("            }\n");
                                code.append("            currentSlot = null;\n");
                                code.append("            inSlot = false;\n");
                                i++;
                                continue;
                            }
                        }

                        code.append("            slotContent.append(\"").append(escapeJava(componentLine)).append("\\n\");\n");
                        i++;
                    }

                    code.append("            if (currentSlot != null && inSlot) {\n");
                    code.append("                componentSlots.put(currentSlot, slotContent.toString());\n");
                    code.append("            }\n");

                    code.append("            if (!componentSlots.containsKey(\"default\")) {\n");
                    code.append("                componentSlots.put(\"default\", slotContent.toString());\n");
                    code.append("            }\n");
                    code.append("            renderComponent(writer, \"").append(componentTemplate).append("\", componentData, componentSlots);\n");
                    code.append("        }\n");
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }

        return code.toString();
    }

    private void processLineWithEcho(StringBuilder code, String line, Set<String> localVars) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }

        String processed = line;

        Matcher commentMatcher = COMMENT_PATTERN.matcher(processed);
        processed = commentMatcher.replaceAll("");

        Matcher echoMatcher = ECHO_PATTERN.matcher(processed);
        int lastEnd = 0;
        while (echoMatcher.find()) {
            String before = processed.substring(lastEnd, echoMatcher.start());
            before = COMMENT_PATTERN.matcher(before).replaceAll("");
            if (!before.isEmpty()) {
                code.append("        write(writer, \"").append(escapeJava(before)).append("\");\n");
            }

            String expression = echoMatcher.group(1).trim();
            if (expression.startsWith("--") && expression.endsWith("--")) {
                lastEnd = echoMatcher.end();
                continue;
            }
            expression = COMMENT_PATTERN.matcher(expression).replaceAll("");
            if (!expression.isEmpty()) {
                String converted = compileOutputExpression(expression, localVars);
                code.append("        write(writer, (").append(converted).append("));\n");
            }

            lastEnd = echoMatcher.end();
        }

        String after = processed.substring(lastEnd);
        after = COMMENT_PATTERN.matcher(after).replaceAll("");
        if (!after.isEmpty()) {
            code.append("        write(writer, \"").append(escapeJava(after)).append("\");\n");
        }
    }

    // ===== Blade 表达式编译引擎 =====
    // jblade 编译器原生支持 Blade 模板表达式语法，将其编译为 Java 代码。
    // 这是 jblade 编译器的核心能力，不是外部转换层。

    /**
     * 编译表达式用于条件判断（@if, @elseif），布尔上下文。
     * 简单变量引用自动包装为 toBoolean()。
     */
    private String compileConditionExpression(String expr, Set<String> localVars) {
        String compiled = compileExpression(expr, localVars);
        // 如果是简单变量引用，包装为 toBoolean()
        if (compiled.equals("ctx.getVariable(\"" + expr.trim().replace("$", "") + "\")")) {
            return "toBoolean(" + compiled + ")";
        }
        return compiled;
    }

    /**
     * 编译表达式用于输出（{{ }}），值上下文，不包装 toBoolean()。
     */
    private String compileOutputExpression(String expr, Set<String> localVars) {
        return compileExpression(expr, localVars);
    }

    /**
     * 编译 Blade 表达式为 Java 表达式。
     * <p>
     * jblade 原生支持以下 Blade 语法：
     * <ul>
     *   <li>单引号字符串字面量 'text' → Java 双引号 "text"</li>
     *   <li>静态方法调用 URL::method(), Carbon::method() → 方法调用</li>
     *   <li>辅助函数 csrf_field(), csrf_token(), old() → 空字符串</li>
     *   <li>对象方法调用 $var->method(args) → invokeMethod(...)</li>
     *   <li>对象属性访问 $var->prop → getProperty(...)</li>
     *   <li>数组访问 $var['key'] → getMapValue(...)</li>
     *   <li>关联数组字面量 ['key' => value] → Map.of("key", value)</li>
     *   <li>字符串拼接运算符 . → Java +</li>
     *   <li>空合并运算符 ?? → nullCoalescing()</li>
     *   <li>Elvis 运算符 ?: → elvis()</li>
     *   <li>三元运算符 ? : → toBoolean() ? :</li>
     *   <li>变量引用 $var → ctx.getVariable("var")</li>
     * </ul>
     */
    private String compileExpression(String expr, Set<String> localVars) {
        String result = expr.trim();

        // Step 1: 单引号字符串 → 双引号字符串
        result = compileStringLiterals(result);

        // Step 2: 静态方法调用和命名空间
        // URL::asset('path') → asset("path")
        result = result.replaceAll("\\bURL::(\\w+)\\s*\\(", "$1(");
        // \Carbon\Carbon::parse($date) → carbonParse($date)
        result = result.replaceAll("\\\\?Carbon\\\\Carbon::(\\w+)\\s*\\(", "carbon$1(");
        // \Illuminate\Support\Carbon::today() → carbonToday()
        result = result.replaceAll("\\\\?Illuminate\\\\Support\\\\Carbon::(\\w+)\\s*\\(", "carbon$1(");
        // Carbon::parse($date) → carbonParse($date) (without namespace)
        result = result.replaceAll("\\bCarbon::(\\w+)\\s*\\(", "carbon$1(");

        // Step 3: 空返回值的辅助函数
        result = result.replaceAll("\\bcsrf_field\\s*\\(\\s*\\)", "\"\"");
        result = result.replaceAll("\\bcsrf_token\\s*\\(\\s*\\)", "\"\"");
        result = result.replaceAll("\\bold\\s*\\(\\s*\\)", "\"\"");

        // Step 4: 对象方法调用 $var->method(args) → invokeMethod(varRef, "method", args)
        result = compileMethodCalls(result, localVars);

        // Step 5: 对象属性访问 $var->prop → getProperty(varRef, "prop")
        result = compilePropertyAccess(result, localVars);

        // Step 5b: 方法调用结果的属性访问 method()->prop → getProperty(method(), "prop")
        result = compileMethodChainProperty(result);

        // Step 6: 数组访问 $var['key'] 或 $var["key"] → getMapValue(varRef, "key")
        result = compileArrayAccess(result, localVars);

        // Step 7: 关联数组字面量 ['key' => value] → Map.of("key", value)
        result = compileArrayLiterals(result, localVars);

        // Step 8: 字符串拼接 . → +（仅在字符串外部）
        result = compileStringConcatenation(result);

        // Step 8b: 空合并运算符 ?? → nullCoalescing()
        result = compileNullCoalescing(result, localVars);

        // Step 9: Elvis 运算符 ?: → elvis()
        result = compileElvisOperator(result, localVars);

        // Step 10: 三元运算符 ? : → toBoolean() ? :
        result = compileTernaryOperator(result, localVars);

        // Step 11: 剩余 $var → ctx.getVariable("var") 或本地变量
        result = compileVariables(result, localVars);

        return result;
    }

    /**
     * 编译单引号字符串字面量为 Java 双引号字符串。
     * 'text' → "text"，同时转义内部双引号。
     */
    private String compileStringLiterals(String expr) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '\'') {
                // 查找闭合单引号
                int end = i + 1;
                while (end < expr.length()) {
                    if (expr.charAt(end) == '\\' && end + 1 < expr.length()) {
                        end += 2;
                    } else if (expr.charAt(end) == '\'') {
                        break;
                    } else {
                        end++;
                    }
                }
                if (end < expr.length()) {
                    String content = expr.substring(i + 1, end);
                    // 转义双引号和反斜杠
                    content = content.replace("\\", "\\\\").replace("\"", "\\\"");
                    // 处理 PHP 单引号转义：\\ → \，\' → '
                    content = content.replace("\\\\'", "'").replace("\\\\", "\\");
                    result.append('"').append(content).append('"');
                    i = end + 1;
                } else {
                    result.append(c);
                    i++;
                }
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    /**
     * 编译对象方法调用 $var->method(args) → invokeMethod(varRef, "method", args)。
     * 正确处理带参数的方法调用，手动查找匹配的闭括号并替换整个方法调用。
     */
    private String compileMethodCalls(String expr, Set<String> localVars) {
        Pattern pattern = Pattern.compile("\\$(\\w+)->(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(expr);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            // 追加匹配前的内容
            result.append(expr, lastEnd, matcher.start());

            String varName = matcher.group(1);
            String methodName = matcher.group(2);
            String varRef = localVars.contains(varName) ? varName : "ctx.getVariable(\"" + varName + "\")";

            // 找到匹配的闭括号
            int argsStart = matcher.end();
            int argsEnd = findMatchingParen(expr, argsStart);

            if (argsEnd > 0) {
                String args = expr.substring(argsStart, argsEnd).trim();
                if (args.isEmpty()) {
                    result.append("invokeMethod(").append(varRef).append(", \"").append(methodName).append("\")");
                } else {
                    result.append("invokeMethod(").append(varRef).append(", \"").append(methodName).append("\", ").append(args).append(")");
                }
                lastEnd = argsEnd + 1; // 跳过闭括号
            } else {
                result.append("invokeMethod(").append(varRef).append(", \"").append(methodName).append("\")(");
                lastEnd = matcher.end();
            }
        }
        result.append(expr, lastEnd, expr.length());
        return result.toString();
    }

    /**
     * 找到匹配的闭括号位置。
     * @param expr 表达式
     * @param start 起始位置（开括号之后）
     * @return 闭括号位置，或 -1 如果未找到
     */
    private int findMatchingParen(String expr, int start) {
        int depth = 1;
        boolean inString = false;
        char stringDelimiter = '"';
        for (int i = start; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (!inString) {
                if (c == '"' || c == '\'') {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            } else {
                if (c == '\\' && i + 1 < expr.length()) {
                    i++;
                } else if (c == stringDelimiter) {
                    inString = false;
                }
            }
        }
        return -1;
    }

    /**
     * 编译对象属性访问 $var->prop → getProperty(varRef, "prop")。
     */
    private String compilePropertyAccess(String expr, Set<String> localVars) {
        Pattern pattern = Pattern.compile("\\$(\\w+)->(\\w+)");
        Matcher matcher = pattern.matcher(expr);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String propName = matcher.group(2);
            String varRef = localVars.contains(varName) ? varName : "ctx.getVariable(\"" + varName + "\")";
            matcher.appendReplacement(sb, "getProperty(" + varRef + ", \"" + propName + "\")");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 编译数组访问 $var['key'] 或 $var["key"] → getMapValue(varRef, "key")。
     */
    private String compileArrayAccess(String expr, Set<String> localVars) {
        // 匹配 $var['key'] 或 $var["key"]
        Pattern pattern = Pattern.compile("\\$(\\w+)\\[(?:'([^']*)'|\"([^\"]*)\")\\]");
        Matcher matcher = pattern.matcher(expr);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String key = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            String varRef = localVars.contains(varName) ? varName : "ctx.getVariable(\"" + varName + "\")";
            matcher.appendReplacement(sb, "getMapValue(" + varRef + ", \"" + key + "\")");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 编译关联数组字面量 ['key' => value] → Map.of("key", value)。
     * 支持多键值对。
     */
    private String compileArrayLiterals(String expr, Set<String> localVars) {
        // 匹配 ['key' => value] 或 ["key" => value]
        Pattern pattern = Pattern.compile("\\[([^\\[\\]]+)\\]");
        Matcher matcher = pattern.matcher(expr);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String inner = matcher.group(1).trim();
            // 检查是否是 PHP 关联数组（包含 =>）
            if (inner.contains("=>")) {
                String[] pairs = inner.split(",");
                StringBuilder mapBuilder = new StringBuilder("Map.of(");
                boolean first = true;
                boolean hasArrow = false;
                for (String pair : pairs) {
                    pair = pair.trim();
                    if (pair.contains("=>")) {
                        hasArrow = true;
                        String[] kv = pair.split("=>", 2);
                        String key = kv[0].trim().replace("'", "").replace("\"", "");
                        String value = kv[1].trim();
                        if (!first) {
                            mapBuilder.append(", ");
                        }
                        mapBuilder.append("\"").append(key).append("\", ").append(value);
                        first = false;
                    }
                }
                if (hasArrow) {
                    mapBuilder.append(")");
                    matcher.appendReplacement(sb, mapBuilder.toString());
                } else {
                    matcher.appendReplacement(sb, "[" + inner + "]");
                }
            } else {
                matcher.appendReplacement(sb, "[" + inner + "]");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 编译字符串拼接运算符 . 为 Java +。
     * 仅在字符串字面量外部进行编译，避免破坏字符串内容。
     */
    private String compileStringConcatenation(String expr) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        char stringDelimiter = '"';
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);

            if (!inString) {
                if (c == '"' || c == '\'') {
                    inString = true;
                    stringDelimiter = c;
                    result.append(c);
                    i++;
                } else if (c == '.' && i > 0 && i < expr.length() - 1) {
                    // 检查是否是数字小数点（前后都是数字）
                    char prev = expr.charAt(i - 1);
                    char next = expr.charAt(i + 1);
                    if (Character.isDigit(prev) && Character.isDigit(next)) {
                        result.append(c);
                    } else {
                        // 检查是否是 -> 的一部分（已经被处理过，但以防万一）
                        if (prev == '-' || next == '>') {
                            result.append(c);
                        } else {
                            result.append(" + ");
                        }
                    }
                    i++;
                } else {
                    result.append(c);
                    i++;
                }
            } else {
                if (c == '\\' && i + 1 < expr.length()) {
                    result.append(c);
                    result.append(expr.charAt(i + 1));
                    i += 2;
                } else if (c == stringDelimiter) {
                    inString = false;
                    result.append(c);
                    i++;
                } else {
                    result.append(c);
                    i++;
                }
            }
        }
        return result.toString();
    }

    /**
     * 编译 Elvis 运算符 ?: → elvis(a, b)。
     * 注意：需要在三元运算符之前处理，因为 ?: 是 ? : 的简写。
     */
    private String compileElvisOperator(String expr, Set<String> localVars) {
        // 匹配 a ?: b 模式（非贪婪）
        // 需要确保 ? 后面紧跟着 :（没有中间内容）
        Pattern pattern = Pattern.compile("([^?]+?)\\?:(.+)");
        Matcher matcher = pattern.matcher(expr);
        if (matcher.matches()) {
            String a = matcher.group(1).trim();
            String b = matcher.group(2).trim();
            return "elvis(" + a + ", " + b + ")";
        }
        return expr;
    }

    /**
     * 编译三元运算符 ? : → Java 三元运算符。
     * 将条件部分包装为 toBoolean()。
     */
    private String compileTernaryOperator(String expr, Set<String> localVars) {
        // 简单的三元运算符转换：condition ? truePart : falsePart
        // 需要找到 ? 和 : 的位置，注意嵌套
        int questionMark = findTernaryQuestionMark(expr);
        if (questionMark < 0) {
            return expr;
        }
        int colon = findTernaryColon(expr, questionMark + 1);
        if (colon < 0) {
            return expr;
        }
        String condition = expr.substring(0, questionMark).trim();
        String truePart = expr.substring(questionMark + 1, colon).trim();
        String falsePart = expr.substring(colon + 1).trim();
        return "toBoolean(" + condition + ") ? " + truePart + " : " + falsePart;
    }

    /**
     * 查找三元运算符的 ? 位置（跳过字符串内的 ?）。
     */
    private int findTernaryQuestionMark(String expr) {
        boolean inString = false;
        char stringDelimiter = '"';
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (!inString) {
                if (c == '"' || c == '\'') {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == '?') {
                    // 检查不是 ?: (Elvis，已处理)
                    if (i + 1 < expr.length() && expr.charAt(i + 1) == ':') {
                        return -1; // Elvis，不是三元
                    }
                    return i;
                }
            } else {
                if (c == '\\' && i + 1 < expr.length()) {
                    i++;
                } else if (c == stringDelimiter) {
                    inString = false;
                }
            }
        }
        return -1;
    }

    /**
     * 查找三元运算符的 : 位置（跳过字符串内的 :）。
     */
    private int findTernaryColon(String expr, int start) {
        boolean inString = false;
        char stringDelimiter = '"';
        for (int i = start; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (!inString) {
                if (c == '"' || c == '\'') {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == ':') {
                    return i;
                }
            } else {
                if (c == '\\' && i + 1 < expr.length()) {
                    i++;
                } else if (c == stringDelimiter) {
                    inString = false;
                }
            }
        }
        return -1;
    }

    /**
     * 编译剩余的 $var 为 ctx.getVariable("var") 或本地变量名。
     */
    private String compileVariables(String expr, Set<String> localVars) {
        Matcher varMatcher = VAR_PATTERN.matcher(expr);
        StringBuffer sb = new StringBuffer();
        while (varMatcher.find()) {
            String varName = varMatcher.group(1);
            if (localVars.contains(varName)) {
                varMatcher.appendReplacement(sb, varName);
            } else {
                varMatcher.appendReplacement(sb, "ctx.getVariable(\"" + varName + "\")");
            }
        }
        varMatcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 编译方法调用链的属性访问 method()->prop → getProperty(method(), "prop")。
     * 处理如 carbonToday()->year 的模式。
     */
    private String compileMethodChainProperty(String expr) {
        // 匹配 word(args)->prop 模式（简单括号，无嵌套）
        Pattern pattern = Pattern.compile("(\\w+\\([^()]*\\))->(\\w+)");
        Matcher matcher = pattern.matcher(expr);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String methodCall = matcher.group(1);
            String propName = matcher.group(2);
            // Carbon 特殊处理：carbonToday()->year → carbonYear(carbonToday())
            if ("year".equals(propName) && methodCall.startsWith("carbon")) {
                matcher.appendReplacement(sb, "carbonYear(" + methodCall + ")");
            } else {
                matcher.appendReplacement(sb, "getProperty(" + methodCall + ", \"" + propName + "\")");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 编译空合并运算符 ?? → nullCoalescing(a, b)。
     * $a ?? $b 返回 a 如果 a 不为 null，否则返回 b。
     */
    private String compileNullCoalescing(String expr, Set<String> localVars) {
        // 查找 ?? 运算符（不在字符串内）
        int pos = findOperator(expr, "??");
        if (pos < 0) {
            return expr;
        }
        String left = expr.substring(0, pos).trim();
        String right = expr.substring(pos + 2).trim();
        return "nullCoalescing(" + left + ", " + right + ")";
    }

    /**
     * 在表达式中查找运算符位置（跳过字符串内的匹配）。
     * @param expr 表达式
     * @param op 运算符字符串
     * @return 运算符位置，或 -1 如果未找到
     */
    private int findOperator(String expr, String op) {
        boolean inString = false;
        char stringDelimiter = '"';
        for (int i = 0; i < expr.length() - op.length() + 1; i++) {
            char c = expr.charAt(i);
            if (!inString) {
                if (c == '"' || c == '\'') {
                    inString = true;
                    stringDelimiter = c;
                } else if (expr.substring(i, i + op.length()).equals(op)) {
                    return i;
                }
            } else {
                if (c == '\\' && i + 1 < expr.length()) {
                    i++;
                } else if (c == stringDelimiter) {
                    inString = false;
                }
            }
        }
        return -1;
    }

    private String escapeJava(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String parseComponentParams(String params) {
        if (params == null || params.trim().isEmpty()) {
            return "";
        }

        StringBuilder code = new StringBuilder();
        params = params.trim();

        if (params.startsWith("[") && params.endsWith("]")) {
            String inner = params.substring(1, params.length() - 1).trim();
            String[] pairs = inner.split(",");

            for (String pair : pairs) {
                pair = pair.trim();
                if (pair.contains("=>")) {
                    String[] kv = pair.split("=>", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replace("'", "").replace("\"", "");
                        String value = kv[1].trim();

                        if (value.startsWith("'") || value.startsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                            code.append("            componentData.put(\"").append(key).append("\", \"").append(escapeJava(value)).append("\");\n");
                        } else {
                            code.append("            componentData.put(\"").append(key).append("\", ").append(value).append(");\n");
                        }
                    }
                }
            }
        }

        return code.toString();
    }

    /**
     * 从处理后的源代码中提取包名（已移除注释和字符串）
     */
    private String extractPackageName(String processedSourceCode) {
        // 只匹配文件开头的package声明（忽略前导空白）
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
        char stringDelimiter = '"'; // 字符串分隔符，可能是"或'

        while (i < length) {
            char c = sourceCode.charAt(i);

            // 处理注释和字符串状态切换
            if (!inSingleLineComment && !inMultiLineComment && !inString) {
                // 检查单行注释
                if (c == '/' && i + 1 < length && sourceCode.charAt(i + 1) == '/') {
                    inSingleLineComment = true;
                    i += 2;
                    continue;
                }
                // 检查多行注释
                else if (c == '/' && i + 1 < length && sourceCode.charAt(i + 1) == '*') {
                    inMultiLineComment = true;
                    i += 2;
                    continue;
                }
                // 检查字符串开始
                else if (c == '"' || c == '\'') {
                    inString = true;
                    stringDelimiter = c;
                    i++;
                    continue;
                }
            }
            // 单行注释结束（遇到换行）
            else if (inSingleLineComment) {
                if (c == '\n' || c == '\r') {
                    inSingleLineComment = false;
                }
                i++;
                continue;
            }
            // 多行注释结束
            else if (inMultiLineComment) {
                if (c == '*' && i + 1 < length && sourceCode.charAt(i + 1) == '/') {
                    inMultiLineComment = false;
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }
            // 字符串结束
            else if (inString) {
                if (c == stringDelimiter) {
                    // 处理转义的分隔符（如\"或\'）
                    if (i > 0 && sourceCode.charAt(i - 1) != '\\') {
                        inString = false;
                    }
                }
                i++;
                continue;
            }

            // 只保留非注释和非字符串的内容
            result.append(c);
            i++;
        }

        return result.toString();
    }

    private static class DirectiveContext {
        String type;
        int depth;
        List<String> forVars;

        DirectiveContext(String type, int depth) {
            this.type = type;
            this.depth = depth;
            this.forVars = new ArrayList<>();
        }

        DirectiveContext(String type, List<String> forVars) {
            this.type = type;
            this.depth = 0;
            this.forVars = forVars;
        }
    }
}