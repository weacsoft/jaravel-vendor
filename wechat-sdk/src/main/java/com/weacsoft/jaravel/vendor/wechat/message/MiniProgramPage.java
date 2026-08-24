package com.weacsoft.jaravel.vendor.wechat.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序消息（{@code msgtype=miniprogrampage}）：小程序跳转卡片。
 * <p>
 * 仅支持客服消息发送，不支持被动回复。
 * <p>
 * 注意：小程序 appid 须与公众号存在绑定关系。
 *
 * @author weacsoft
 */
public final class MiniProgramPage extends Message {

    private final String title;
    private final String appId;
    private final String pagePath;
    private final String thumbMediaId;

    /**
     * @param title        卡片标题（必填）
     * @param appId        小程序 AppID（必填，与公众号绑定）
     * @param pagePath     小程序页面路径，与 app.json 对齐，可带参数（必填）
     * @param thumbMediaId 卡片封面媒体 id，image 类型素材，建议 520*416（必填）
     * @throws IllegalArgumentException 必填项为空时
     */
    public MiniProgramPage(String title, String appId, String pagePath, String thumbMediaId) {
        requireNonEmpty(title, "title");
        requireNonEmpty(appId, "appId");
        requireNonEmpty(pagePath, "pagePath");
        requireNonEmpty(thumbMediaId, "thumbMediaId");
        this.title = title;
        this.appId = appId;
        this.pagePath = pagePath;
        this.thumbMediaId = thumbMediaId;
    }

    @Override
    public String getType() {
        return "miniprogrampage";
    }

    public String getTitle() {
        return title;
    }

    public String getAppId() {
        return appId;
    }

    public String getPagePath() {
        return pagePath;
    }

    public String getThumbMediaId() {
        return thumbMediaId;
    }

    @Override
    protected Map<String, Object> payload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("title", title);
        p.put("appid", appId);
        p.put("pagepath", pagePath);
        p.put("thumb_media_id", thumbMediaId);
        return p;
    }

    @Override
    public String toString() {
        return "MiniProgramPage{title=" + title + ", appId=" + appId + "}";
    }
}
