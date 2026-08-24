package com.weacsoft.jaravel.vendor.wechat.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义菜单按钮（对齐官方 {@code menu/create} 的 button 结构）。
 * <p>
 * 支持的 {@code type}：
 * <ul>
 *   <li>{@code click}（点击拉取事件，需 {@code key}）</li>
 *   <li>{@code view}（跳转链接，需 {@code url}）</li>
 *   <li>{@code miniprogram}（跳转小程序，需 {@code appId}/{@code pagePath}，建议附 {@code url} 兜底）</li>
 *   <li>{@code scancode_push / scancode_waitmsg}（扫码推事件，需 {@code key}）</li>
 *   <li>{@code pic_sysphoto / pic_photo_or_album / pic_weixin}（发图，需 {@code key}）</li>
 *   <li>{@code location_select}（发送位置，需 {@code key}）</li>
 *   <li>{@code media_id / view_limited}（图文素材，需 {@code mediaId}）</li>
 *   <li>{@code article_id / article_view_limited}（发布图文，需 {@code articleId}）</li>
 * </ul>
 *
 * 层级约束（官方）：顶层 ≤3 个按钮，子级 ≤5 个按钮，最深两级。
 *
 * <p>
 * fluent 链式构造：
 * <pre>
 * MenuItem home = new MenuItem().name("首页").view("https://example.com");
 * MenuItem scan = new MenuItem().name("扫码").click("SCAN_ME");
 * MenuItem mini = new MenuItem().name("小程序").miniprogram("wx123", "pages/index/index", null);
 * </pre>
 *
 * @author weacsoft
 */
public final class MenuItem {

    private static final int MAX_SUB_BUTTONS = 5;

    private String name;
    private String type;
    private String key;
    private String url;
    private String appId;
    private String pagePath;
    private String mediaId;
    private String articleId;
    private List<MenuItem> subButtons;

    /**
     * fluent：按钮名称（必填，≤16 字节）。
     */
    public MenuItem name(String name) {
        this.name = name;
        return this;
    }

    /**
     * fluent：click 类型（携带事件 key）。
     */
    public MenuItem click(String key) {
        this.type = "click";
        this.key = key;
        return this;
    }

    /**
     * fluent：view 类型（跳转 URL）。
     */
    public MenuItem view(String url) {
        this.type = "view";
        this.url = url;
        return this;
    }

    /**
     * fluent：miniprogram 类型（跳转小程序）。
     *
     * @param appId    小程序 AppID（与公众号绑定）
     * @param pagePath 页面路径（可带参数）
     * @param url      降级 URL（不支持小程序的客户端使用），可空
     */
    public MenuItem miniprogram(String appId, String pagePath, String url) {
        this.type = "miniprogram";
        this.appId = appId;
        this.pagePath = pagePath;
        this.url = url;
        return this;
    }

    /**
     * fluent：扫码推事件（{@code scancode_push})。
     */
    public MenuItem scancodePush(String key) {
        this.type = "scancode_push";
        this.key = key;
        return this;
    }

    /**
     * fluent：扫码带提示（{@code scancode_waitmsg}）。
     */
    public MenuItem scancodeWaitmsg(String key) {
        this.type = "scancode_waitmsg";
        this.key = key;
        return this;
    }

    /**
     * fluent：发送位置（{@code location_select}）。
     */
    public MenuItem locationSelect(String key) {
        this.type = "location_select";
        this.key = key;
        return this;
    }

    /**
     * fluent：系统拍照（{@code pic_sysphoto}）。
     */
    public MenuItem picSysphoto(String key) {
        this.type = "pic_sysphoto";
        this.key = key;
        return this;
    }

    /**
     * fluent：拍照或相册（{@code pic_photo_or_album}）。
     */
    public MenuItem picPhotoOrAlbum(String key) {
        this.type = "pic_photo_or_album";
        this.key = key;
        return this;
    }

    /**
     * fluent：微信相册（{@code pic_weixin}）。
     */
    public MenuItem picWeixin(String key) {
        this.type = "pic_weixin";
        this.key = key;
        return this;
    }

    /**
     * fluent：发布图文素材按钮（{@code media_id} / {@code view_limited} 共用 mediaId 字段）。
     *
     * @param type    "media_id" 或 "view_limited"
     * @param mediaId 图文素材 id
     */
    public MenuItem mediaContent(String type, String mediaId) {
        if (!"media_id".equals(type) && !"view_limited".equals(type)) {
            throw new IllegalArgumentException("mediaContent 仅支持 media_id / view_limited 类型");
        }
        this.type = type;
        this.mediaId = mediaId;
        return this;
    }

    /**
     * fluent：发布文章按钮（{@code article_id} / {@code article_view_limited} 共用 articleId 字段）。
     *
     * @param type       "article_id" 或 "article_view_limited"
     * @param articleId  发布文章 id
     */
    public MenuItem article(String type, String articleId) {
        if (!"article_id".equals(type) && !"article_view_limited".equals(type)) {
            throw new IllegalArgumentException("article 仅支持 article_id / article_view_limited 类型");
        }
        this.type = type;
        this.articleId = articleId;
        return this;
    }

    /**
     * fluent：子菜单（≤5 个，仅顶层按钮可用）。
     *
     * @throws IllegalArgumentException 子级超过 5 个时
     */
    public MenuItem sub(List<MenuItem> subButtons) {
        if (subButtons.size() > MAX_SUB_BUTTONS) {
            throw new IllegalArgumentException("子菜单按钮不能超过 " + MAX_SUB_BUTTONS + " 个（当前 " + subButtons.size() + "）");
        }
        this.subButtons = List.copyOf(subButtons);
        return this;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public String getUrl() {
        return url;
    }

    public String getAppId() {
        return appId;
    }

    public String getPagePath() {
        return pagePath;
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getArticleId() {
        return articleId;
    }

    public List<MenuItem> getSubButtons() {
        return subButtons;
    }

    /**
     * 序列化为官方 button JSON（null 字段省略；子菜单为 {@code sub_button}）。
     *
     * @throws IllegalStateException name 缺失时
     */
    public Map<String, Object> toJson() {
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException("菜单按钮 name 不能为空");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        putIfPresent(m, "type", type);
        putIfPresent(m, "key", key);
        putIfPresent(m, "url", url);
        putIfPresent(m, "appid", appId);
        putIfPresent(m, "pagepath", pagePath);
        putIfPresent(m, "media_id", mediaId);
        putIfPresent(m, "article_id", articleId);
        if (subButtons != null && !subButtons.isEmpty()) {
            List<Map<String, Object>> subs = new ArrayList<>(subButtons.size());
            for (MenuItem item : subButtons) {
                subs.add(item.toJson());
            }
            m.put("sub_button", subs);
        }
        return m;
    }

    /**
     * 从官方 {@code menu/get} 响应还原按钮（含容错：null/缺失字段容忍）。
     *
     * @param node 按钮节点
     * @return 按钮
     */
    @SuppressWarnings("unchecked")
    public static MenuItem fromJsonMap(Map<String, Object> node) {
        MenuItem item = new MenuItem();
        item.name = getString(node, "name");
        item.type = getString(node, "type");
        item.key = getString(node, "key");
        item.url = getString(node, "url");
        item.appId = getString(node, "appid");
        item.pagePath = getString(node, "pagepath");
        item.mediaId = getString(node, "media_id");
        item.articleId = getString(node, "article_id");
        Object subs = node.get("sub_button");
        if (subs instanceof List<?> list && !list.isEmpty()) {
            List<MenuItem> children = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof Map<?, ?> om) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) om;
                    children.add(fromJsonMap(m));
                }
            }
            if (!children.isEmpty()) {
                item.subButtons = children;
            }
        }
        return item;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    @Override
    public String toString() {
        return "MenuItem{name=" + name + ", type=" + type
                + (subButtons != null ? ", sub=" + subButtons.size() : "") + "}";
    }
}
