package com.weacsoft.jaravel.vendor.jblade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PHP/Blade 表达式 → Java 代码翻译器。
 * <p>
 * 将模板中的 PHP 风格表达式（如 {@code $user->name}、{@code count($list) > 0}、
 * {@code route('login', ['id' => 1])}）翻译为调用 {@link BladeTemplate}
 * 运行时辅助方法的 Java 表达式代码。
 * <p>
 * 支持：
 * <ul>
 *   <li>变量 $var、属性 $a->b、方法 $a->m(...)、下标 $a['k'] / $a[0]</li>
 *   <li>字面量：数字、'字符串'、"字符串"（含 $var / {$expr} 插值）、true/false/null、数组 [..]</li>
 *   <li>运算符：. (连接) + - * / % ! && || and or == != <> === !== < > <= >= ?? ?: ? : = .= += -= ++ --</li>
 *   <li>函数调用：内置函数直译，未知函数 → BladeFunctions 动态调用</li>
 *   <li>静态调用：URL::asset、Carbon::parse 等</li>
 * </ul>
 */
public class PhpExpressionTranslator {

    /** 翻译结果：Java 代码 + 静态类型提示 */
    public static class Expr {
        public final String code;
        /** 结果已是 boolean */
        public final boolean isBoolean;
        /** 结果已是 String */
        public final boolean isString;
        /** 编译期字符串字面量值（非字面量为 null） */
        public final String literalString;

        Expr(String code) {
            this(code, false, false, null);
        }

        Expr(String code, boolean isBoolean, boolean isString, String literalString) {
            this.code = code;
            this.isBoolean = isBoolean;
            this.isString = isString;
            this.literalString = literalString;
        }

        /** 包装为 boolean 代码 */
        public String asBoolean() {
            return isBoolean ? code : "toBoolean(" + code + ")";
        }

        /** 包装为 String 代码 */
        public String asString() {
            return isString ? code : "String.valueOf(" + code + ")";
        }

        /** 包装为 Object 代码（boolean 需要装箱提示） */
        public String asObject() {
            if (isBoolean) {
                return "Boolean.valueOf(" + code + ")";
            }
            return code;
        }
    }

    /* ==================== 词法分析 ==================== */

    private static final int T_EOF = 0;
    private static final int T_VAR = 1;      // $name
    private static final int T_IDENT = 2;    // 标识符
    private static final int T_NUMBER = 3;
    private static final int T_STRING_SQ = 4; // '...'
    private static final int T_STRING_DQ = 5; // "..."
    private static final int T_OP = 6;

    private static class Token {
        final int type;
        final String text;

        Token(int type, String text) {
            this.type = type;
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private final List<Token> tokens = new ArrayList<>();
    private int pos = 0;
    private final String source;

    public PhpExpressionTranslator(String expression) {
        this.source = expression == null ? "" : expression.trim();
        tokenize();
    }

    /**
     * 快捷方法：翻译一个表达式。
     */
    public static Expr translate(String phpExpression) {
        return new PhpExpressionTranslator(phpExpression).parse();
    }

    /**
     * 快捷方法：按顶层逗号切分实参列表并分别翻译。
     */
    public static List<Expr> translateArgs(String argsSource) {
        List<Expr> result = new ArrayList<>();
        for (String part : splitTopLevel(argsSource)) {
            if (!part.trim().isEmpty()) {
                result.add(translate(part));
            }
        }
        return result;
    }

    /**
     * 按不在括号/引号内的逗号切分。
     */
    public static List<String> splitTopLevel(String src) {
        List<String> parts = new ArrayList<>();
        if (src == null) {
            return parts;
        }
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
            } else if (c == ',' && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        return parts;
    }

    private void tokenize() {
        String s = source;
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '$' && i + 1 < n && (Character.isLetter(s.charAt(i + 1)) || s.charAt(i + 1) == '_')) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) {
                    j++;
                }
                tokens.add(new Token(T_VAR, s.substring(i + 1, j)));
                i = j;
                continue;
            }
            if (Character.isLetter(c) || c == '_' || c == '\\') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_' || s.charAt(j) == '\\')) {
                    j++;
                }
                tokens.add(new Token(T_IDENT, s.substring(i, j)));
                i = j;
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1))
                    && !prevTokenIsValue())) {
                int j = i;
                boolean hasDot = false;
                while (j < n && (Character.isDigit(s.charAt(j)) || (s.charAt(j) == '.' && !hasDot
                        && j + 1 < n && Character.isDigit(s.charAt(j + 1))))) {
                    if (s.charAt(j) == '.') {
                        hasDot = true;
                    }
                    j++;
                }
                tokens.add(new Token(T_NUMBER, s.substring(i, j)));
                i = j;
                continue;
            }
            if (c == '\'' || c == '"') {
                StringBuilder sb = new StringBuilder();
                int j = i + 1;
                while (j < n && s.charAt(j) != c) {
                    if (s.charAt(j) == '\\' && j + 1 < n) {
                        sb.append(s.charAt(j)).append(s.charAt(j + 1));
                        j += 2;
                    } else {
                        sb.append(s.charAt(j));
                        j++;
                    }
                }
                tokens.add(new Token(c == '\'' ? T_STRING_SQ : T_STRING_DQ, sb.toString()));
                i = j + 1;
                continue;
            }
            // 多字符运算符（长的优先）
            String[] ops = {"===", "!==", "<=>", "??=", "?->", "<<", ">>", "==", "!=", "<>", "<=", ">=",
                    "&&", "||", "??", "?:", "->", "=>", "::", "++", "--", "+=", "-=", "*=", "/=", ".="};
            String matched = null;
            for (String op : ops) {
                if (s.startsWith(op, i)) {
                    matched = op;
                    break;
                }
            }
            if (matched != null) {
                tokens.add(new Token(T_OP, matched));
                i += matched.length();
                continue;
            }
            tokens.add(new Token(T_OP, String.valueOf(c)));
            i++;
        }
        tokens.add(new Token(T_EOF, "<eof>"));
    }

    private boolean prevTokenIsValue() {
        if (tokens.isEmpty()) {
            return false;
        }
        Token t = tokens.get(tokens.size() - 1);
        return t.type == T_VAR || t.type == T_NUMBER || t.type == T_IDENT
                || (t.type == T_OP && (")".equals(t.text) || "]".equals(t.text)));
    }

    /* ==================== 语法分析 ==================== */

    private Token peek() {
        return tokens.get(pos);
    }

    private Token next() {
        return tokens.get(pos++);
    }

    private boolean matchOp(String op) {
        if (peek().type == T_OP && peek().text.equals(op)) {
            pos++;
            return true;
        }
        return false;
    }

    private boolean matchIdent(String ident) {
        if (peek().type == T_IDENT && peek().text.equalsIgnoreCase(ident)) {
            pos++;
            return true;
        }
        return false;
    }

    private void expectOp(String op) {
        if (!matchOp(op)) {
            throw new IllegalArgumentException(
                    "jblade 表达式语法错误：期望 '" + op + "'，实际 '" + peek().text + "'，表达式: " + source);
        }
    }

    /**
     * 翻译完整表达式。
     */
    public Expr parse() {
        if (tokens.size() == 1) {
            return new Expr("null");
        }
        Expr e = parseAssignment();
        if (peek().type != T_EOF) {
            throw new IllegalArgumentException(
                    "jblade 表达式语法错误：多余内容 '" + peek().text + "'，表达式: " + source);
        }
        return e;
    }

    private Expr parseAssignment() {
        // 赋值：$x = / .= / += / -= / *= / /=
        int save = pos;
        if (peek().type == T_VAR) {
            String varName = peek().text;
            pos++;
            if (peek().type == T_OP) {
                String op = peek().text;
                if ("=".equals(op) && !"==".equals(op)) {
                    pos++;
                    Expr value = parseAssignment();
                    return new Expr("setVar(\"" + varName + "\", " + value.asObject() + ")");
                }
                if (".=".equals(op)) {
                    pos++;
                    Expr value = parseAssignment();
                    return new Expr("setVar(\"" + varName + "\", concat(v(\"" + varName + "\"), "
                            + value.asObject() + "))");
                }
                if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op) || "/=".equals(op)) {
                    pos++;
                    Expr value = parseAssignment();
                    String helper = "+=".equals(op) ? "plus" : "-=".equals(op) ? "minus"
                            : "*=".equals(op) ? "mul" : "div";
                    return new Expr("setVar(\"" + varName + "\", " + helper + "(v(\"" + varName + "\"), "
                            + value.asObject() + "))");
                }
            }
            pos = save;
        }
        return parseTernary();
    }

    private Expr parseTernary() {
        Expr cond = parseNullCoalesce();
        if (matchOp("?:")) {
            Expr other = parseTernary();
            return new Expr("elvis(" + cond.asObject() + ", " + other.asObject() + ")");
        }
        if (matchOp("?")) {
            Expr ifTrue = parseTernary();
            expectOp(":");
            Expr ifFalse = parseTernary();
            return new Expr("(" + cond.asBoolean() + " ? (Object)(" + ifTrue.asObject() + ") : (Object)("
                    + ifFalse.asObject() + "))");
        }
        return cond;
    }

    private Expr parseNullCoalesce() {
        Expr left = parseLogicalOr();
        if (matchOp("??")) {
            Expr right = parseNullCoalesce();
            return new Expr("nullCoalesce(" + left.asObject() + ", " + right.asObject() + ")");
        }
        return left;
    }

    private Expr parseLogicalOr() {
        Expr left = parseLogicalAnd();
        while (true) {
            if (peek().type == T_OP && "||".equals(peek().text)) {
                pos++;
            } else if (peek().type == T_IDENT && "or".equalsIgnoreCase(peek().text)) {
                pos++;
            } else {
                return left;
            }
            Expr right = parseLogicalAnd();
            left = new Expr("(" + left.asBoolean() + " || " + right.asBoolean() + ")", true, false, null);
        }
    }

    private Expr parseLogicalAnd() {
        Expr left = parseEquality();
        while (true) {
            if (peek().type == T_OP && "&&".equals(peek().text)) {
                pos++;
            } else if (peek().type == T_IDENT && "and".equalsIgnoreCase(peek().text)) {
                pos++;
            } else {
                return left;
            }
            Expr right = parseEquality();
            left = new Expr("(" + left.asBoolean() + " && " + right.asBoolean() + ")", true, false, null);
        }
    }

    private Expr parseEquality() {
        Expr left = parseRelational();
        while (peek().type == T_OP) {
            String op = peek().text;
            String helper;
            switch (op) {
                case "==": helper = "eq"; break;
                case "!=":
                case "<>": helper = "neq"; break;
                case "===": helper = "identical"; break;
                case "!==": helper = "notIdentical"; break;
                default: return left;
            }
            pos++;
            Expr right = parseRelational();
            left = new Expr(helper + "(" + left.asObject() + ", " + right.asObject() + ")", true, false, null);
        }
        return left;
    }

    private Expr parseRelational() {
        Expr left = parseAdditive();
        while (peek().type == T_OP) {
            String op = peek().text;
            String helper;
            switch (op) {
                case "<": helper = "lt"; break;
                case ">": helper = "gt"; break;
                case "<=": helper = "lte"; break;
                case ">=": helper = "gte"; break;
                default: return left;
            }
            pos++;
            Expr right = parseAdditive();
            left = new Expr(helper + "(" + left.asObject() + ", " + right.asObject() + ")", true, false, null);
        }
        return left;
    }

    private Expr parseAdditive() {
        Expr left = parseMultiplicative();
        while (peek().type == T_OP) {
            String op = peek().text;
            if ("+".equals(op)) {
                pos++;
                Expr right = parseMultiplicative();
                left = new Expr("plus(" + left.asObject() + ", " + right.asObject() + ")");
            } else if ("-".equals(op)) {
                pos++;
                Expr right = parseMultiplicative();
                left = new Expr("minus(" + left.asObject() + ", " + right.asObject() + ")");
            } else if (".".equals(op)) {
                pos++;
                Expr right = parseMultiplicative();
                left = new Expr("concat(" + left.asObject() + ", " + right.asObject() + ")", false, true, null);
            } else {
                return left;
            }
        }
        return left;
    }

    private Expr parseMultiplicative() {
        Expr left = parseUnary();
        while (peek().type == T_OP) {
            String op = peek().text;
            String helper;
            switch (op) {
                case "*": helper = "mul"; break;
                case "/": helper = "div"; break;
                case "%": helper = "mod"; break;
                default: return left;
            }
            pos++;
            Expr right = parseUnary();
            left = new Expr(helper + "(" + left.asObject() + ", " + right.asObject() + ")");
        }
        return left;
    }

    private Expr parseUnary() {
        if (matchOp("!")) {
            Expr operand = parseUnary();
            return new Expr("(!" + operand.asBoolean() + ")", true, false, null);
        }
        if (matchOp("-")) {
            Expr operand = parseUnary();
            return new Expr("neg(" + operand.asObject() + ")");
        }
        if (matchOp("+")) {
            return parseUnary();
        }
        // 前缀 ++ / --
        if (peek().type == T_OP && ("++".equals(peek().text) || "--".equals(peek().text))) {
            String op = next().text;
            if (peek().type == T_VAR) {
                String name = next().text;
                String helper = "++".equals(op) ? "plus" : "minus";
                return new Expr("setVar(\"" + name + "\", " + helper + "(v(\"" + name + "\"), 1L))");
            }
            throw new IllegalArgumentException("jblade 表达式语法错误：" + op + " 后必须是变量，表达式: " + source);
        }
        return parsePostfix();
    }

    private Expr parsePostfix() {
        Expr expr = parsePrimary();
        while (true) {
            Token t = peek();
            if (t.type != T_OP) {
                return expr;
            }
            if ("->".equals(t.text) || "?->".equals(t.text)) {
                pos++;
                if (peek().type != T_IDENT) {
                    throw new IllegalArgumentException("jblade 表达式语法错误：-> 后必须是属性/方法名，表达式: " + source);
                }
                String member = next().text;
                if (matchOp("(")) {
                    List<Expr> args = parseArgList();
                    StringBuilder sb = new StringBuilder("invokeMethod(" + expr.asObject() + ", \"" + member + "\"");
                    for (Expr a : args) {
                        sb.append(", ").append(a.asObject());
                    }
                    sb.append(")");
                    expr = new Expr(sb.toString());
                } else {
                    expr = new Expr("getProperty(" + expr.asObject() + ", \"" + member + "\")");
                }
            } else if ("[".equals(t.text)) {
                pos++;
                Expr key = parseAssignment();
                expectOp("]");
                expr = new Expr("arrGet(" + expr.asObject() + ", " + key.asObject() + ")");
            } else if ("++".equals(t.text) || "--".equals(t.text)) {
                // 后缀自增（返回值语义简化为前缀）
                pos++;
                String code = expr.code;
                if (code.startsWith("v(\"") && code.endsWith("\")")) {
                    String name = code.substring(3, code.length() - 2);
                    String helper = "++".equals(t.text) ? "plus" : "minus";
                    expr = new Expr("setVar(\"" + name + "\", " + helper + "(v(\"" + name + "\"), 1L))");
                } else {
                    throw new IllegalArgumentException("jblade 表达式语法错误：++/-- 只支持变量，表达式: " + source);
                }
            } else {
                return expr;
            }
        }
    }

    /** 内置直译函数（BladeTemplate 中有同名 Java 方法且签名兼容 Object 实参） */
    private static final Set<String> DIRECT_FUNCTIONS = new HashSet<>(Arrays.asList(
            "count", "empty", "intval", "json_encode", "number_format",
            "strtoupper", "strtolower", "ucfirst", "trim", "strlen", "e"
    ));

    private Expr parsePrimary() {
        Token t = next();
        switch (t.type) {
            case T_VAR:
                return new Expr("v(\"" + t.text + "\")");
            case T_NUMBER:
                if (t.text.contains(".")) {
                    return new Expr(t.text);
                }
                return new Expr(t.text + "L");
            case T_STRING_SQ: {
                String value = unescapePhpSingle(t.text);
                return new Expr(javaStringLiteral(value), false, true, value);
            }
            case T_STRING_DQ:
                return parseInterpolatedString(t.text);
            case T_IDENT:
                return parseIdentExpr(t.text);
            case T_OP:
                if ("(".equals(t.text)) {
                    Expr inner = parseAssignment();
                    expectOp(")");
                    return new Expr("(" + inner.code + ")", inner.isBoolean, inner.isString, inner.literalString);
                }
                if ("[".equals(t.text)) {
                    return parseArrayLiteral("]");
                }
                break;
            default:
                break;
        }
        throw new IllegalArgumentException("jblade 表达式语法错误：意外的 '" + t.text + "'，表达式: " + source);
    }

    private Expr parseIdentExpr(String ident) {
        // 关键字
        if ("true".equalsIgnoreCase(ident)) {
            return new Expr("true", true, false, null);
        }
        if ("false".equalsIgnoreCase(ident)) {
            return new Expr("false", true, false, null);
        }
        if ("null".equalsIgnoreCase(ident)) {
            return new Expr("null");
        }
        if ("array".equalsIgnoreCase(ident) && matchOp("(")) {
            return parseArrayLiteral(")");
        }
        // 静态调用 Class::method(...)
        if (matchOp("::")) {
            if (peek().type != T_IDENT) {
                throw new IllegalArgumentException("jblade 表达式语法错误：:: 后必须是成员名，表达式: " + source);
            }
            String member = next().text;
            List<Expr> args = new ArrayList<>();
            boolean isCall = matchOp("(");
            if (isCall) {
                args = parseArgList();
            }
            return translateStaticCall(ident, member, args, isCall);
        }
        // 函数调用
        if (matchOp("(")) {
            List<Expr> args = parseArgList();
            return translateFunctionCall(ident, args);
        }
        // PHP 常量
        if ("PHP_EOL".equals(ident)) {
            return new Expr("\"\\n\"", false, true, "\n");
        }
        // 裸标识符：按字符串常量处理（PHP 宽松行为）
        return new Expr(javaStringLiteral(ident), false, true, ident);
    }

    private List<Expr> parseArgList() {
        List<Expr> args = new ArrayList<>();
        if (matchOp(")")) {
            return args;
        }
        while (true) {
            args.add(parseAssignment());
            if (matchOp(")")) {
                return args;
            }
            expectOp(",");
        }
    }

    /** 解析数组字面量（起始 [ 或 array( 已消费），closer 为 ] 或 ) */
    private Expr parseArrayLiteral(String closer) {
        List<String> keys = new ArrayList<>();
        List<Expr> values = new ArrayList<>();
        boolean assoc = false;
        if (!matchOp(closer)) {
            while (true) {
                Expr first = parseAssignment();
                if (matchOp("=>")) {
                    assoc = true;
                    Expr value = parseAssignment();
                    keys.add(first.asObject());
                    values.add(value);
                } else {
                    keys.add(null);
                    values.add(first);
                }
                if (matchOp(closer)) {
                    break;
                }
                expectOp(",");
                if (matchOp(closer)) {
                    break; // 允许尾逗号
                }
            }
        }
        if (assoc) {
            StringBuilder sb = new StringBuilder("map(");
            int autoIndex = 0;
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                String key = keys.get(i) != null ? keys.get(i) : String.valueOf(autoIndex++);
                sb.append(key).append(", ").append(values.get(i).asObject());
            }
            sb.append(")");
            return new Expr(sb.toString());
        }
        StringBuilder sb = new StringBuilder("list(");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i).asObject());
        }
        sb.append(")");
        return new Expr(sb.toString());
    }

    private Expr translateFunctionCall(String name, List<Expr> args) {
        switch (name) {
            case "route":
                if (args.size() <= 1) {
                    return new Expr("route(" + (args.isEmpty() ? "\"\"" : args.get(0).asString()) + ")",
                            false, true, null);
                }
                return new Expr("routeAny(" + args.get(0).asObject() + ", " + args.get(1).asObject() + ")",
                        false, true, null);
            case "asset":
                return new Expr("asset(" + oneArg(args).asString() + ")", false, true, null);
            case "url":
                return new Expr("url(" + oneArg(args).asString() + ")", false, true, null);
            case "session":
                return new Expr("session(" + oneArg(args).asString() + ")");
            case "old":
                if (args.size() >= 2) {
                    return new Expr("old(" + args.get(0).asString() + ", " + args.get(1).asObject() + ")");
                }
                return new Expr("old(" + oneArg(args).asString() + ")");
            case "csrf_field":
                return new Expr("csrf_field()", false, true, null);
            case "csrf_token":
                return new Expr("csrf_token()", false, true, null);
            case "isset":
                return new Expr("isset(" + oneArg(args).asObject() + ")", true, false, null);
            case "sprintf": {
                StringBuilder sb = new StringBuilder("sprintf(" + oneArg(args).asString());
                for (int i = 1; i < args.size(); i++) {
                    sb.append(", ").append(args.get(i).asObject());
                }
                sb.append(")");
                return new Expr(sb.toString(), false, true, null);
            }
            case "str_replace":
                requireArgs(args, 3, name);
                return new Expr("str_replace(" + args.get(0).asString() + ", " + args.get(1).asString()
                        + ", " + args.get(2).asString() + ")", false, true, null);
            case "implode":
                requireArgs(args, 2, name);
                return new Expr("implode(" + args.get(0).asString() + ", " + args.get(1).asObject() + ")",
                        false, true, null);
            case "substr": {
                StringBuilder sb = new StringBuilder("substr(" + args.get(0).asObject()
                        + ", " + args.get(1).asObject());
                for (int i = 2; i < args.size(); i++) {
                    sb.append(", ").append(args.get(i).asObject());
                }
                sb.append(")");
                return new Expr(sb.toString(), false, true, null);
            }
            case "ceil":
                return new Expr("ceil(toNumber(" + oneArg(args).asObject() + ").doubleValue())");
            case "floor":
                return new Expr("floor(toNumber(" + oneArg(args).asObject() + ").doubleValue())");
            case "__":
            case "trans":
                // 无翻译系统：注册了则调用，否则原样返回 key
                return new Expr("BladeFunctions.callOrDefault(\"" + name + "\", "
                        + oneArg(args).asObject() + ", " + oneArg(args).asObject() + ")");
            default:
                break;
        }
        if (DIRECT_FUNCTIONS.contains(name)) {
            StringBuilder sb = new StringBuilder(name).append("(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(args.get(i).asObject());
            }
            sb.append(")");
            boolean isBool = "empty".equals(name);
            return new Expr(sb.toString(), isBool, false, null);
        }
        // 未知函数 → BladeFunctions 动态调用（外部合并注册）
        StringBuilder sb = new StringBuilder("fn(" + javaStringLiteral(name));
        for (Expr a : args) {
            sb.append(", ").append(a.asObject());
        }
        sb.append(")");
        return new Expr(sb.toString());
    }

    private Expr translateStaticCall(String className, String member, List<Expr> args, boolean isCall) {
        String shortName = className.contains("\\")
                ? className.substring(className.lastIndexOf('\\') + 1) : className;
        if ("URL".equals(shortName) && "asset".equals(member)) {
            return new Expr("asset(" + oneArg(args).asString() + ")", false, true, null);
        }
        if ("Carbon".equals(shortName)) {
            if ("parse".equals(member)) {
                return new Expr("carbonParse(" + oneArg(args).asObject() + ")");
            }
            if ("today".equals(member) || "now".equals(member)) {
                return new Expr("carbonToday()");
            }
        }
        // 其他静态调用 → 动态函数 "Class::member"
        StringBuilder sb = new StringBuilder("fn(" + javaStringLiteral(shortName + "::" + member));
        for (Expr a : args) {
            sb.append(", ").append(a.asObject());
        }
        sb.append(")");
        return new Expr(sb.toString());
    }

    private static Expr oneArg(List<Expr> args) {
        if (args.isEmpty()) {
            return new Expr("null");
        }
        return args.get(0);
    }

    private static void requireArgs(List<Expr> args, int count, String name) {
        if (args.size() < count) {
            throw new IllegalArgumentException("jblade：函数 " + name + " 需要 " + count + " 个参数");
        }
    }

    /* ==================== 字符串处理 ==================== */

    /**
     * 双引号字符串：处理转义 + $var / {$expr} 插值。
     */
    private Expr parseInterpolatedString(String raw) {
        List<String> parts = new ArrayList<>();
        StringBuilder lit = new StringBuilder();
        int i = 0;
        int n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char e = raw.charAt(i + 1);
                switch (e) {
                    case 'n': lit.append('\n'); break;
                    case 't': lit.append('\t'); break;
                    case 'r': lit.append('\r'); break;
                    case '"': lit.append('"'); break;
                    case '\\': lit.append('\\'); break;
                    case '$': lit.append('$'); break;
                    default: lit.append('\\').append(e);
                }
                i += 2;
                continue;
            }
            if (c == '{' && i + 1 < n && raw.charAt(i + 1) == '$') {
                int depth = 1;
                int j = i + 1;
                while (j < n && depth > 0) {
                    if (raw.charAt(j) == '{') {
                        depth++;
                    } else if (raw.charAt(j) == '}') {
                        depth--;
                        if (depth == 0) {
                            break;
                        }
                    }
                    j++;
                }
                String inner = raw.substring(i + 1, j);
                if (lit.length() > 0) {
                    parts.add(javaStringLiteral(lit.toString()));
                    lit.setLength(0);
                }
                parts.add(translate(inner).asObject());
                i = j + 1;
                continue;
            }
            if (c == '$' && i + 1 < n && (Character.isLetter(raw.charAt(i + 1)) || raw.charAt(i + 1) == '_')) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(raw.charAt(j)) || raw.charAt(j) == '_')) {
                    j++;
                }
                if (lit.length() > 0) {
                    parts.add(javaStringLiteral(lit.toString()));
                    lit.setLength(0);
                }
                parts.add("v(\"" + raw.substring(i + 1, j) + "\")");
                i = j;
                continue;
            }
            lit.append(c);
            i++;
        }
        if (parts.isEmpty()) {
            String value = lit.toString();
            return new Expr(javaStringLiteral(value), false, true, value);
        }
        if (lit.length() > 0) {
            parts.add(javaStringLiteral(lit.toString()));
        }
        if (parts.size() == 1) {
            return new Expr("String.valueOf(" + parts.get(0) + ")", false, true, null);
        }
        StringBuilder sb = new StringBuilder("concat(");
        for (int k = 0; k < parts.size(); k++) {
            if (k > 0) {
                sb.append(", ");
            }
            sb.append(parts.get(k));
        }
        sb.append(")");
        return new Expr(sb.toString(), false, true, null);
    }

    private static String unescapePhpSingle(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char e = raw.charAt(i + 1);
                if (e == '\'' || e == '\\') {
                    sb.append(e);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Java 字符串字面量转义。
     */
    public static String javaStringLiteral(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
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
        sb.append("\"");
        return sb.toString();
    }
}
