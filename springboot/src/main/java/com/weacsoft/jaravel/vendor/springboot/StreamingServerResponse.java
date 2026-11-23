package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.StreamingResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.OutputStream;
import java.util.List;

/**
 * 把 jaravel 的 {@link StreamingResponse} 原样写进 servlet 输出流的 {@link ServerResponse}。
 * <p>
 * <b>为什么必须自己实现，而不是用 Spring 现成的入口</b>：三处经验证都走不通，
 * 根因是 {@code RouterFunction} 这条链路不具备 {@code @RequestMapping} 那种
 * 返回值处理器链：
 * <ol>
 *   <li>{@code ServerResponse.BodyBuilder.body(自定义对象)}
 *       —— 交给消息转换器，抛
 *       {@code HttpMessageNotWritableException: No converter for [...]}；</li>
 *   <li>{@code ServerResponse.BodyBuilder.body(StreamingResponseBody)}
 *       —— {@code StreamingResponseBody} 依赖 MVC 的 return-value handler
 *       与 media-type 协商，在 {@code RouterFunction} 下会被当作普通 body 处理，
 *       抛 {@code HttpMediaTypeNotAcceptableException: No acceptable representation}；</li>
 *   <li>{@code ServerResponse.sse(...)}
 *       —— {@code SseBuilder.send(Object)} 会把内容再包一层 {@code data:} 前缀，
 *       而上游给的本就是完整 SSE 文本（自带 {@code data:} 与 {@code [DONE]}），
 *       会双重包装、破坏协议。</li>
 * </ol>
 * <p>
 * 唯一既保持「逐块 flush」、又不改动内容的方式，是拿到 servlet 输出流自己写。
 * {@link #writeTo} 的第三个参数 {@code Context} 不用，我们只用前两个参数。
 */
final class StreamingServerResponse implements ServerResponse {

    private final Response delegate;
    private final StreamingResponse streaming;

    /**
     * @param delegate  原始响应（提供 status / headers）
     * @param streaming 流式写出逻辑
     */
    StreamingServerResponse(Response delegate, StreamingResponse streaming) {
        this.delegate = delegate;
        this.streaming = streaming;
    }

    @Override
    public HttpStatusCode statusCode() {
        return HttpStatusCode.valueOf(delegate.getStatus());
    }

    @Override
    public int rawStatusCode() {
        return delegate.getStatus();
    }

    @Override
    public HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        delegate.getHeaders().forEach((name, values) -> values.forEach(v -> headers.add(name, v)));
        return headers;
    }

    @Override
    public MultiValueMap<String, jakarta.servlet.http.Cookie> cookies() {
        MultiValueMap<String, jakarta.servlet.http.Cookie> map = new LinkedMultiValueMap<>();
        jakarta.servlet.http.Cookie[] cookies = delegate.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                map.add(cookie.getName(), cookie);
            }
        }
        return map;
    }

    /**
     * 直接写 servlet 输出流。
     * <p>
     * <b>返回 {@code null}</b> 是关键：它告诉 Spring「响应已由本对象自行写出，
     * 不要再做视图解析/内容协商」。
     *
     * @param servletRequest  原始请求
     * @param servletResponse 原始响应
     * @param context         Spring 的上下文（本实现不需要）
     * @return 恒为 {@code null}
     */
    @Override
    public ModelAndView writeTo(HttpServletRequest servletRequest,
                                HttpServletResponse servletResponse,
                                ServerResponse.Context context)
            throws java.io.IOException, jakarta.servlet.ServletException {
        servletResponse.setStatus(delegate.getStatus());
        delegate.getHeaders().forEach((name, values) ->
                values.forEach(v -> servletResponse.addHeader(name, v)));
        jakarta.servlet.http.Cookie[] cookies = delegate.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                servletResponse.addCookie(cookie);
            }
        }

        OutputStream out = servletResponse.getOutputStream();
        try {
            streaming.writeTo(out);
            out.flush();
        } catch (java.io.IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // StreamingResponse.writeTo 声明 throws Exception，这里统一转 IOException
            throw new java.io.IOException(e);
        }
        return null;
    }

    /**
     * 便捷判断：响应是否需要走流式分支。
     *
     * @param response jaravel 响应
     * @return 是流式响应返回 true
     */
    static boolean isStreaming(Response response) {
        return response instanceof StreamingResponse;
    }

    /**
     * 构造流式 {@link ServerResponse}。
     *
     * @param response jaravel 响应（须实现 {@link StreamingResponse}）
     * @return ServerResponse
     */
    static ServerResponse of(Response response) {
        return new StreamingServerResponse(response, (StreamingResponse) response);
    }

    /** 供日志/调试：当前响应设置了哪些头 */
    List<String> headerNames() {
        return delegate.getHeaders().keySet().stream().toList();
    }
}
