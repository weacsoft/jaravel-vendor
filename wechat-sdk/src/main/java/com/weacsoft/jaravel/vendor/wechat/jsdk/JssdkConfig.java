package com.weacsoft.jaravel.vendor.wechat.jsdk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSSDK 配置对象（公众号网页 JS-SDK，替代原 Map 版 {@code buildJsSdkConfig}）。
 * <p>
 * 字段对齐官方 {@code wx.config} 参数：appId、timestamp、nonceStr、signature、
 * jsApiList、openTagList（可选）、debug（可选）。
 *
 * @author weacsoft
 */
public final class JssdkConfig {

    private final String appId;
    private final String timestamp;
    private final String nonceStr;
    private final String signature;
    private final List<String> jsApiList;
    private final List<String> openTagList;
    private final boolean debug;

    /**
     * @param appId      公众号 appid
     * @param timestamp  时间戳（秒级，字符串形态）
     * @param nonceStr   随机串
     * @param signature  签名（sha1(jsapi_ticket=..., noncestr, timestamp, url)）
     * @param jsApiList  需要使用的 JS 接口列表（必填，非空）
     * @param openTagList 开放标签列表（可空）
     * @param debug      是否开启调试
     * @throws IllegalArgumentException jsApiList 为空时
     */
    public JssdkConfig(String appId, String timestamp, String nonceStr, String signature,
                       List<String> jsApiList, List<String> openTagList, boolean debug) {
        if (appId == null || appId.isEmpty()) {
            throw new IllegalArgumentException("JSSDK 配置缺少 appId");
        }
        if (signature == null || signature.isEmpty()) {
            throw new IllegalArgumentException("JSSDK 配置缺少 signature");
        }
        if (jsApiList == null || jsApiList.isEmpty()) {
            throw new IllegalArgumentException("JSSDK 配置 jsApiList 不能为空");
        }
        this.appId = appId;
        this.timestamp = timestamp;
        this.nonceStr = nonceStr;
        this.signature = signature;
        this.jsApiList = List.copyOf(jsApiList);
        this.openTagList = openTagList != null ? List.copyOf(openTagList) : List.of();
        this.debug = debug;
    }

    public String getAppId() {
        return appId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getNonceStr() {
        return nonceStr;
    }

    public String getSignature() {
        return signature;
    }

    /**
     * @return JS 接口列表（只读）
     */
    public List<String> getJsApiList() {
        return jsApiList;
    }

    /**
     * @return 开放标签列表（只读；未配置时为空列表）
     */
    public List<String> getOpenTagList() {
        return openTagList;
    }

    public boolean isDebug() {
        return debug;
    }

    /**
     * 渲染为前端可直接消费的 {@code wx.config({...})} 参数对象。
     *
     * @return 配置对象（openTagList 为空时省略）
     */
    public Map<String, Object> toJsonBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", appId);
        body.put("timestamp", timestamp);
        body.put("nonceStr", nonceStr);
        body.put("signature", signature);
        body.put("jsApiList", jsApiList);
        if (!openTagList.isEmpty()) {
            body.put("openTagList", openTagList);
        }
        body.put("debug", debug);
        return body;
    }

    /**
     * 渲染为可直接粘贴到前端的 {@code wx.config(...)} JS 片段。
     *
     * @return JS 代码片段
     */
    public String toJavascript() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("wx.config(").append(toJsonBody().toString()).append(");\n");
        sb.append("wx.error(function (e) { console.error('微信 JSSDK 配置失败', e); });");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "JssdkConfig{appId=" + appId + ", jsApiList=" + jsApiList + ", debug=" + debug + "}";
    }
}
