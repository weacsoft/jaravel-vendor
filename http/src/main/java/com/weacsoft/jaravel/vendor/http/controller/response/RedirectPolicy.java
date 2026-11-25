package com.weacsoft.jaravel.vendor.http.controller.response;

import java.util.Locale;

/**
 * 重定向目标（{@code Location} 响应头）的结构校验。
 *
 * <h3>校验的必要性</h3>
 * <p>
 * {@code Location} 头的值会被浏览器直接解释：协议决定了浏览器的行为形态，
 * 而协议由目标字符串自身决定，不受服务端控制。因此若业务代码把未经校验的字符串
 * 交给 {@link ResponseBuilder#redirect(String)}，就可能产生两类后果：
 * <ul>
 *   <li><b>脚本执行</b>：{@code javascript:...}、{@code data:...} 会被浏览器当作脚本或文档执行；</li>
 *   <li><b>响应头注入</b>：目标中若含 {@code CR/LF}，可在响应头中插入额外头部。</li>
 * </ul>
 * 此外，协议相对写法（{@code //evil.example}）与含反斜杠的写法（{@code /\evil.example}）
 * 会被浏览器当作站外地址，等价于一次开放重定向。
 *
 * <h3>校验规则</h3>
 * <ul>
 *   <li>地址不得为 null 或空白；</li>
 *   <li>地址中不得含控制字符（含 {@code CR}/{@code LF}）；</li>
 *   <li>地址中不得含反斜杠（浏览器会把 {@code \} 当作 {@code /}）；</li>
 *   <li>不得以 {@code //} 开头（协议相对地址）；</li>
 *   <li>带协议时只接受 {@code http} 与 {@code https}。</li>
 * </ul>
 * 相对路径（以 {@code /} 开头）与正常的 http(s) 绝对地址一律放行。
 *
 * <p>
 * 本类只做结构校验，不判断目标主机是否属于本站。目标主机由应用自身决定：
 * 若目标来自请求数据（如 {@code Host} 头），应先固定对外基础地址
 * （{@code jaravel.http.base-url}），使框架生成的地址不依赖请求头。
 */
public final class RedirectPolicy {

    private RedirectPolicy() {
    }

    /**
     * 校验重定向目标地址。
     *
     * @param url 重定向目标地址
     * @return 去除首尾空白后的地址（校验通过时原样返回）
     * @throws IllegalArgumentException 地址为空、含控制字符、含反斜杠、
     *                                  为协议相对写法或协议不受支持时抛出
     */
    public static String validate(String url) {
        if (url == null) {
            throw new IllegalArgumentException("重定向地址不能为 null");
        }
        // 控制字符检查在去空白之前执行：String.trim() 会丢弃首尾的控制字符
        // （含 NUL），先检查才能把这类地址当作非法输入拒绝，而不是悄悄改写。
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new IllegalArgumentException("重定向地址不得包含控制字符");
            }
        }
        String target = url.trim();
        if (target.isEmpty()) {
            throw new IllegalArgumentException("重定向地址不能为空");
        }
        if (target.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("重定向地址不得包含反斜杠");
        }
        if (target.startsWith("//")) {
            throw new IllegalArgumentException("重定向地址不得使用协议相对写法（//host）");
        }
        if (hasScheme(target)) {
            String scheme = target.substring(0, target.indexOf(':')).toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException("重定向地址只支持 http/https 协议，实际为: " + scheme);
            }
        }
        return target;
    }

    /**
     * 判断地址是否带有协议（scheme）。
     * <p>
     * 依据 RFC 3986：{@code scheme ":"} 出现在路径、查询串或片段之前，且协议名只由
     * 字母、数字、{@code +}、{@code -}、{@code .} 组成。
     *
     * @param target 已去除首尾空白的地址
     * @return 带协议返回 true
     */
    private static boolean hasScheme(String target) {
        int colon = target.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        int slash = target.indexOf('/');
        if (slash >= 0 && slash < colon) {
            return false;
        }
        int question = target.indexOf('?');
        if (question >= 0 && question < colon) {
            return false;
        }
        int hash = target.indexOf('#');
        if (hash >= 0 && hash < colon) {
            return false;
        }
        for (int i = 0; i < colon; i++) {
            char c = target.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (i > 0 && ((c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.'));
            if (!valid) {
                return false;
            }
        }
        return true;
    }
}
