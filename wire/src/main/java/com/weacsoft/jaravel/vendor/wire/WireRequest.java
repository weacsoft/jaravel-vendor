package com.weacsoft.jaravel.vendor.wire;

import com.weacsoft.jaravel.vendor.json.Json;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URLDecoder;
import java.util.*;

/**
 * Wire 更新请求，从前端 POST 的 JSON 中解析。
 * <p>
 * 请求格式：
 * <pre>{@code
 * {
 *   "snapshot": "base64编码的组件状态",
 *   "action": "save",
 *   "params": {"title": "新标题", "content": "新内容"},
 *   "sections": ["content", "sidebar"]
 * }
 * }</pre>
 */
public class WireRequest {

    private final String snapshot;
    private final String action;
    private final Map<String, Object> params;
    private final List<String> sections;

    public WireRequest(String snapshot, String action, Map<String, Object> params, List<String> sections) {
        this.snapshot = snapshot;
        this.action = action;
        this.params = params != null ? params : new HashMap<>();
        this.sections = sections != null ? sections : new ArrayList<>();
    }

    /**
     * 从 Jaravel Request 解析 Wire 请求体。
     *
     * @param request HTTP 请求
     * @return WireRequest 实例
     */
    @SuppressWarnings("unchecked")
    public static WireRequest from(Request request) {
        try {
            String body = request.input("wire_body");
            if (body == null || body.isEmpty()) {
                body = request.get("wire_body", "");
            }
            if (body == null || body.isEmpty()) {
                body = readWireBodyFromInputStream(request);
            }
            if (body == null || body.isEmpty()) {
                throw new IllegalStateException("缺少 wire_body 参数");
            }
            Map<String, Object> data = Json.parseToMap(body);

            String snapshot = (String) data.get("snapshot");
            String action = (String) data.get("action");

            // 兼容 params 可能是 ArrayList（Jackson 解析空对象 {} 时的问题）
            Object paramsObj = data.get("params");
            Map<String, Object> params;
            if (paramsObj instanceof Map) {
                params = (Map<String, Object>) paramsObj;
            } else if (paramsObj instanceof java.util.List) {
                // Jackson 将 {} 解析为空 ArrayList 的情况
                params = new java.util.HashMap<>();
            } else {
                params = new java.util.HashMap<>();
            }

            // 兼容 sections 可能是 ArrayList 的情况
            Object sectionsObj = data.get("sections");
            List<String> sections;
            if (sectionsObj instanceof List) {
                sections = (List<String>) sectionsObj;
            } else {
                sections = new ArrayList<>();
            }

            return new WireRequest(snapshot, action, params, sections);
        } catch (Exception e) {
            throw new RuntimeException("解析 Wire 请求失败", e);
        }
    }

    private static String readWireBodyFromInputStream(Request request) {
        try {
            HttpServletRequest httpReq = request.getRequest();
            if (httpReq == null) return null;
            String contentType = httpReq.getContentType();
            if (contentType == null || !contentType.contains("application/x-www-form-urlencoded")) {
                return null;
            }
            InputStream is = httpReq.getInputStream();
            byte[] buf = new byte[4096];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            String raw = baos.toString("UTF-8");
            String[] pairs = raw.split("&");
            for (String pair : pairs) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String key = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                    String val = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                    if ("wire_body".equals(key)) {
                        return val;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 直接从 JSON 字符串解析。
     */
    @SuppressWarnings("unchecked")
    public static WireRequest fromJson(String json) {
        try {
            Map<String, Object> data = Json.parseToMap(json);
            String snapshot = (String) data.get("snapshot");
            String action = (String) data.get("action");

            // 兼容 params 可能是 ArrayList 的情况
            Object paramsObj = data.get("params");
            Map<String, Object> params;
            if (paramsObj instanceof Map) {
                params = (Map<String, Object>) paramsObj;
            } else if (paramsObj instanceof java.util.List) {
                params = new java.util.HashMap<>();
            } else {
                params = new java.util.HashMap<>();
            }

            // 兼容 sections 可能是 ArrayList 的情况
            Object sectionsObj = data.get("sections");
            List<String> sections;
            if (sectionsObj instanceof List) {
                sections = (List<String>) sectionsObj;
            } else {
                sections = new ArrayList<>();
            }

            return new WireRequest(snapshot, action, params, sections);
        } catch (Exception e) {
            throw new RuntimeException("解析 Wire 请求 JSON 失败", e);
        }
    }

    public String getSnapshot() {
        return snapshot;
    }

    public String getAction() {
        return action;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public List<String> getSections() {
        return sections;
    }

    // 已删除（0.2.0 破坏性窗口，审计 O5 / N5）：
    //   - getData()：直接调用 WireManager.decodeSnapshot，**不校验快照签名** ——
    //     任何能构造 base64 快照的调用方都能绕过完整性校验；
    //   - getMergedData()：在 getData() 之上合并 params，同样绕过签名。
    // 两者在仓库内均无调用方（框架主流程走 WireController 的「先验签、后解析」路径），
    // 因此按「不留绕过签名的公开入口」直接删除，而不是保留 @Deprecated 警告。
}
