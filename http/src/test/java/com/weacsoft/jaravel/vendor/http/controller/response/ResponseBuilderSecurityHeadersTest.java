package com.weacsoft.jaravel.vendor.http.controller.response;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件响应头安全回归测试（审计 L4 / N3）。
 * <p>
 * <ul>
 *   <li><b>N3</b>：内联展示/下载用户可上传的内容必须带 {@code X-Content-Type-Options: nosniff}，
 *       否则浏览器嗅探内容类型后可能把 {@code .html}/{@code .svg} 当页面渲染 → 同源存储型 XSS；</li>
 *   <li><b>L4</b>：私有文件不得按 {@code public, max-age} 下发（会被 CDN/共享代理缓存给他人）；</li>
 *   <li>文件名需做头注入清洗并同时给出 RFC 5987 的 {@code filename*}（中文名否则乱码）。</li>
 * </ul>
 */
class ResponseBuilderSecurityHeadersTest {

    private static String header(Response response, String name) {
        List<String> values = response.getHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    @Test
    void inlinePreviewIsNoStoreAndNoSniff() {
        Response response = ResponseBuilder.inlineFile(
                "x".getBytes(), "text/html", "private, no-store", "证件.pdf");

        assertEquals("private, no-store", header(response, "Cache-Control"),
                "私有文件必须禁止共享缓存");
        assertEquals("nosniff", header(response, "X-Content-Type-Options"),
                "内联展示必须禁止内容嗅探");
        String disposition = header(response, "Content-Disposition");
        assertTrue(disposition.startsWith("inline;"), disposition);
        assertTrue(disposition.contains("filename*=UTF-8''"), "非 ASCII 文件名需 RFC 5987 形式: " + disposition);
        assertTrue(disposition.contains("filename="), disposition);
    }

    @Test
    void publicStaticFileKeepsPublicCache() {
        Response response = ResponseBuilder.staticFile("x".getBytes(), "text/css", 3600, "app.css");
        assertEquals("public, max-age=3600", header(response, "Cache-Control"),
                "公共静态资源仍应可缓存（不要误伤）");
    }

    @Test
    void downloadSanitizesFilenameAgainstHeaderInjection() {
        Response response = ResponseBuilder.file("x".getBytes(), "bad\"\r\nSet-Cookie: a=b.pdf");

        String disposition = header(response, "Content-Disposition");
        assertTrue(disposition.startsWith("attachment;"), disposition);
        assertFalse(disposition.contains("\r"), "不得允许 CR 进入响应头: " + disposition);
        assertFalse(disposition.contains("\n"), "不得允许 LF 进入响应头: " + disposition);
        // CR/LF 与引号都被替换为下划线 → 注入内容只能留在 filename 参数的引号内，无法产生新响应头
        assertTrue(disposition.contains("bad___Set-Cookie"), "危险字符应被替换: " + disposition);
        assertTrue(disposition.contains("filename*=UTF-8''"), disposition);
        assertEquals("nosniff", header(response, "X-Content-Type-Options"));
    }
}