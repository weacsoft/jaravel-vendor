package com.weacsoft.jaravel.vendor.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weacsoft.jaravel.vendor.cache.driver.ArrayCacheDriver;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.store.DefaultCacheStore;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 微信公众号服务，对齐 PHP 项目的 {@code WechatService}。
 * <p>
 * 封装微信公众号全部常用 API，包括用户信息、模板消息、菜单管理、标签管理、
 * 素材管理、客服消息、JSSDK 配置等。所有 API 调用通过 OkHttp 执行，
 * JSON 响应通过 Jackson {@link ObjectMapper} 解析。
 *
 * <h3>PHP 对齐关系</h3>
 * <table border="1">
 * <tr><th>PHP WechatService 方法</th><th>Java OfficialAccountService 方法</th></tr>
 * <tr><td>getApplication()</td><td>getAccessToken(configName)</td></tr>
 * <tr><td>getUserData(openid)</td><td>getUserData(openid)</td></tr>
 * <tr><td>sendTemplate(...)</td><td>sendTemplate(...)</td></tr>
 * <tr><td>getMenu() / setMenu(json)</td><td>getMenu() / setMenu(menuJson)</td></tr>
 * <tr><td>createTag / getTag / deleteTag</td><td>createTag / getTags / deleteTag</td></tr>
 * <tr><td>batchTagging / batchUnTagging</td><td>batchTagging / batchUnTagging</td></tr>
 * <tr><td>uploadImageTemp / uploadImageFull</td><td>uploadImageTemp / uploadImageFull</td></tr>
 * <tr><td>downloadImageFull / deleteImageFull</td><td>downloadImageFull / deleteImageFull</td></tr>
 * <tr><td>getMaterialList</td><td>getMaterialList</td></tr>
 * <tr><td>sendMessage(data)</td><td>sendMessage(data)</td></tr>
 * <tr><td>sendTyping(openid, command)</td><td>sendTyping(openid, command)</td></tr>
 * <tr><td>controllerbuildJsSdkConfig(...)</td><td>buildJsSdkConfig(...)</td></tr>
 * </table>
 *
 * <h3>多配置支持</h3>
 * 支持多公众号配置（如 default、snsapi_userinfo），每个方法可通过 configName 参数
 * 指定使用哪个配置，默认使用 default。
 *
 * <h3>线程安全</h3>
 * 本类为无状态单例（配置、客户端、解析器均为构造后不可变字段），可被多线程并发安全调用。
 * JSSDK ticket 通过 cache 模块的 {@link CacheStore} 缓存（优先 redis，回退 array），线程安全。
 *
 * @author weacsoft
 */
public class OfficialAccountService {

    private static final Logger logger = LoggerFactory.getLogger(OfficialAccountService.class);

    /** 微信 API 基础地址 */
    private static final String API_BASE_URL = "https://api.weixin.qq.com";

    /** JSON 媒体类型 */
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** JSSDK ticket 缓存键前缀，完整 key 格式：wechat:jsapi_ticket:{appId} */
    private static final String TICKET_CACHE_PREFIX = "wechat:jsapi_ticket:";

    /** JSSDK ticket 提前过期缓冲时间（秒） */
    private static final long TICKET_BUFFER_SECONDS = 300;

    /** Access Token 管理器 */
    private final AccessTokenManager accessTokenManager;

    /** 微信配置属性 */
    private final WechatProperties properties;

    /** OkHttp 客户端 */
    private final OkHttpClient httpClient;

    /** Jackson JSON 解析器 */
    private final ObjectMapper objectMapper;

    /** 缓存仓库（用于 JSSDK ticket 缓存，优先 redis，未注册时回退 array） */
    private final CacheStore cacheStore;

    /**
     * 构造公众号服务。
     * <p>
     * 通过 {@link CacheManager} 解析缓存仓库用于 JSSDK ticket 缓存：优先使用 {@code redis} store，
     * 当 redis store 未注册时回退到 {@code array} 内存 store。
     *
     * @param accessTokenManager Access Token 管理器
     * @param properties         微信配置属性
     * @param httpClient         OkHttp 客户端
     * @param objectMapper       Jackson JSON 解析器
     * @param cacheManager       缓存管理器（由 cache 模块提供，用于 JSSDK ticket 缓存）
     */
    public OfficialAccountService(AccessTokenManager accessTokenManager,
                                  WechatProperties properties,
                                  OkHttpClient httpClient,
                                  ObjectMapper objectMapper,
                                  CacheManager cacheManager) {
        this.accessTokenManager = accessTokenManager;
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.cacheStore = resolveStore(cacheManager, properties != null ? properties.getCacheStore() : "redis");
    }

    // ==================== 用户管理 ====================

    /**
     * 获取用户基本信息，对齐 PHP {@code WechatService::getUserData(openid)}。
     * <p>
     * API: {@code GET /cgi-bin/user/info?openid={openid}&lang=zh_CN}
     *
     * @param openid 用户 openid
     * @return 用户信息（subscribe, openid, nickname, sex, city, ... 等）
     */
    public Map<String, Object> getUserData(String openid) {
        return getUserData(openid, "default");
    }

    /**
     * 获取用户基本信息（指定配置）。
     *
     * @param openid     用户 openid
     * @param configName 公众号配置名
     * @return 用户信息
     */
    public Map<String, Object> getUserData(String openid, String configName) {
        String token = getAccessToken(configName);
        String url = API_BASE_URL + "/cgi-bin/user/info?access_token=" + token
                + "&openid=" + openid + "&lang=zh_CN";
        return executeGet(url, "getUserData");
    }

    /**
     * 设置用户备注名，对齐微信 API。
     * <p>
     * API: {@code POST /cgi-bin/user/info/updateremark}
     *
     * @param openid 用户 openid
     * @param remark 备注名
     * @return API 响应
     */
    public Map<String, Object> updateUserRemark(String openid, String remark) {
        return updateUserRemark(openid, remark, "default");
    }

    /**
     * 设置用户备注名（指定配置）。
     *
     * @param openid     用户 openid
     * @param remark     备注名
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> updateUserRemark(String openid, String remark, String configName) {
        String token = getAccessToken(configName);
        Map<String, Object> body = new HashMap<>();
        body.put("openid", openid);
        body.put("remark", remark);
        return executePostJson(API_BASE_URL + "/cgi-bin/user/info/updateremark?access_token=" + token,
                body, "updateUserRemark");
    }

    // ==================== 模板消息 ====================

    /**
     * 发送模板消息，对齐 PHP {@code WechatService::sendTemplate(template_id, openid, data, url, miniprogram)}。
     * <p>
     * API: {@code POST /cgi-bin/message/template/send}
     *
     * @param templateId  模板 ID
     * @param openid      接收者 openid
     * @param data        模板数据（key -> {value, color}）
     * @param url         点击跳转 URL（可为 null）
     * @param miniprogram 小程序跳转信息（可为 null，包含 appid、pagepath）
     * @return API 响应（含 msgid）
     */
    public Map<String, Object> sendTemplate(String templateId, String openid, Map<String, Object> data,
                                            String url, Map<String, Object> miniprogram) {
        return sendTemplate(templateId, openid, data, url, miniprogram, "default");
    }

    /**
     * 发送模板消息（指定配置）。
     *
     * @param templateId  模板 ID
     * @param openid      接收者 openid
     * @param data        模板数据
     * @param url         点击跳转 URL（可为 null）
     * @param miniprogram 小程序跳转信息（可为 null）
     * @param configName  公众号配置名
     * @return API 响应（含 msgid）
     */
    public Map<String, Object> sendTemplate(String templateId, String openid, Map<String, Object> data,
                                            String url, Map<String, Object> miniprogram, String configName) {
        String token = getAccessToken(configName);
        Map<String, Object> body = new HashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        body.put("data", data);
        if (url != null && !url.isEmpty()) {
            body.put("url", url);
        }
        if (miniprogram != null && !miniprogram.isEmpty()) {
            body.put("miniprogram", miniprogram);
        }
        return executePostJson(API_BASE_URL + "/cgi-bin/message/template/send?access_token=" + token,
                body, "sendTemplate");
    }

    // ==================== 菜单管理 ====================

    /**
     * 获取自定义菜单，对齐 PHP {@code WechatService::getMenu()}。
     * <p>
     * API: {@code GET /cgi-bin/menu/get}
     *
     * @return 菜单配置
     */
    public Map<String, Object> getMenu() {
        return getMenu("default");
    }

    /**
     * 获取自定义菜单（指定配置）。
     *
     * @param configName 公众号配置名
     * @return 菜单配置
     */
    public Map<String, Object> getMenu(String configName) {
        String token = getAccessToken(configName);
        String url = API_BASE_URL + "/cgi-bin/menu/get?access_token=" + token;
        return executeGet(url, "getMenu");
    }

    /**
     * 创建自定义菜单，对齐 PHP {@code WechatService::setMenu(json)}。
     * <p>
     * API: {@code POST /cgi-bin/menu/create}
     *
     * @param menuJson 菜单 JSON 结构（button 数组）
     * @return API 响应
     */
    public Map<String, Object> setMenu(Object menuJson) {
        return setMenu(menuJson, "default");
    }

    /**
     * 创建自定义菜单（指定配置）。
     *
     * @param menuJson   菜单 JSON 结构
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> setMenu(Object menuJson, String configName) {
        String token = getAccessToken(configName);
        return executePostJson(API_BASE_URL + "/cgi-bin/menu/create?access_token=" + token,
                menuJson, "setMenu");
    }

    // ==================== 标签管理 ====================

    /**
     * 创建标签，对齐 PHP {@code WechatService::createTag(name)}。
     * <p>
     * API: {@code POST /cgi-bin/tags/create}
     *
     * @param name 标签名（不超过 30 字符）
     * @return API 响应（含 tag.id 与 tag.name）
     */
    public Map<String, Object> createTag(String name) {
        return createTag(name, "default");
    }

    /**
     * 创建标签（指定配置）。
     *
     * @param name       标签名
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> createTag(String name, String configName) {
        String token = getAccessToken(configName);
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> tag = new HashMap<>();
        tag.put("name", name);
        body.put("tag", tag);
        return executePostJson(API_BASE_URL + "/cgi-bin/tags/create?access_token=" + token,
                body, "createTag");
    }

    /**
     * 获取标签列表，对齐 PHP {@code WechatService::getTag()}。
     * <p>
     * API: {@code GET /cgi-bin/tags/get}
     *
     * @return 标签列表
     */
    public Map<String, Object> getTags() {
        return getTags("default");
    }

    /**
     * 获取标签列表（指定配置）。
     *
     * @param configName 公众号配置名
     * @return 标签列表
     */
    public Map<String, Object> getTags(String configName) {
        String token = getAccessToken(configName);
        String url = API_BASE_URL + "/cgi-bin/tags/get?access_token=" + token;
        return executeGet(url, "getTags");
    }

    /**
     * 删除标签，对齐 PHP {@code WechatService::deleteTag(id)}。
     * <p>
     * API: {@code POST /cgi-bin/tags/delete}
     *
     * @param id 标签 ID
     * @return API 响应
     */
    public Map<String, Object> deleteTag(int id) {
        return deleteTag(id, "default");
    }

    /**
     * 删除标签（指定配置）。
     *
     * @param id         标签 ID
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> deleteTag(int id, String configName) {
        String token = getAccessToken(configName);
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> tag = new HashMap<>();
        tag.put("id", id);
        body.put("tag", tag);
        return executePostJson(API_BASE_URL + "/cgi-bin/tags/delete?access_token=" + token,
                body, "deleteTag");
    }

    /**
     * 批量为用户打标签，对齐 PHP {@code WechatService::batchTagging}。
     * <p>
     * API: {@code POST /cgi-bin/tags/members/batchtagging}
     *
     * @param tagId   标签 ID
     * @param openids 用户 openid 列表（最多 50 个）
     * @return API 响应
     */
    public Map<String, Object> batchTagging(int tagId, List<String> openids) {
        return batchTagging(tagId, openids, "default");
    }

    /**
     * 批量为用户打标签（指定配置）。
     *
     * @param tagId      标签 ID
     * @param openids    用户 openid 列表
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> batchTagging(int tagId, List<String> openids, String configName) {
        String token = getAccessToken(configName);
        Map<String, Object> body = new HashMap<>();
        body.put("tagid", tagId);
        body.put("openid_list", openids);
        return executePostJson(API_BASE_URL + "/cgi-bin/tags/members/batchtagging?access_token=" + token,
                body, "batchTagging");
    }

    /**
     * 批量取消用户标签，对齐 PHP {@code WechatService::batchUnTagging}。
     * <p>
     * API: {@code POST /cgi-bin/tags/members/batchuntagging}
     *
     * @param tagId   标签 ID
     * @param openids 用户 openid 列表
     * @return API 响应
     */
    public Map<String, Object> batchUnTagging(int tagId, List<String> openids) {
        return batchUnTagging(tagId, openids, "default");
    }

    /**
     * 批量取消用户标签（指定配置）。
     *
     * @param tagId      标签 ID
     * @param openids    用户 openid 列表
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> batchUnTagging(int tagId, List<String> openids, String configName) {
        String token = getAccessToken(configName);
        Map<String, Object> body = new HashMap<>();
        body.put("tagid", tagId);
        body.put("openid_list", openids);
        return executePostJson(API_BASE_URL + "/cgi-bin/tags/members/batchuntagging?access_token=" + token,
                body, "batchUnTagging");
    }

    // ==================== 素材管理 ====================

    /**
     * 上传临时图片素材，对齐 PHP {@code WechatService::uploadImageTemp(path)}。
     * <p>
     * API: {@code POST cgi-bin/media/upload?type=image}
     * 临时素材有效期为 3 天，media_id 可用于发送客服消息等场景。
     *
     * @param path 本地图片文件路径
     * @return API 响应（含 media_id、type、created_at）
     */
    public Map<String, Object> uploadImageTemp(String path) {
        return uploadImageTemp(path, "default");
    }

    /**
     * 上传临时图片素材（指定配置）。
     *
     * @param path       本地图片文件路径
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> uploadImageTemp(String path, String configName) {
        String token = getAccessToken(configName);
        String url = API_BASE_URL + "/cgi-bin/media/upload?access_token=" + token + "&type=image";
        return uploadFile(url, path, "media", "uploadImageTemp");
    }

    /**
     * 上传永久图片素材，对齐 PHP {@code WechatService::uploadImageFull(path)}。
     * <p>
     * API: {@code POST /cgi-bin/material/add_material?type=image}
     * 永久素材不会过期，但有数量限制。
     *
     * @param path 本地图片文件路径
     * @return API 响应（含 media_id、url）
     */
    public Map<String, Object> uploadImageFull(String path) {
        return uploadImageFull(path, "default");
    }

    /**
     * 上传永久图片素材（指定配置）。
     *
     * @param path       本地图片文件路径
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> uploadImageFull(String path, String configName) {
        String token = getAccessToken(configName);
        String url = API_BASE_URL + "/cgi-bin/material/add_material?access_token=" + token + "&type=image";
        return uploadFile(url, path, "media", "uploadImageFull");
    }

    /**
     * 获取永久素材，对齐 PHP {@code WechatService::downloadImageFull(media_id)}。
     * <p>
     * API: {@code POST /cgi-bin/material/get_material}
     *
     * @param mediaId 永久素材 media_id
     * @return API 响应（图片素材返回素材 URL 等信息）
     */
    public Map<String, Object> downloadImageFull(String mediaId) {
        return downloadImageFull(mediaId, "default");
    }

    /**
     * 获取永久素材（指定配置）。
     *
     * @param mediaId    永久素材 media_id
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> downloadImageFull(String mediaId, String configName) {
        String token = getAccessToken(configName);
        Map<String, Object> body = new HashMap<>();
        body.put("media_id", mediaId);
        return executePostJson(API_BASE_URL + "/cgi-bin/material/get_material?access_token=" + token,
                body, "downloadImageFull");
    }

    /**
     * 删除永久素材，对齐 PHP {@code WechatService::deleteImageFull(media_id)}。
     * <p>
     * API: {@code POST /cgi-bin/material/del_material}
     *
     * @param mediaId 永久素材 media_id
     * @return API 响应
     */
    public Map<String, Object> deleteImageFull(String mediaId) {
        return deleteImageFull(mediaId, "default");
    }

    /**
     * 删除永久素材（指定配置）。
     *
     * @param mediaId    永久素材 media_id
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> deleteImageFull(String mediaId, String configName) {
        String token = getAccessToken(configName);
        Map<String, Object> body = new HashMap<>();
        body.put("media_id", mediaId);
        return executePostJson(API_BASE_URL + "/cgi-bin/material/del_material?access_token=" + token,
                body, "deleteImageFull");
    }

    /**
     * 获取素材列表，对齐 PHP {@code WechatService::getMaterialList()}。
     * <p>
     * API: {@code POST /cgi-bin/material/batchget_material}
     *
     * @param type  素材类型（image / video / voice / news）
     * @param page  页码（从 0 开始）
     * @param count 每页数量（1-20）
     * @return API 响应（含 total_count、item_count、item 列表）
     */
    public Map<String, Object> getMaterialList(String type, int page, int count) {
        return getMaterialList(type, page, count, "default");
    }

    /**
     * 获取素材列表（指定配置）。
     *
     * @param type       素材类型
     * @param page       页码（从 0 开始）
     * @param count      每页数量
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> getMaterialList(String type, int page, int count, String configName) {
        String token = getAccessToken(configName);
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("offset", page * count);
        body.put("count", count);
        return executePostJson(API_BASE_URL + "/cgi-bin/material/batchget_material?access_token=" + token,
                body, "getMaterialList");
    }

    // ==================== 客服消息 ====================

    /**
     * 发送客服消息，对齐 PHP {@code WechatService::sendMessage(data)}。
     * <p>
     * API: {@code POST cgi-bin/message/custom/send}
     *
     * @param data 消息体（含 touser、msgtype、text/image/... 等）
     * @return API 响应
     */
    public Map<String, Object> sendMessage(Map<String, Object> data) {
        return sendMessage(data, "default");
    }

    /**
     * 发送客服消息（指定配置）。
     *
     * @param data       消息体
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> sendMessage(Map<String, Object> data, String configName) {
        String token = getAccessToken(configName);
        return executePostJson(API_BASE_URL + "/cgi-bin/message/custom/send?access_token=" + token,
                data, "sendMessage");
    }

    /**
     * 发送客服输入状态，对齐 PHP {@code WechatService::sendTyping(openid, command)}。
     * <p>
     * API: {@code POST /cgi-bin/message/custom/typing}
     *
     * @param openid  用户 openid
     * @param command 命令：typing（正在输入）/ cancel_typing（取消输入）
     * @return API 响应
     */
    public Map<String, Object> sendTyping(String openid, int command) {
        return sendTyping(openid, command, "default");
    }

    /**
     * 发送客服输入状态（指定配置）。
     *
     * @param openid     用户 openid
     * @param command    命令：0=typing，1=cancel_typing
     * @param configName 公众号配置名
     * @return API 响应
     */
    public Map<String, Object> sendTyping(String openid, int command, String configName) {
        String token = getAccessToken(configName);
        Map<String, Object> body = new HashMap<>();
        body.put("touser", openid);
        body.put("command", command == 0 ? "typing" : "cancel_typing");
        return executePostJson(API_BASE_URL + "/cgi-bin/message/custom/typing?access_token=" + token,
                body, "sendTyping");
    }

    // ==================== JSSDK 配置 ====================

    /**
     * 构建 JSSDK 配置，对齐 PHP {@code WechatService::controllerbuildJsSdkConfig(url, jsApiList, openTagList, debug)}。
     * <p>
     * JSSDK 配置用于前端页面调用微信 JSSDK 能力（如分享、扫一扫、地理位置等）。
     * 签名算法：
     * <ol>
     *   <li>获取 jsapi_ticket（缓存）</li>
     *   <li>生成随机 noncestr 与当前 timestamp</li>
     *   <li>拼接签名串：{@code jsapi_ticket={ticket}&noncestr={nonce}&timestamp={ts}&url={url}}</li>
     *   <li>对签名串做 SHA1 哈希，得到 signature</li>
     * </ol>
     *
     * @param url         当前页面 URL（不含 # 及之后部分）
     * @param jsApiList   需要使用的 JS 接口列表（如 ["chooseImage", "previewImage"]）
     * @param openTagList 开放标签列表（如 ["wx-open-launch-app"]，可为 null）
     * @param debug       是否开启调试模式
     * @return JSSDK 配置（appId, timestamp, nonceStr, signature, jsApiList, openTagList, debug）
     */
    public Map<String, Object> buildJsSdkConfig(String url, List<String> jsApiList,
                                                List<String> openTagList, boolean debug) {
        return buildJsSdkConfig(url, jsApiList, openTagList, debug, "default");
    }

    /**
     * 构建 JSSDK 配置（指定配置）。
     *
     * @param url         当前页面 URL
     * @param jsApiList   JS 接口列表
     * @param openTagList 开放标签列表（可为 null）
     * @param debug       是否调试模式
     * @param configName  公众号配置名
     * @return JSSDK 配置
     */
    public Map<String, Object> buildJsSdkConfig(String url, List<String> jsApiList,
                                                List<String> openTagList, boolean debug,
                                                String configName) {
        WechatProperties.OfficialAccountConfig config = properties.getOfficialAccount(configName);
        if (config == null) {
            throw new IllegalStateException("未找到公众号配置: " + configName);
        }

        // 获取 jsapi_ticket
        String ticket = getJsApiTicket(configName);
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        // 拼接签名串并 SHA1
        String signStr = "jsapi_ticket=" + ticket + "&noncestr=" + nonceStr
                + "&timestamp=" + timestamp + "&url=" + url;
        String signature = sha1(signStr);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appId", config.getAppId());
        result.put("timestamp", timestamp);
        result.put("nonceStr", nonceStr);
        result.put("signature", signature);
        result.put("jsApiList", jsApiList);
        if (openTagList != null) {
            result.put("openTagList", openTagList);
        }
        result.put("debug", debug);

        logger.debug("[wechat] JSSDK 配置生成成功: appId={}, url={}", config.getAppId(), url);
        return result;
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 获取指定配置的 access_token。
     *
     * @param configName 公众号配置名
     * @return access_token
     */
    private String getAccessToken(String configName) {
        WechatProperties.OfficialAccountConfig config = properties.getOfficialAccount(configName);
        if (config == null) {
            throw new IllegalStateException("未找到公众号配置: " + configName);
        }
        return accessTokenManager.getToken(config.getAppId(), config.getSecret());
    }

    /**
     * 获取 jsapi_ticket（带缓存），对齐 EasyWeChat 的 jsapi_ticket 缓存机制。
     * <p>
     * 缓存 key 格式：{@code wechat:jsapi_ticket:{appId}}，TTL = expires_in - 300（提前 5 分钟过期）。
     * 缓存仓库优先 redis（多实例共享），未注册时回退 array 内存缓存。
     * <p>
     * API: {@code GET /cgi-bin/ticket/getticket?type=jsapi}
     *
     * @param configName 公众号配置名
     * @return jsapi_ticket
     */
    private String getJsApiTicket(String configName) {
        WechatProperties.OfficialAccountConfig config = properties.getOfficialAccount(configName);
        if (config == null) {
            throw new IllegalStateException("未找到公众号配置: " + configName);
        }
        String appId = config.getAppId();
        String cacheKey = TICKET_CACHE_PREFIX + appId;

        // 1. 优先从缓存读取
        String cached = cacheStore.get(cacheKey, String.class);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 2. 缓存未命中，请求微信 API
        String token = accessTokenManager.getToken(config.getAppId(), config.getSecret());
        String url = API_BASE_URL + "/cgi-bin/ticket/getticket?access_token=" + token + "&type=jsapi";
        Map<String, Object> result = executeGet(url, "getJsApiTicket");

        String ticket = (String) result.get("ticket");
        if (ticket == null || ticket.isEmpty()) {
            throw new RuntimeException("获取 jsapi_ticket 失败: " + result.get("errmsg"));
        }

        int expiresIn = result.get("expires_in") != null
                ? ((Number) result.get("expires_in")).intValue() : 7200;
        long ttlSeconds = Math.max(expiresIn - TICKET_BUFFER_SECONDS, 60);

        // 3. 写入缓存
        cacheStore.put(cacheKey, ticket, ttlSeconds);

        logger.info("[wechat] 获取 jsapi_ticket 成功: appId={}, expires_in={}s", appId, expiresIn);
        return ticket;
    }

    /**
     * 执行 GET 请求并解析 JSON 响应。
     *
     * @param url       完整请求 URL
     * @param operation 操作名（用于日志）
     * @return 解析后的 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeGet(String url, String operation) {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            return parseResponse(response, operation);
        } catch (IOException e) {
            logger.error("[wechat] {} 网络异常: {}", operation, e.getMessage());
            throw new RuntimeException(operation + " 网络异常: " + e.getMessage(), e);
        }
    }

    /**
     * 执行 POST 请求（JSON body）并解析响应。
     *
     * @param url       完整请求 URL
     * @param body      请求体对象（将序列化为 JSON）
     * @param operation 操作名
     * @return 解析后的 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executePostJson(String url, Object body, String operation) {
        try {
            String json = objectMapper.writeValueAsString(body);
            RequestBody requestBody = RequestBody.create(json, JSON_MEDIA_TYPE);
            Request request = new Request.Builder().url(url).post(requestBody).build();
            try (Response response = httpClient.newCall(request).execute()) {
                return parseResponse(response, operation);
            }
        } catch (IOException e) {
            logger.error("[wechat] {} 网络异常: {}", operation, e.getMessage());
            throw new RuntimeException(operation + " 网络异常: " + e.getMessage(), e);
        }
    }

    /**
     * 上传文件（multipart/form-data）并解析响应。
     *
     * @param url       完整请求 URL
     * @param path      本地文件路径
     * @param formName  表单字段名（media / media 等）
     * @param operation 操作名
     * @return 解析后的 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> uploadFile(String url, String path, String formName, String operation) {
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + path);
        }
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
        MultipartBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(formName, file.getName(), fileBody)
                .build();
        Request request = new Request.Builder().url(url).post(multipart).build();
        try (Response response = httpClient.newCall(request).execute()) {
            return parseResponse(response, operation);
        } catch (IOException e) {
            logger.error("[wechat] {} 网络异常: {}", operation, e.getMessage());
            throw new RuntimeException(operation + " 网络异常: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 HTTP 响应为 Map。
     *
     * @param response  OkHttp 响应
     * @param operation 操作名
     * @return 解析后的 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(Response response, String operation) throws IOException {
        String body = response.body() != null ? response.body().string() : "";
        if (!response.isSuccessful()) {
            logger.error("[wechat] {} HTTP 失败: code={}, body={}", operation, response.code(), body);
            throw new RuntimeException(operation + " HTTP 失败: " + response.code());
        }
        Map<String, Object> result = objectMapper.readValue(body, Map.class);
        // 检查微信业务错误码（errcode 非 0 表示失败，部分接口无 errcode 字段）
        Object errcode = result.get("errcode");
        if (errcode != null && errcode instanceof Number && ((Number) errcode).intValue() != 0) {
            logger.warn("[wechat] {} 业务失败: errcode={}, errmsg={}", operation, errcode, result.get("errmsg"));
        }
        return result;
    }

    /**
     * SHA1 哈希计算。
     *
     * @param input 输入字符串
     * @return SHA1 十六进制摘要
     */
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

    /**
     * 解析缓存仓库：优先 {@code redis} store（多实例共享），未注册时回退到 {@code array} 内存 store。
     * <p>
     * 当 {@link CacheManager} 为空（cache 自动装配未启用）时，使用独立的内存 store 保证 SDK 仍可用。
     *
     * @param cacheManager 缓存管理器，可为 null
     * @return 解析出的缓存仓库
     */
    private static CacheStore resolveStore(CacheManager cacheManager, String preferredStore) {
        if (cacheManager == null) {
            logger.warn("[wechat] CacheManager 未注入，jsapi_ticket 使用本地内存缓存");
            return new DefaultCacheStore(new ArrayCacheDriver(), "");
        }
        if (preferredStore == null || preferredStore.isEmpty()) {
            preferredStore = "redis";
        }
        try {
            return cacheManager.store(preferredStore);
        } catch (IllegalStateException e) {
            logger.debug("[wechat] 缓存 store '{}' 未注册，jsapi_ticket 回退到 array 内存缓存: {}", preferredStore, e.getMessage());
            return cacheManager.store("array");
        }
    }
}
