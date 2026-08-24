package com.weacsoft.jaravel.vendor.wechat.transport;

import com.weacsoft.jaravel.vendor.wechat.response.WeChatResponse;
import com.weacsoft.jaravel.vendor.wechat.response.WechatApiException;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * 微信 API HTTP 传输层：统一管理 OkHttp 调用、URL 构造、响应解析。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>所有调用以<b>相对路径</b>（如 {@code cgi-bin/message/custom/send}）表达，
 *       由本类统一拼接 {@code https://api.weixin.qq.com} 与 query 参数（值自动 URL 编码）</li>
 *   <li>响应统一收敛为 {@link WeChatResponse}（提取 errcode/errmsg/msgid），
 *       各服务方法不再重复解析响应</li>
 *   <li>HTTP 非 2xx 直接抛 {@link WechatApiException}（微信 API 的业务失败是 HTTP 200 + errcode）</li>
 *   <li>二进制接口（小程序码图片）单独提供 {@code getBinary}/{@code postBinary}：
 *       image/* 响应返回字节，application/json 响应按业务错误抛出</li>
 * </ul>
 *
 * 本类无状态（OkHttpClient 线程安全），可跨线程共享。
 *
 * @author weacsoft
 */
public final class WechatTransport {

    private static final Logger logger = LoggerFactory.getLogger(WechatTransport.class);

    /** 微信 API 基础地址 */
    private static final String API_BASE_URL = "https://api.weixin.qq.com";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private static final MediaType OCTET_STREAM = MediaType.get("application/octet-stream");

    private final OkHttpClient httpClient;

    /** 微信请求体 JSON 序列化器（由 json 模块统一提供） */
    private final RequestJsonEncoder encoder;

    /**
     * 构造传输层。
     *
     * @param httpClient OkHttp 客户端
     * @param encoder    请求体 JSON 编码器
     */
    public WechatTransport(OkHttpClient httpClient, RequestJsonEncoder encoder) {
        this.httpClient = httpClient;
        this.encoder = encoder;
    }

    /**
     * GET 请求（query 参数值自动 URL 编码）。
     *
     * @param path      相对路径（不含前导 /）
     * @param query     query 参数，可为 null
     * @param operation 操作名（写入日志/异常）
     * @return 封装后的响应
     * @throws WechatApiException HTTP 非 2xx 或网络异常
     */
    public WeChatResponse get(String path, Map<String, String> query, String operation) {
        Request request = newBuilder(operation).url(buildUrl(path, query, false)).get().build();
        return execute(request, operation);
    }

    /**
     * GET 请求，二进制响应（如小程序码图片）。
     *
     * @param path      相对路径
     * @param query     query 参数
     * @param operation 操作名
     * @return 图片字节流
     * @throws WechatApiException 响应为 JSON（业务错误）或非 2xx
     */
    public byte[] getBinary(String path, Map<String, String> query, String operation) {
        Request request = newBuilder(operation).url(buildUrl(path, query, false)).get().build();
        return executeBinary(request, operation);
    }

    /**
     * POST JSON 请求。
     *
     * @param path      相对路径
     * @param query     query 参数（如 access_token），可为 null
     * @param body      请求体（Map/Object，由编码器序列化为 JSON）
     * @param operation 操作名
     * @return 封装后的响应
     * @throws WechatApiException HTTP 非 2xx 或网络异常
     */
    public WeChatResponse postJson(String path, Map<String, String> query, Object body, String operation) {
        Request request = newBuilder(operation)
                .url(buildUrl(path, query, true))
                .post(RequestBody.create(encoder.encode(body), JSON_MEDIA_TYPE))
                .build();
        return execute(request, operation);
    }

    /**
     * POST 二进制请求（如小程序码生成），响应为图片字节流。
     *
     * @param path      相对路径
     * @param query     query 参数
     * @param body      请求体
     * @param operation 操作名
     * @return 图片字节流
     * @throws WechatApiException 响应为 JSON（业务错误）或非 2xx
     */
    public byte[] postBinary(String path, Map<String, String> query, Object body, String operation) {
        Request request = newBuilder(operation)
                .url(buildUrl(path, query, true))
                .post(RequestBody.create(encoder.encode(body), JSON_MEDIA_TYPE))
                .build();
        return executeBinary(request, operation);
    }

    /**
     * multipart 文件上传（素材接口）。
     *
     * @param path      相对路径
     * @param query     query 参数（如 access_token、type）
     * @param file      本地文件
     * @param formName  multipart 字段名（素材接口均为 media）
     * @param operation 操作名
     * @return 封装后的响应
     * @throws WechatApiException HTTP 非 2xx 或网络异常
     */
    public WeChatResponse upload(String path, Map<String, String> query, File file, String formName, String operation) {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(formName, file.getName(), RequestBody.create(file, OCTET_STREAM))
                .build();
        Request request = newBuilder(operation).url(buildUrl(path, query, false)).post(body).build();
        return execute(request, operation);
    }

    private Request.Builder newBuilder(String operation) {
        logger.debug("[wechat] 请求: {} ...", operation);
        return new Request.Builder();
    }

    private String buildUrl(String path, Map<String, String> query, boolean requirePost) {
        HttpUrl.Builder builder = HttpUrl.parse(API_BASE_URL + "/" + path).newBuilder();
        if (query != null) {
            query.forEach((key, value) -> {
                if (value != null) {
                    builder.addQueryParameter(key, value);
                }
            });
        }
        return builder.build().toString();
    }

    private WeChatResponse execute(Request request, String operation) {
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String respBody = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                logger.error("[wechat] {} HTTP 失败: {} resp={}", operation, response.code(), respBody);
                throw new WechatApiException(operation + " HTTP 失败: " + response.code());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = encoder.parseToMap(respBody.isEmpty() ? "{}" : respBody);
            WeChatResponse wrapped = WeChatResponse.of(result);
            logBusinessResult(operation, wrapped);
            return wrapped;
        } catch (IOException e) {
            logger.error("[wechat] {} 网络异常: {}", operation, e.getMessage());
            throw new WechatApiException(operation + " 网络异常: " + e.getMessage());
        }
    }

    private byte[] executeBinary(Request request, String operation) {
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                String respBody = body != null ? body.string() : "";
                logger.error("[wechat] {} HTTP 失败: {} resp={}", operation, response.code(), respBody);
                throw new WechatApiException(operation + " HTTP 失败: " + response.code());
            }
            String contentType = response.header("Content-Type", "");
            if (contentType.startsWith("image/")) {
                return body != null ? body.bytes() : new byte[0];
            }
            // 微信对图片类接口的业务错误仍以 JSON 返回
            String json = body != null ? body.string() : "";
            @SuppressWarnings("unchecked")
            Map<String, Object> result = json.isEmpty() ? Map.of() : encoder.parseToMap(json);
            WeChatResponse wrapped = WeChatResponse.of(result);
            wrapped.requireSuccess(operation);
            throw new WechatApiException(operation + " 响应非图片: " + contentType);
        } catch (IOException e) {
            logger.error("[wechat] {} 网络异常: {}", operation, e.getMessage());
            throw new WechatApiException(operation + " 网络异常: " + e.getMessage());
        }
    }

    private void logBusinessResult(String operation, WeChatResponse wrapped) {
        if (wrapped.isSuccess()) {
            logger.debug("[wechat] {} 成功{}", operation,
                    wrapped.getMsgId() != null ? " msgid=" + wrapped.getMsgId() : "");
        } else {
            logger.warn("[wechat] {} 业务失败: errcode={} errmsg={}",
                    operation, wrapped.getErrcode(), wrapped.getErrmsg());
        }
    }
}
