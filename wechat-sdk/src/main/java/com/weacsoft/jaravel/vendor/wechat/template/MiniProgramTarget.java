package com.weacsoft.jaravel.vendor.wechat.template;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模板消息的小程序目标（点击跳转到小程序）。
 * <p>
 * 约束：{@code appId} 须与公众号绑定；模板消息与订阅通知共用本类型（wire 键均为 {@code miniprogram}）。
 *
 * @author weacsoft
 */
public final class MiniProgramTarget {

    private final String appId;
    private final String pagePath;

    /**
     * @param appId    小程序 AppID（必填，与公众号绑定）
     * @param pagePath 页面路径（可带参数；可空）
     * @throws IllegalArgumentException appId 为空时
     */
    public MiniProgramTarget(String appId, String pagePath) {
        if (appId == null || appId.isEmpty()) {
            throw new IllegalArgumentException("小程序 appId 不能为空");
        }
        this.appId = appId;
        this.pagePath = pagePath;
    }

    public String getAppId() {
        return appId;
    }

    public String getPagePath() {
        return pagePath;
    }

    /**
     * wire 形态。
     *
     * @return {@code {appid, pagepath?}}
     */
    public Map<String, Object> toWire() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appid", appId);
        if (pagePath != null && !pagePath.isEmpty()) {
            m.put("pagepath", pagePath);
        }
        return m;
    }
}
