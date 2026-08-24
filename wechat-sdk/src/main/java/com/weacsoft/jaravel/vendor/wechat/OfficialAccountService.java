package com.weacsoft.jaravel.vendor.wechat;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.wechat.jsdk.JssdkConfig;
import com.weacsoft.jaravel.vendor.wechat.message.Image;
import com.weacsoft.jaravel.vendor.wechat.message.MenuMessage;
import com.weacsoft.jaravel.vendor.wechat.message.Message;
import com.weacsoft.jaravel.vendor.wechat.message.MiniProgramPage;
import com.weacsoft.jaravel.vendor.wechat.message.MpNews;
import com.weacsoft.jaravel.vendor.wechat.message.MpNewsArticle;
import com.weacsoft.jaravel.vendor.wechat.message.Music;
import com.weacsoft.jaravel.vendor.wechat.message.News;
import com.weacsoft.jaravel.vendor.wechat.message.Text;
import com.weacsoft.jaravel.vendor.wechat.message.Video;
import com.weacsoft.jaravel.vendor.wechat.message.Voice;
import com.weacsoft.jaravel.vendor.wechat.message.WeChatCard;
import com.weacsoft.jaravel.vendor.wechat.menu.Menu;
import com.weacsoft.jaravel.vendor.wechat.response.WeChatResponse;
import com.weacsoft.jaravel.vendor.wechat.response.WechatApiException;
import com.weacsoft.jaravel.vendor.wechat.template.SubscriptionNotice;
import com.weacsoft.jaravel.vendor.wechat.template.TemplateMessage;
import com.weacsoft.jaravel.vendor.wechat.transport.JacksonJsonEncoder;
import com.weacsoft.jaravel.vendor.wechat.transport.RequestJsonEncoder;
import com.weacsoft.jaravel.vendor.wechat.transport.WechatTransport;
import com.weacsoft.jaravel.vendor.wechat.user.MaterialItem;
import com.weacsoft.jaravel.vendor.wechat.user.Tag;
import com.weacsoft.jaravel.vendor.wechat.user.WeChatUser;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 微信公众号服务：类型化的 API 门面。
 * <p>
 * 对齐 EasyWeChat（overtrue/wechat 5.x）的对象模型：发送侧用
 * {@link com.weacsoft.jaravel.vendor.wechat.message.Message} 消息族 + {@link template.TemplateMessage}/
 * {@link template.SubscriptionNotice}，读取侧返回类型化 DTO（{@link WeChatUser}/{@link Tag}/
 * {@link MaterialItem}），发送/管理动作返回 {@link WeChatResponse}（errcode/errmsg/msgid 可检查、可 {@code as()}）。
 *
 * <h3>功能面（官方 API 全覆盖）</h3>
 * <ul>
 *   <li><b>用户</b>：getUser / updateRemark / listUserOpenids / batchGetUsers</li>
 *   <li><b>标签</b>：createTag / getTags / deleteTag / batchTagging / batchUnTagging</li>
 *   <li><b>模板消息</b>：sendTemplate（服务号）/ sendSubscriptionNotice（订阅通知，官方主推）</li>
 *   <li><b>菜单</b>：setMenu / getCustomMenu / deleteMenu（类型化 {@link Menu}）</li>
 *   <li><b>素材</b>：uploadTemp / uploadPermanent / uploadImageTemp / uploadImageFull /
 *       getMaterial / deleteMaterial / listMaterial / ocrCheck</li>
 *   <li><b>客服消息</b>：sendCustomerMessage（11 种消息类型 + customservice + aimsgcontext）/
 *       setTyping / sendText / sendImage / sendVoice / sendVideo / sendMusic / sendNews /
 *       sendMpNews / sendMpNewsArticle / sendCard / sendMiniProgramPage / sendMsgMenu</li>
 *   <li><b>二维码</b>：createTemporaryQrCode / createPermanentQrCode</li>
 *   <li><b>JSSDK</b>：buildJsSdkConfig → {@link JssdkConfig}（jsapi_ticket 走 cache 模块缓存）</li>
 *   <li><b>接收消息</b>：server() → {@link WeChatServer}（验签/加解密/被动回复，plain/safe 双模式）</li>
 * </ul>
 *
 * <h3>多配置支持</h3>
 * 支持多公众号命名配置（声明 {@code @RegisterWechatOfficialAccount} &gt; yml &gt; 兜底默认）；
 * 每个方法都有「默认 default」与「指定 configName」两种重载。
 *
 * <h3>线程安全</h3>
 * 本类无状态（票据缓存走 cache 模块的 {@link CacheStore}，OkHttp 线程安全），可跨线程共享。
 *
 * @author weacsoft
 */
public class OfficialAccountService {

    private static final Logger logger = LoggerFactory.getLogger(OfficialAccountService.class);

    /** JSSDK ticket 缓存键前缀，完整 key 格式：wechat:jsapi_ticket:{appId} */
    private static final String TICKET_CACHE_PREFIX = "wechat:jsapi_ticket:";

    /** JSSDK ticket 提前过期缓冲时间（秒） */
    private static final long TICKET_BUFFER_SECONDS = 300;

    /** user/info/batchget 单次上限（官方：100） */
    private static final int BATCH_USER_INFO_LIMIT = 100;

    /** tags/members/batchtagging 单次上限（官方：50） */
    private static final int BATCH_TAGGING_LIMIT = 50;

    /** getall 单页上限（官方：10000） */
    private static final int GETALL_PAGE_SIZE = 10000;

    /** getall 最大翻页次数（安全上限，防无限循环） */
    private static final int GETALL_MAX_PAGES = 2000;

    /** Access Token 管理器 */
    private final AccessTokenManager accessTokenManager;

    /** 微信配置属性 */
    private final WechatProperties properties;

    /** HTTP 传输层 */
    private final WechatTransport transport;

    /** 缓存仓库（用于 jsapi_ticket 缓存） */
    private final CacheStore cacheStore;

    /**
     * 便捷构造（默认 JSON 编码器 + 无 cache 管理器）。
     *
     * @param accessTokenManager Access Token 管理器
     * @param properties         微信配置属性
     * @param httpClient         OkHttp 客户端
     */
    public OfficialAccountService(AccessTokenManager accessTokenManager,
                                  WechatProperties properties,
                                  OkHttpClient httpClient) {
        this(accessTokenManager, properties, httpClient, new JacksonJsonEncoder(), null);
    }

    /**
     * 构造公众号服务。
     *
     * @param accessTokenManager Access Token 管理器
     * @param properties         微信配置属性
     * @param httpClient         OkHttp 客户端
     * @param encoder            请求体 JSON 编码器
     * @param cacheManager       缓存管理器（用于 jsapi_ticket 缓存，可为 null）
     */
    public OfficialAccountService(AccessTokenManager accessTokenManager,
                                  WechatProperties properties,
                                  OkHttpClient httpClient,
                                  RequestJsonEncoder encoder,
                                  CacheManager cacheManager) {
        this.accessTokenManager = accessTokenManager;
        this.properties = properties;
        this.transport = new WechatTransport(httpClient, encoder);
        this.cacheStore = WechatCacheResolver.resolve(cacheManager,
                properties != null ? properties.getCacheStore() : "");
    }

    // ==================== 用户管理 ====================

    /**
     * 获取用户基本信息。
     * <p>
     * API: {@code GET /cgi-bin/user/info}
     *
     * @param openid 用户 openid
     * @return 用户信息（类型化）
     */
    public WeChatUser getUser(String openid) {
        return getUser(openid, null);
    }

    /**
     * 获取用户基本信息（指定配置）。
     *
     * @param openid     用户 openid
     * @param configName 公众号配置名（null 用 default）
     * @return 用户信息
     */
    public WeChatUser getUser(String openid, String configName) {
        WeChatResponse resp = wechatGet("cgi-bin/user/info",
                Map.of("access_token", accessToken(configName), "openid", openid, "lang", "zh_CN"),
                "getUser");
        resp.requireSuccess("getUser");
        return WeChatUser.fromResponse(resp);
    }

    /**
     * 设置用户备注名。
     * <p>
     * API: {@code POST /cgi-bin/user/info/updateremark}
     *
     * @param openid 用户 openid
     * @param remark 备注名
     * @return API 响应
     */
    public WeChatResponse updateRemark(String openid, String remark) {
        return updateRemark(openid, remark, null);
    }

    /**
     * 设置用户备注名（指定配置）。
     *
     * @param openid     用户 openid
     * @param remark     备注名
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse updateRemark(String openid, String remark, String configName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("openid", openid);
        body.put("remark", remark);
        return wechatPost("cgi-bin/user/info/updateremark", tokenQuery(configName), body, "updateRemark");
    }

    /**
     * 拉取全部关注用户 openid（自动翻页 user/getall）。
     * <p>
     * API: {@code POST /cgi-bin/user/getall}
     *
     * @return 全部关注用户 openid 列表
     */
    public List<String> listUserOpenids() {
        return listUserOpenids(null);
    }

    /**
     * 拉取全部关注用户 openid（指定配置，自动翻页）。
     *
     * @param configName 公众号配置名
     * @return 全部关注用户 openid 列表
     */
    @SuppressWarnings("unchecked")
    public List<String> listUserOpenids(String configName) {
        List<String> all = new ArrayList<>();
        String nextOpenid = null;
        for (int page = 0; page < GETALL_MAX_PAGES; page++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("count", GETALL_PAGE_SIZE);
            if (nextOpenid != null) {
                body.put("next_openid", nextOpenid);
            }
            WeChatResponse resp = wechatPost("cgi-bin/user/getall", tokenQuery(configName), body, "listUserOpenids");
            resp.requireSuccess("listUserOpenids");
            Object dataRaw = resp.raw().get("data");
            Object listRaw = null;
            if (dataRaw instanceof Map<?, ?> dataMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) dataMap;
                listRaw = data.get("openid_list");
            }
            if (!(listRaw instanceof List<?> list) || list.isEmpty()) {
                break;
            }
            for (Object o : list) {
                if (o instanceof String s) {
                    all.add(s);
                }
            }
            Object noid = resp.raw().get("next_openid");
            nextOpenid = (noid instanceof String s && !s.isEmpty()) ? s : null;
        }
        logger.info("[wechat] listUserOpenids 完成: 共 {} 人", all.size());
        return List.copyOf(all);
    }

    /**
     * 批量获取用户信息（单页 ≤100，超出自动分批，user/info/batchget）。
     * <p>
     * 官方说明：已取关的用户不会返回，返回列表可能少于请求数。
     *
     * @param openids openid 列表（建议 ≤1000，内部按 100/批分页）
     * @return 用户信息列表
     */
    public List<WeChatUser> batchGetUsers(List<String> openids) {
        return batchGetUsers(openids, null);
    }

    /**
     * 批量获取用户信息（指定配置）。
     *
     * @param openids    openid 列表
     * @param configName 公众号配置名
     * @return 用户信息列表
     */
    @SuppressWarnings("unchecked")
    public List<WeChatUser> batchGetUsers(List<String> openids, String configName) {
        if (openids == null || openids.isEmpty()) {
            return List.of();
        }
        List<WeChatUser> users = new ArrayList<>();
        for (int i = 0; i < openids.size(); i += BATCH_USER_INFO_LIMIT) {
            List<String> chunk = openids.subList(i, Math.min(i + BATCH_USER_INFO_LIMIT, openids.size()));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("user_list", chunk);
            body.put("lang", "zh_CN");
            WeChatResponse resp = wechatPost("cgi-bin/user/info/batchget", tokenQuery(configName), body, "batchGetUsers");
            resp.requireSuccess("batchGetUsers");
            Object userlistRaw = resp.raw().get("userlist");
            if (userlistRaw instanceof List<?> userlist) {
                for (Object o : userlist) {
                    if (o instanceof Map<?, ?> om) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) om;
                        users.add(WeChatUser.from(m));
                    }
                }
            }
        }
        return List.copyOf(users);
    }

    // ==================== 标签管理 ====================

    /**
     * 创建标签。
     * <p>
     * API: {@code POST /cgi-bin/tags/create}
     *
     * @param name 标签名（≤30 字符）
     * @return API 响应（含 tag.id / tag.name）
     */
    public WeChatResponse createTag(String name) {
        return createTag(name, null);
    }

    /**
     * 创建标签（指定配置）。
     *
     * @param name       标签名
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse createTag(String name, String configName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tag", Map.of("name", name));
        return wechatPost("cgi-bin/tags/create", tokenQuery(configName), body, "createTag");
    }

    /**
     * 获取标签列表。
     * <p>
     * API: {@code GET /cgi-bin/tags/get}
     *
     * @return 标签列表
     */
    public List<Tag> getTags() {
        return getTags(null);
    }

    /**
     * 获取标签列表（指定配置）。
     *
     * @param configName 公众号配置名
     * @return 标签列表
     */
    @SuppressWarnings("unchecked")
    public List<Tag> getTags(String configName) {
        WeChatResponse resp = wechatGet("cgi-bin/tags/get", tokenQuery(configName), "getTags");
        resp.requireSuccess("getTags");
        Object raw = resp.raw().get("tagid_list");
        List<Tag> tags = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> om) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) om;
                    tags.add(Tag.from(m));
                }
            }
        }
        return List.copyOf(tags);
    }

    /**
     * 删除标签（已删除标签不会删除已打标的用户，用户变为无标签状态）。
     * <p>
     * API: {@code POST /cgi-bin/tags/delete}
     *
     * @param tagId 标签 id
     * @return API 响应
     */
    public WeChatResponse deleteTag(int tagId) {
        return deleteTag(tagId, null);
    }

    /**
     * 删除标签（指定配置）。
     *
     * @param tagId      标签 id
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse deleteTag(int tagId, String configName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tag", Map.of("id", tagId));
        return wechatPost("cgi-bin/tags/delete", tokenQuery(configName), body, "deleteTag");
    }

    /**
     * 批量为用户打标签（官方单次 ≤50 个 openid，超出自动分批）。
     * <p>
     * API: {@code POST /cgi-bin/tags/members/batchtagging}
     *
     * @param tagId   标签 id
     * @param openids openid 列表
     * @return 最后一批的 API 响应（各批均成功才返回）
     */
    public WeChatResponse batchTagging(int tagId, List<String> openids) {
        return batchTagging(tagId, openids, null);
    }

    /**
     * 批量为用户打标签（指定配置，超出 50 个自动分批）。
     *
     * @param tagId      标签 id
     * @param openids    openid 列表
     * @param configName 公众号配置名
     * @return 最后一批的 API 响应
     */
    public WeChatResponse batchTagging(int tagId, List<String> openids, String configName) {
        WeChatResponse last = null;
        for (int i = 0; i < openids.size(); i += BATCH_TAGGING_LIMIT) {
            List<String> chunk = openids.subList(i, Math.min(i + BATCH_TAGGING_LIMIT, openids.size()));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tagid", tagId);
            body.put("openid_list", chunk);
            last = wechatPost("cgi-bin/tags/members/batchtagging", tokenQuery(configName), body, "batchTagging");
            last.requireSuccess("batchTagging");
        }
        return last != null ? last : WeChatResponse.of(Map.of("tagid", tagId, "openid_count", openids.size()));
    }

    /**
     * 批量为用户取消标签（官方单次 ≤50，超出自动分批）。
     * <p>
     * API: {@code POST /cgi-bin/tags/members/batchuntagging}
     *
     * @param tagId   标签 id
     * @param openids openid 列表
     * @return 最后一批的 API 响应（各批均成功才返回）
     */
    public WeChatResponse batchUnTagging(int tagId, List<String> openids) {
        return batchUnTagging(tagId, openids, null);
    }

    /**
     * 批量为用户取消标签（指定配置，超出 50 个自动分批）。
     *
     * @param tagId      标签 id
     * @param openids    openid 列表
     * @param configName 公众号配置名
     * @return 最后一批的 API 响应
     */
    public WeChatResponse batchUnTagging(int tagId, List<String> openids, String configName) {
        WeChatResponse last = null;
        for (int i = 0; i < openids.size(); i += BATCH_TAGGING_LIMIT) {
            List<String> chunk = openids.subList(i, Math.min(i + BATCH_TAGGING_LIMIT, openids.size()));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tagid", tagId);
            body.put("openid_list", chunk);
            last = wechatPost("cgi-bin/tags/members/batchuntagging", tokenQuery(configName), body, "batchUnTagging");
            last.requireSuccess("batchUnTagging");
        }
        return last != null ? last : WeChatResponse.of(Map.of("tagid", tagId, "openid_count", openids.size()));
    }

    // ==================== 模板消息 / 订阅通知 ====================

    /**
     * 发送服务号模板消息。
     * <p>
     * API: {@code POST /cgi-bin/message/template/send}
     *
     * @param message 模板消息（含 touser/template_id/data 等）
     * @return API 响应（业务返回 msgid）
     */
    public WeChatResponse sendTemplate(TemplateMessage message) {
        return sendTemplate(message, null);
    }

    /**
     * 发送服务号模板消息（指定配置）。
     *
     * @param message    模板消息
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse sendTemplate(TemplateMessage message, String configName) {
        return wechatPost("cgi-bin/message/template/send", tokenQuery(configName),
                message.toJsonBody(), "sendTemplate");
    }

    /**
     * 发送订阅通知（官方主推消息类型）。
     * <p>
     * API: {@code POST /cgi-bin/message/template/subscribe}
     *
     * @param notice 订阅通知（含 touser/template_id/title/content）
     * @return API 响应（业务返回 msgid；失败会推送 subscribe_msg_sent_event 事件）
     */
    public WeChatResponse sendSubscriptionNotice(SubscriptionNotice notice) {
        return sendSubscriptionNotice(notice, null);
    }

    /**
     * 发送订阅通知（指定配置）。
     *
     * @param notice     订阅通知
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse sendSubscriptionNotice(SubscriptionNotice notice, String configName) {
        return wechatPost("cgi-bin/message/template/subscribe", tokenQuery(configName),
                notice.toJsonBody(), "sendSubscriptionNotice");
    }

    // ==================== 菜单管理 ====================

    /**
     * 创建自定义菜单（全量覆盖现有菜单）。
     * <p>
     * API: {@code POST /cgi-bin/menu/create}
     *
     * @param menu 菜单（≤3 个顶层按钮，两级以内）
     * @return API 响应
     */
    public WeChatResponse setMenu(Menu menu) {
        return setMenu(menu, null);
    }

    /**
     * 创建自定义菜单（指定配置）。
     *
     * @param menu       菜单
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse setMenu(Menu menu, String configName) {
        return wechatPost("cgi-bin/menu/create", tokenQuery(configName), menu.toJson(), "setMenu");
    }

    /**
     * 获取自定义菜单（类型化回读）。
     * <p>
     * API: {@code GET /cgi-bin/menu/get}
     *
     * @return 菜单（含子菜单）
     */
    public Menu getCustomMenu() {
        return getCustomMenu(null);
    }

    /**
     * 获取自定义菜单（指定配置）。
     *
     * @param configName 公众号配置名
     * @return 菜单
     */
    public Menu getCustomMenu(String configName) {
        WeChatResponse resp = wechatGet("cgi-bin/menu/get", tokenQuery(configName), "getCustomMenu");
        resp.requireSuccess("getCustomMenu");
        Object menuNode = resp.raw().get("menu");
        if (!(menuNode instanceof Map)) {
            throw new WechatApiException("getCustomMenu 响应缺少 menu 节点");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> menuMap = (Map<String, Object>) menuNode;
        return Menu.fromJsonMap(menuMap);
    }

    /**
     * 删除自定义菜单。
     * <p>
     * API: {@code POST /cgi-bin/menu/delete}
     *
     * @return API 响应
     */
    public WeChatResponse deleteMenu() {
        return deleteMenu(null);
    }

    /**
     * 删除自定义菜单（指定配置）。
     *
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse deleteMenu(String configName) {
        return wechatPost("cgi-bin/menu/delete", tokenQuery(configName), Map.of(), "deleteMenu");
    }

    // ==================== 素材管理 ====================

    /**
     * 上传临时素材（3 天有效，media_id 可用于客服消息）。
     * <p>
     * API: {@code POST /cgi-bin/media/upload?type={type}}
     *
     * @param path 本地文件路径
     * @param type 素材类型：image / voice / video / thumb
     * @return API 响应（media_id / created_at）
     */
    public WeChatResponse uploadTemp(String path, String type) {
        return uploadTemp(path, type, null);
    }

    /**
     * 上传临时素材（指定配置）。
     *
     * @param path       本地文件路径
     * @param type       素材类型
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse uploadTemp(String path, String type, String configName) {
        Map<String, String> query = new LinkedHashMap<>(tokenQuery(configName));
        query.put("type", type);
        return wechatUpload("cgi-bin/media/upload", query, path, "uploadTemp");
    }

    /**
     * 上传永久素材（不过期，有数量限额）。
     * <p>
     * API: {@code POST /cgi-bin/material/add_material?type={type}}
     *
     * @param path 本地文件路径
     * @param type 素材类型：image / voice / video（图文走图文素材接口，本通道不适用）
     * @return API 响应（media_id / url）
     */
    public WeChatResponse uploadPermanent(String path, String type) {
        return uploadPermanent(path, type, null);
    }

    /**
     * 上传永久素材（指定配置）。
     *
     * @param path       本地文件路径
     * @param type       素材类型
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse uploadPermanent(String path, String type, String configName) {
        Map<String, String> query = new LinkedHashMap<>(tokenQuery(configName));
        query.put("type", type);
        return wechatUpload("cgi-bin/material/add_material", query, path, "uploadPermanent");
    }

    /**
     * 上传临时图片素材（便捷方法）。
     *
     * @param path 本地图片路径
     * @return API 响应
     */
    public WeChatResponse uploadImageTemp(String path) {
        return uploadTemp(path, "image");
    }

    /**
     * 上传永久图片素材（便捷方法）。
     *
     * @param path 本地图片路径
     * @return API 响应
     */
    public WeChatResponse uploadImageFull(String path) {
        return uploadPermanent(path, "image");
    }

    /**
     * 获取单个永久素材（图文返回图文数组，其他返回下载 URL）。
     * <p>
     * API: {@code POST /cgi-bin/material/get_material}
     *
     * @param mediaId 素材 media_id
     * @return API 响应（item 数组 / url）
     */
    public WeChatResponse getMaterial(String mediaId) {
        return getMaterial(mediaId, null);
    }

    /**
     * 获取单个永久素材（指定配置）。
     *
     * @param mediaId    素材 media_id
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse getMaterial(String mediaId, String configName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("media_id", mediaId);
        return wechatPost("cgi-bin/material/get_material", tokenQuery(configName), body, "getMaterial");
    }

    /**
     * 删除永久素材。
     * <p>
     * API: {@code POST /cgi-bin/material/del_material}
     *
     * @param mediaId 素材 media_id
     * @return API 响应
     */
    public WeChatResponse deleteMaterial(String mediaId) {
        return deleteMaterial(mediaId, null);
    }

    /**
     * 删除永久素材（指定配置）。
     *
     * @param mediaId    素材 media_id
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse deleteMaterial(String mediaId, String configName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("media_id", mediaId);
        return wechatPost("cgi-bin/material/del_material", tokenQuery(configName), body, "deleteMaterial");
    }

    /**
     * 获取素材列表（官方分页：offset / count，count ≤20）。
     * <p>
     * API: {@code POST /cgi-bin/material/batchget_material}
     *
     * @param type   素材类型：image / voice / video / news
     * @param offset 偏移（0 起）
     * @param count  每页数量（1-20）
     * @return 素材条目列表
     */
    public List<MaterialItem> listMaterial(String type, int offset, int count) {
        return listMaterial(type, offset, count, null);
    }

    /**
     * 获取素材列表（指定配置）。
     *
     * @param type       素材类型
     * @param offset     偏移（0 起）
     * @param count      每页数量（1-20）
     * @param configName 公众号配置名
     * @return 素材条目列表
     */
    @SuppressWarnings("unchecked")
    public List<MaterialItem> listMaterial(String type, int offset, int count, String configName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("offset", offset);
        body.put("count", count);
        WeChatResponse resp = wechatPost("cgi-bin/material/batchget_material", tokenQuery(configName), body, "listMaterial");
        resp.requireSuccess("listMaterial");
        Object raw = resp.raw().get("item");
        List<MaterialItem> items = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> om) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) om;
                    items.add(MaterialItem.from(m));
                }
            }
        }
        return List.copyOf(items);
    }

    /**
     * 微信 OCR（图片识别文字 / 识别二维码）。
     * <p>
     * API: {@code POST /cgi-bin/media/ocr}；type=1 识别文字，type=2 识别二维码。
     * 每日限额 10000 次。
     *
     * @param mediaId 素材 media_id
     * @return API 响应（type=1 时为 data 文本数组）
     */
    public WeChatResponse ocrCheck(String mediaId) {
        return ocrCheck(mediaId, null);
    }

    /**
     * 微信 OCR（指定配置）。
     *
     * @param mediaId    素材 media_id
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse ocrCheck(String mediaId, String configName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("media_id", mediaId);
        body.put("type", 1);
        return wechatPost("cgi-bin/media/ocr", tokenQuery(configName), body, "ocrCheck");
    }

    // ==================== 客服消息 ====================

    /**
     * 发送客服消息（统一入口，11 种消息类型共享）。
     * <p>
     * API: {@code POST /cgi-bin/message/custom/send}
     * <p>
     * 官方规则：
     * <ul>
     *   <li>用户 48h 内发消息才能收到（被动窗口）；菜单点击/关注/扫码 5 分钟内可发</li>
     *   <li>用户主动消息后 5min 内可发 5 条；菜单场景 1min 内 3 条</li>
     *   <li>news 客服消息条数 ≤1（微信 45008 约束，已在 {@link News} 内强制）</li>
     * </ul>
     *
     * @param message 消息对象（已 {@code toUser(openid)}；可选 {@code withKfAccount}/{@code withAiMsg}）
     * @return API 响应（业务返回 msgid）
     */
    public WeChatResponse sendCustomerMessage(Message message) {
        return sendCustomerMessage(message, null);
    }

    /**
     * 发送客服消息（指定配置）。
     *
     * @param message    消息对象
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse sendCustomerMessage(Message message, String configName) {
        return wechatPost("cgi-bin/message/custom/send", tokenQuery(configName),
                message.toJsonBody(), "sendCustomerMessage");
    }

    /** 发送文本客服消息（便捷）。 */
    public WeChatResponse sendText(String openid, String content) {
        return sendCustomerMessage(new Text(content).toUser(openid));
    }

    /** 发送图片客服消息（便捷）。 */
    public WeChatResponse sendImage(String openid, String mediaId) {
        return sendCustomerMessage(new Image(mediaId).toUser(openid));
    }

    /** 发送语音客服消息（便捷）。 */
    public WeChatResponse sendVoice(String openid, String mediaId) {
        return sendCustomerMessage(new Voice(mediaId).toUser(openid));
    }

    /** 发送视频客服消息（便捷）。 */
    public WeChatResponse sendVideo(String openid, Video video) {
        return sendCustomerMessage(video.toUser(openid));
    }

    /** 发送音乐客服消息（便捷）。 */
    public WeChatResponse sendMusic(String openid, Music music) {
        return sendCustomerMessage(music.toUser(openid));
    }

    /** 发送图文客服消息（便捷，≤1 条图文）。 */
    public WeChatResponse sendNews(String openid, News news) {
        return sendCustomerMessage(news.toUser(openid));
    }

    /** 发送公众号图文（mpnews，media_id）客服消息（便捷）。 */
    public WeChatResponse sendMpNews(String openid, MpNews mpNews) {
        return sendCustomerMessage(mpNews.toUser(openid));
    }

    /** 发送发布图文（mpnewsarticle，article_id）客服消息（便捷，官方推荐）。 */
    public WeChatResponse sendMpNewsArticle(String openid, MpNewsArticle article) {
        return sendCustomerMessage(article.toUser(openid));
    }

    /** 发送卡券客服消息（便捷）。 */
    public WeChatResponse sendCard(String openid, WeChatCard card) {
        return sendCustomerMessage(card.toUser(openid));
    }

    /** 发送小程序卡片客服消息（便捷）。 */
    public WeChatResponse sendMiniProgramPage(String openid, MiniProgramPage page) {
        return sendCustomerMessage(page.toUser(openid));
    }

    /** 发送菜单客服消息（msgmenu，便捷）。 */
    public WeChatResponse sendMsgMenu(String openid, MenuMessage msgMenu) {
        return sendCustomerMessage(msgMenu.toUser(openid));
    }

    /**
     * 设置客服打字状态。
     * <p>
     * API: {@code POST /cgi-bin/message/custom/typing}
     *
     * @param openid 用户 openid
     * @param typing true=正在输入（typing），false=取消（cancel_typing）
     * @return API 响应
     */
    public WeChatResponse setTyping(String openid, boolean typing) {
        return setTyping(openid, typing, null);
    }

    /**
     * 设置客服打字状态（指定配置）。
     *
     * @param openid     用户 openid
     * @param typing     true=正在输入，false=取消
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse setTyping(String openid, boolean typing, String configName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openid);
        body.put("command", typing ? "typing" : "cancel_typing");
        return wechatPost("cgi-bin/message/custom/typing", tokenQuery(configName), body, "setTyping");
    }

    // ==================== 二维码 ====================

    /**
     * 创建临时二维码（带场景值，60 秒至 30 天过期，自动失效）。
     * <p>
     * API: {@code POST /cgi-bin/qrcode/create}（action_name=QR_LIMIT_SCENE）
     *
     * @param scene         场景字符串（≤128 字节，字母/数字/常用符号）
     * @param expireSeconds 过期秒数（60 ~ 30*24*3600）
     * @return API 响应（ticket / expire_in / url）
     */
    public WeChatResponse createTemporaryQrCode(String scene, int expireSeconds) {
        if (expireSeconds < 60 || expireSeconds > 30 * 24 * 3600) {
            throw new IllegalArgumentException("临时二维码有效期必须在 60 秒到 30 天之间"
                    + "（当前 " + expireSeconds + "）");
        }
        return createTemporaryQrCode(scene, expireSeconds, null);
    }

    /**
     * 创建临时二维码（指定配置）。
     *
     * @param scene         场景字符串
     * @param expireSeconds 过期秒数
     * @param configName    公众号配置名
     * @return API 响应
     */
    public WeChatResponse createTemporaryQrCode(String scene, int expireSeconds, String configName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action_name", "QR_LIMIT_SCENE");
        Map<String, Object> sceneInfo = new LinkedHashMap<>();
        sceneInfo.put("scene_id", 0);
        sceneInfo.put("scene_str", scene);
        body.put("action_info", Map.of("scene", sceneInfo));
        body.put("expire_in", expireSeconds);
        return wechatPost("cgi-bin/qrcode/create", tokenQuery(configName), body, "createTemporaryQrCode");
    }

    /**
     * 创建永久二维码（带场景值）。
     * <p>
     * API: {@code POST /cgi-bin/qrcode/create}（action_name=QR_LIMIT）
     *
     * @param scene 场景字符串
     * @return API 响应（ticket / url）
     */
    public WeChatResponse createPermanentQrCode(String scene) {
        return createPermanentQrCode(scene, null);
    }

    /**
     * 创建永久二维码（指定配置）。
     *
     * @param scene      场景字符串
     * @param configName 公众号配置名
     * @return API 响应
     */
    public WeChatResponse createPermanentQrCode(String scene, String configName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action_name", "QR_LIMIT");
        Map<String, Object> sceneInfo = new LinkedHashMap<>();
        sceneInfo.put("scene_id", 0);
        sceneInfo.put("scene_str", scene);
        body.put("action_info", Map.of("scene", sceneInfo));
        return wechatPost("cgi-bin/qrcode/create", tokenQuery(configName), body, "createPermanentQrCode");
    }

    // ==================== JSSDK ====================

    /**
     * 构建 JSSDK 配置（类型化对象；jsapi_ticket 走 cache 模块缓存）。
     * <p>
     * 签名算法（官方）：
     * <ol>
     *   <li>取 jsapi_ticket（缓存 key {@code wechat:jsapi_ticket:{appId}}，TTL=expires_in-300，最小 60）</li>
     *   <li>拼接 {@code jsapi_ticket=…&noncestr=…&timestamp=…&url=…}</li>
     *   <li>SHA1 得 signature</li>
     * </ol>
     *
     * @param url         当前页面 URL（去除 # 后）
     * @param jsApiList   要使用的 JS 接口
     * @param openTagList 开放标签（可空）
     * @param debug       是否调试模式
     * @return JSSDK 配置（含 appId/timestamp/nonceStr/signature/jsApiList/openTagList/debug，
     *     可 {@code toJsonBody()} 或 {@code toJavascript()} 直出前端代码）
     */
    public JssdkConfig buildJsSdkConfig(String url, List<String> jsApiList,
                                        List<String> openTagList, boolean debug) {
        return buildJsSdkConfig(url, jsApiList, openTagList, debug, null);
    }

    /**
     * 构建 JSSDK 配置（指定公众号配置）。
     *
     * @param url         当前页面 URL
     * @param jsApiList   JS 接口列表
     * @param openTagList 开放标签（可空）
     * @param debug       是否调试
     * @param configName  公众号配置名
     * @return JSSDK 配置
     */
    public JssdkConfig buildJsSdkConfig(String url, List<String> jsApiList,
                                        List<String> openTagList, boolean debug,
                                        String configName) {
        WechatProperties.OfficialAccountConfig config = resolveConfig(configName);
        String ticket = getJsApiTicket(configName);
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signStr = "jsapi_ticket=" + ticket + "&noncestr=" + nonceStr
                + "&timestamp=" + timestamp + "&url=" + url;
        String signature = sha1(signStr);
        logger.debug("[wechat] JSSDK 配置生成成功: appId={}, url={}", config.getAppId(), url);
        return new JssdkConfig(config.getAppId(), timestamp, nonceStr, signature,
                jsApiList, openTagList, debug);
    }

    // ==================== 接收消息 ====================

    /**
     * 获取「接收消息」服务端（验签/加解密/被动回复）。
     * <p>
     * 消息模式由 {@code jaravel.wechat.official-accounts.{name}.message-mode}（plain/safe）决定。
     *
     * @return 服务端实例
     */
    public WeChatServer server() {
        return server(null);
    }

    /**
     * 获取指定配置的「接收消息」服务端。
     *
     * @param configName 公众号配置名
     * @return 服务端实例
     */
    public WeChatServer server(String configName) {
        return new WeChatServer(properties, configName);
    }

    // ==================== 令牌 ====================

    /**
     * 获取指定配置的 access_token（走 cache 模块缓存；token 模式由
     * {@code jaravel.wechat.token-mode} 决定 legacy/stable）。
     *
     * @param configName 公众号配置名（null 用 default）
     * @return access_token
     */
    public String getAccessToken(String configName) {
        return accessToken(configName);
    }

    /**
     * @return 底层 Access Token 管理器（供高级用法/失效重置）
     */
    public AccessTokenManager tokenManager() {
        return accessTokenManager;
    }

    // ==================== 内部辅助 ====================

    private WechatProperties.OfficialAccountConfig resolveConfig(String configName) {
        WechatProperties.OfficialAccountConfig config = properties.getOfficialAccount(configName);
        if (config == null) {
            throw new IllegalStateException("未找到公众号配置: "
                    + (configName == null ? "default" : configName)
                    + "（可通过 @RegisterWechatOfficialAccount 声明或 yml 配置）");
        }
        return config;
    }

    private Map<String, String> tokenQuery(String configName) {
        WechatProperties.OfficialAccountConfig config = resolveConfig(configName);
        if (config.getAppId() == null || config.getSecret() == null) {
            throw new IllegalStateException("公众号配置 \"" + configName + "\" 缺少 appId 或 secret");
        }
        return Map.of("access_token", accessTokenManager.getToken(config.getAppId(), config.getSecret()));
    }

    private String accessToken(String configName) {
        WechatProperties.OfficialAccountConfig config = resolveConfig(configName);
        if (config.getAppId() == null || config.getSecret() == null) {
            throw new IllegalStateException("公众号配置 \"" + configName + "\" 缺少 appId 或 secret");
        }
        return accessTokenManager.getToken(config.getAppId(), config.getSecret());
    }

    private WeChatResponse wechatGet(String path, Map<String, String> query, String operation) {
        return transport.get(path, query, operation);
    }

    private WeChatResponse wechatPost(String path, Map<String, String> query, Object body, String operation) {
        return transport.postJson(path, query, body, operation);
    }

    private WeChatResponse wechatUpload(String path, Map<String, String> query, String filePath, String operation) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }
        return transport.upload(path, query, file, "media", operation);
    }

    /**
     * 获取 jsapi_ticket（带缓存）。
     * <p>
     * 缓存 key：{@code wechat:jsapi_ticket:{appId}}，TTL = expires_in - 300（最小 60）。
     * 缓存仓库：cache 模块默认 store（或 {@code jaravel.wechat.cache-store} 指定）。
     *
     * @param configName 公众号配置名
     * @return jsapi_ticket
     */
    @SuppressWarnings("unchecked")
    private String getJsApiTicket(String configName) {
        WechatProperties.OfficialAccountConfig config = resolveConfig(configName);
        String appId = config.getAppId();
        String cacheKey = TICKET_CACHE_PREFIX + appId;

        String cached = cacheStore.get(cacheKey, String.class);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        WeChatResponse resp = wechatGet("cgi-bin/ticket/getticket",
                Map.of("access_token", accessToken(configName), "type", "jsapi"), "getJsApiTicket");
        resp.requireSuccess("getJsApiTicket");
        Object ticketRaw = resp.raw().get("ticket");
        if (ticketRaw == null || String.valueOf(ticketRaw).isEmpty()) {
            throw new WechatApiException("getJsApiTicket 响应缺少 ticket 字段");
        }
        String ticket = String.valueOf(ticketRaw);
        int expiresIn = resp.raw().get("expires_in") instanceof Number n ? n.intValue() : 7200;
        long ttlSeconds = Math.max(expiresIn - TICKET_BUFFER_SECONDS, 60);
        cacheStore.put(cacheKey, ticket, ttlSeconds);
        logger.info("[wechat] 获取 jsapi_ticket 成功: appId={}, expires_in={}s", appId, expiresIn);
        return ticket;
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 算法不可用", e);
        }
    }
}
