package com.weacsoft.jaravel.vendor.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信小程序服务，对齐 PHP 项目的 {@code MiniProgramService}。
 * <p>
 * 封装微信小程序常用 API，包括登录凭证校验（jscode2session）、
 * 小程序 access_token 获取、订阅消息发送等。
 *
 * <h3>PHP 对齐关系</h3>
 * <p>
 * PHP 项目中 {@code MiniProgramService} 目前为空实现，本类作为 Java 侧的完整实现，
 * 对齐 EasyWeChat MiniProgram 模块的核心能力。
 *
 * <h3>多小程序支持</h3>
 * 支持多小程序配置（如客服小程序 wx7051c4a2a779d651、管理端小程序 wxb33c8c0f6bea3602），
 * 通过 appId 参数区分不同小程序，配置从 {@link WechatProperties#getMiniApps()} 获取。
 *
 * <h3>线程安全</h3>
 * 本类为无状态单例（配置、客户端、解析器均为构造后不可变字段），可被多线程并发安全调用。
 *
 * @author weacsoft
 */
public class MiniProgramService {

    private static final Logger logger = LoggerFactory.getLogger(MiniProgramService.class);

    /** 微信 API 基础地址 */
    private static final String API_BASE_URL = "https://api.weixin.qq.com";

    /** JSON 媒体类型 */
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** Access Token 管理器 */
    private final AccessTokenManager accessTokenManager;

    /** 微信配置属性 */
    private final WechatProperties properties;

    /** OkHttp 客户端 */
    private final OkHttpClient httpClient;

    /** Jackson JSON 解析器 */
    private final ObjectMapper objectMapper;

    /**
     * 构造小程序服务。
     *
     * @param accessTokenManager Access Token 管理器
     * @param properties         微信配置属性
     * @param httpClient         OkHttp 客户端
     * @param objectMapper       Jackson JSON 解析器
     */
    public MiniProgramService(AccessTokenManager accessTokenManager,
                              WechatProperties properties,
                              OkHttpClient httpClient,
                              ObjectMapper objectMapper) {
        this.accessTokenManager = accessTokenManager;
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 小程序登录凭证校验，对齐 EasyWeChat MiniProgram 的 session 接口。
     * <p>
     * 小程序前端调用 {@code wx.login()} 获取 code 后，将其发送到后端，
     * 后端调用本方法用 code 换取 openid 和 session_key。
     * <p>
     * API: {@code GET https://api.weixin.qq.com/sns/jscode2session
     * ?appid={appId}&secret={secret}&js_code={code}&grant_type=authorization_code}
     *
     * @param appId 小程序 AppID（如 wx7051c4a2a779d651）
     * @param code  小程序登录凭证（wx.login 返回的 code）
     * @return API 响应（含 openid、session_key、unionid 等）
     * @throws RuntimeException API 返回错误或网络异常时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> jscode2session(String appId, String code) {
        WechatProperties.MiniAppConfig config = properties.getMiniApp(appId);
        if (config == null) {
            throw new IllegalStateException("未找到小程序配置: " + appId);
        }

        String url = API_BASE_URL + "/sns/jscode2session?appid=" + config.getAppId()
                + "&secret=" + config.getSecret()
                + "&js_code=" + code
                + "&grant_type=authorization_code";

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                logger.error("[wechat-mini] jscode2session HTTP 失败: appId={}, code={}, resp={}",
                        appId, response.code(), body);
                throw new RuntimeException("jscode2session HTTP 失败: " + response.code());
            }
            Map<String, Object> result = objectMapper.readValue(body, Map.class);
            Object errcode = result.get("errcode");
            if (errcode != null && errcode instanceof Number && ((Number) errcode).intValue() != 0) {
                logger.warn("[wechat-mini] jscode2session 业务失败: appId={}, errcode={}, errmsg={}",
                        appId, errcode, result.get("errmsg"));
            } else {
                logger.info("[wechat-mini] jscode2session 成功: appId={}", appId);
            }
            return result;
        } catch (IOException e) {
            logger.error("[wechat-mini] jscode2session 网络异常: appId={}", appId, e);
            throw new RuntimeException("jscode2session 网络异常: " + e.getMessage(), e);
        }
    }

    /**
     * 获取小程序 access_token，对齐 EasyWeChat MiniProgram 的 AccessToken。
     * <p>
     * 小程序 access_token 与公众号 access_token 使用相同的获取接口，但 appId/secret 不同。
     * 本方法委托 {@link AccessTokenManager} 获取并缓存，避免频繁刷新。
     *
     * @param appId 小程序 AppID
     * @return access_token 字符串
     */
    public String getAccessToken(String appId) {
        WechatProperties.MiniAppConfig config = properties.getMiniApp(appId);
        if (config == null) {
            throw new IllegalStateException("未找到小程序配置: " + appId);
        }
        return accessTokenManager.getToken(config.getAppId(), config.getSecret());
    }

    /**
     * 发送订阅消息，对齐 EasyWeChat MiniProgram 的 subscribe message。
     * <p>
     * API: {@code POST /cgi-bin/message/subscribe/send}
     * <p>
     * 小程序订阅消息需要用户在前端主动订阅后才能发送，每次发送消耗一次订阅配额。
     *
     * @param appId      小程序 AppID
     * @param openid     接收者 openid
     * @param templateId 订阅消息模板 ID
     * @param data       模板数据（key -> {value}）
     * @param page       点击跳转的小程序页面路径（如 pages/index/index，可为 null）
     * @return API 响应
     * @throws RuntimeException API 返回错误或网络异常时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sendTemplateMessage(String appId, String openid, String templateId,
                                                   Map<String, Object> data, String page) {
        WechatProperties.MiniAppConfig config = properties.getMiniApp(appId);
        if (config == null) {
            throw new IllegalStateException("未找到小程序配置: " + appId);
        }

        String token = accessTokenManager.getToken(config.getAppId(), config.getSecret());

        Map<String, Object> body = new HashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        body.put("data", data);
        if (page != null && !page.isEmpty()) {
            body.put("page", page);
        }
        // miniprogram_state: formal(正式) / trial(体验) / developer(开发)
        body.put("miniprogram_state", "formal");
        body.put("lang", "zh_CN");

        String url = API_BASE_URL + "/cgi-bin/message/subscribe/send?access_token=" + token;
        try {
            String json = objectMapper.writeValueAsString(body);
            RequestBody requestBody = RequestBody.create(json, JSON_MEDIA_TYPE);
            Request request = new Request.Builder().url(url).post(requestBody).build();
            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    logger.error("[wechat-mini] sendTemplateMessage HTTP 失败: appId={}, code={}, body={}",
                            appId, response.code(), respBody);
                    throw new RuntimeException("sendTemplateMessage HTTP 失败: " + response.code());
                }
                Map<String, Object> result = objectMapper.readValue(respBody, Map.class);
                Object errcode = result.get("errcode");
                if (errcode != null && errcode instanceof Number && ((Number) errcode).intValue() != 0) {
                    logger.warn("[wechat-mini] sendTemplateMessage 业务失败: appId={}, errcode={}, errmsg={}",
                            appId, errcode, result.get("errmsg"));
                } else {
                    logger.info("[wechat-mini] sendTemplateMessage 成功: appId={}, openid={}", appId, openid);
                }
                return result;
            }
        } catch (IOException e) {
            logger.error("[wechat-mini] sendTemplateMessage 网络异常: appId={}", appId, e);
            throw new RuntimeException("sendTemplateMessage 网络异常: " + e.getMessage(), e);
        }
    }
}
