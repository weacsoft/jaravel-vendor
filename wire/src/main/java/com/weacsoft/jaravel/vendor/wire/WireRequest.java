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
            Map<String, Object> params = (Map<String, Object>) data.get("params");
            List<String> sections = (List<String>) data.get("sections");

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
            Map<String, Object> params = (Map<String, Object>) data.get("params");
            List<String> sections = (List<String>) data.get("sections");
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

    /**
     * 从 snapshot 解码出原始数据 Map。
     *
     * @return 组件状态数据
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getData() {
        return WireManager.decodeSnapshot(snapshot);
    }

    /**
     * 将 params 合并到数据 Map 中（用于 wire:model 的属性更新）。
     *
     * @return 合并了 params 的数据 Map
     */
    public Map<String, Object> getMergedData() {
        Map<String, Object> data = new LinkedHashMap<>(getData());
        if (params != null) {
            data.putAll(params);
        }
        return data;
    }
}
