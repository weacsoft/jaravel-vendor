package com.weacsoft.jaravel.jblade;

import com.weacsoft.jaravel.utils.memory.MemoryClassLoader;
import com.weacsoft.jaravel.utils.memory.MemoryFileManager;
import com.weacsoft.jaravel.utils.memory.SourceCodeJavaFileObject;
import org.springframework.core.io.ClassPathResource;

import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BladeCompiler {
    //注释
    private static final Pattern COMMENT_PATTERN = Pattern.compile("\\{\\{--.*?--\\}\\}", Pattern.DOTALL);
    //输出
    private static final Pattern ECHO_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*\\}\\}");
    //命令
    private static final Pattern DIRECTIVE_PATTERN = Pattern.compile("@(\\w+)\\s*(?:\\((.*?)\\))?");
    //参数
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$(\\w+)");

    private final String templateDir;
    private final MemoryClassLoader classLoader;

    private final String suffix;

    public BladeCompiler(String templateDir, MemoryClassLoader classLoader) {
        this(templateDir, classLoader, ".jblade");
    }

    public BladeCompiler(String templateDir, MemoryClassLoader classLoader, String suffix) {
        this.templateDir = templateDir;
        this.classLoader = classLoader;
        this.suffix = suffix;
    }

    /**
     * 编译一个文件的代码
     *
     * @param templateName 模板文件名
     */
    public String compile(String templateName) throws IOException {
        String templatePath = templateDir + File.separator + templateName.replace(".", File.separator) + suffix;
        InputStream resource = new ClassPathResource(templatePath).getInputStream();
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
        javaCode.append("import com.weacsoft.jaravel.jblade.*;\n");
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
                        code.append("        if (").append(convertExpression(args, localVars)).append(") {\n");
                        directiveStack.push(new DirectiveContext("if", 0));
                        break;
                    case "elseif":
                        code.append("        } else if (").append(convertExpression(args, localVars)).append(") {\n");
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
                            code.append("        for (").append(convertExpressionWithoutToBoolean(forParts[0], localVars)).append("; ")
                                    .append(convertExpressionWithoutToBoolean(forParts[1], localVars)).append("; ")
                                    .append(convertExpressionWithoutToBoolean(forParts[2], localVars)).append(") {\n");
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
                                collection = convertExpression(collectionExpr, localVars);
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
                            String sectionValue = parts[1].trim().replace("'", "").replace("\"", "");
                            code.append("            ctx.setSection(\"").append(sectionName).append("\", \"").append(escapeJava(sectionValue)).append("\");\n");
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

                switch (directive) {
                    case "extends":
                    case "section":
                    case "endsection":
                        break;
                    case "yield":
                        String yieldName = args.replace("'", "").replace("\"", "");
                        code.append("        Consumer<Writer> renderer = ctx.getSectionRenderer(\"").append(yieldName).append("\");\n");
                        code.append("        if (renderer != null) {\n");
                        code.append("            renderer.accept(writer);\n");
                        code.append("        } else {\n");
                        code.append("            String yieldContent = ctx.getSection(\"").append(yieldName).append("\");\n");
                        code.append("            if (yieldContent != null) {\n");
                        code.append("                write(writer, yieldContent);\n");
                        code.append("            }\n");
                        code.append("        }\n");
                        break;
                    case "component":
                    case "endcomponent":
                    case "slot":
                    case "endslot":
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
                String converted = convertExpressionWithoutToBoolean(expression, localVars);
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

    private String convertExpression(String expr, Set<String> localVars) {
        String result = expr.trim();

        Matcher varMatcher = VAR_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (varMatcher.find()) {
            String varName = varMatcher.group(1);
            if (localVars.contains(varName)) {
                varMatcher.appendReplacement(sb, varName);
            } else {
                varMatcher.appendReplacement(sb, "toBoolean(ctx.getVariable(\"" + varName + "\"))");
            }
        }
        varMatcher.appendTail(sb);

        return sb.toString();
    }

    private String convertExpressionWithoutToBoolean(String expr, Set<String> localVars) {
        String result = expr.trim();

        Matcher varMatcher = VAR_PATTERN.matcher(result);
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