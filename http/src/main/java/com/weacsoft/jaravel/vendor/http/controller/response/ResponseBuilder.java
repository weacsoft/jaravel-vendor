package com.weacsoft.jaravel.vendor.http.controller.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResponseBuilder {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static Object bladeEngine;

    private static Object getBladeEngine() {
        if (bladeEngine == null) {
            throw new RuntimeException("jblade 模块未引入");
        }
        return bladeEngine;
    }

    public static void setBladeEngine(Object engine) {
        bladeEngine = engine;
    }

    public static Response ok() {
        return new AbstractResponse() {
            @Override
            public int getStatus() {
                return 200;
            }

            @Override
            public String getContent() {
                return "ok";
            }
        };
    }

    public static Response view(String templateName, Map<String, Object> data) {
        return new AbstractResponse() {
            {
                addHeader("Content-Type", "text/html; charset=utf-8");
            }

            @Override
            public int getStatus() {
                return 200;
            }

            @Override
            public String getContent() {
                try {
                    Object engine = getBladeEngine();
                    Method renderMethod = engine.getClass().getMethod("render", String.class, Map.class);
                    return (String) renderMethod.invoke(engine, templateName, data);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to render template: " + templateName, e);
                }
            }
        };
    }

    // ===== Wire 方法已迁移到 WireResponse =====
    // 请使用 WireResponse.wire() / WireResponse.update() / WireResponse.redirect() / WireResponse.error()
    // WireResponse 位于 wire 模块，直接调用 WireManager 无需反射。

    public static Response json(Object data) {
        return new AbstractResponse() {
            {
                addHeader("Content-Type", "application/json; charset=utf-8");
            }

            @Override
            public int getStatus() {
                return 200;
            }

            @Override
            public String getContent() {
                try {
                    return objectMapper.writeValueAsString(data);
                } catch (Exception e) {
                    throw new RuntimeException("JSON 序列化失败", e);
                }
            }
        };
    }

    public static Response content(String content) {
        return new AbstractResponse() {
            {
                addHeader("Content-Type", "text/plain; charset=utf-8");
            }

            @Override
            public int getStatus() {
                return 200;
            }

            @Override
            public String getContent() {
                return content;
            }
        };
    }

    /**
     * HTML 响应。
     *
     * @param html HTML 内容
     * @return Content-Type 为 text/html 的响应
     */
    public static Response html(String html) {
        return new AbstractResponse() {
            {
                addHeader("Content-Type", "text/html; charset=utf-8");
            }

            @Override
            public int getStatus() {
                return 200;
            }

            @Override
            public String getContent() {
                return html;
            }
        };
    }

    public static Response file(byte[] data, String filename) {
        return new AbstractResponse() {
            {
                addHeader("Content-Type", "application/octet-stream");
                addHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            }

            @Override
            public int getStatus() {
                return 200;
            }

            @Override
            public String getContent() {
                return null;
            }

            @Override
            public byte[] getBytes() {
                return data;
            }
        };
    }

    /**
     * 构建静态资源响应（inline 显示，带缓存头和 MIME 类型）。
     * <p>
     * 与 {@link #file} 不同，此方法用于服务静态资源文件（css/js/图片等），
     * 设置正确的 Content-Type 和 Cache-Control 头，浏览器直接渲染而非下载。
     *
     * @param data        文件内容
     * @param mimeType    MIME 类型（如 {@code text/css}）
     * @param cacheMaxAge 缓存时间（秒）
     * @return 静态资源响应
     */
    public static Response staticFile(byte[] data, String mimeType, int cacheMaxAge) {
        return new AbstractResponse() {
            {
                addHeader("Content-Type", mimeType);
                addHeader("Cache-Control", "public, max-age=" + cacheMaxAge);
                addHeader("Content-Length", String.valueOf(data.length));
            }

            @Override
            public int getStatus() {
                return 200;
            }

            @Override
            public String getContent() {
                return null;
            }

            @Override
            public byte[] getBytes() {
                return data;
            }
        };
    }

    public static Response unauthorized(String message) {
        return error(401, message);
    }

    public static Response forbidden(String message) {
        return error(403, message);
    }

    public static Response error(int status, String message) {
        return new AbstractResponse() {
            {
                addHeader("Content-Type", "application/json; charset=utf-8");
            }

            @Override
            public int getStatus() {
                return status;
            }

            @Override
            public String getContent() {
                try {
                    Map<String, String> map = new HashMap<>();
                    map.put("message", message);
                    return objectMapper.writeValueAsString(map);
                } catch (Exception e) {
                    throw new RuntimeException("JSON 序列化失败", e);
                }
            }
        };
    }

    /**
     * 返回错误响应，附带额外的 redirect 字段。
     * 用于 Wire 请求认证过期时，告知前端跳转到登录页。
     *
     * @param status    HTTP 状态码（如 401）
     * @param message   错误消息
     * @param redirect  重定向 URL（如 /login）
     */
    public static Response error(int status, String message, String redirect) {
        return new AbstractResponse() {
            {
                addHeader("Content-Type", "application/json; charset=utf-8");
            }

            @Override
            public int getStatus() {
                return status;
            }

            @Override
            public String getContent() {
                try {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("message", message);
                    map.put("redirect", redirect);
                    return objectMapper.writeValueAsString(map);
                } catch (Exception e) {
                    throw new RuntimeException("JSON 序列化失败", e);
                }
            }
        };
    }

    public static Response redirect(String url) {
        return new AbstractResponse() {
            {
                addHeader("Location", url);
            }

            @Override
            public int getStatus() {
                return 302;
            }

            @Override
            public String getContent() {
                return "";
            }
        };
    }

    /** 将对象序列化为 JSON 字符串 */
    public static String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    // ===== Raw 模式 =====

    /**
     * Raw 模式：创建一个完全空的响应构建器，开发者自由组织 header 和 body。
     * <p>
     * 不预设任何 Content-Type 或状态码，全部由开发者决定。
     * 如果最终没有设置 Content-Type，框架会在写入 HTTP 响应时兜底为 {@code text/plain;charset=utf-8}。
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 自定义 XML 响应
     * return ResponseBuilder.raw()
     *     .status(200)
     *     .header("Content-Type", "application/xml;charset=utf-8")
     *     .header("X-Custom-Header", "hello")
     *     .body("<xml><name>test</name></xml>");
     *
     * // 自定义二进制响应
     * return ResponseBuilder.raw()
     *     .status(200)
     *     .header("Content-Type", "image/png")
     *     .body(imageBytes);
     *
     * // 不设 Content-Type，框架兜底为 text/plain
     * return ResponseBuilder.raw()
     *     .status(204)
     *     .body("");
     * }</pre>
     *
     * @return RawResponse 构建器
     */
    public static RawResponse raw() {
        return new RawResponse();
    }

    /**
     * Raw 响应构建器：链式设置 status / header / cookie / body，不预设任何值。
     */
    public static class RawResponse implements Response {
        private int status = 200;
        private final Map<String, List<String>> headers = new LinkedHashMap<>();
        private final List<Cookie> cookies = new ArrayList<>();
        private String content;
        private byte[] bytes;

        /**
         * 设置 HTTP 状态码（默认 200）。
         */
        public RawResponse status(int status) {
            this.status = status;
            return this;
        }

        /**
         * 添加响应头。
         */
        public RawResponse header(String name, String value) {
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            return this;
        }

        /**
         * 设置 Content-Type。
         */
        public RawResponse contentType(String contentType) {
            headers.put("Content-Type", new ArrayList<>(List.of(contentType)));
            return this;
        }

        /**
         * 添加 Cookie。
         */
        public RawResponse cookie(Cookie cookie) {
            cookies.add(cookie);
            return this;
        }

        /**
         * 添加 Cookie（简易方式）。
         */
        public RawResponse cookie(String name, String value) {
            cookies.add(new Cookie(name, value));
            return this;
        }

        /**
         * 设置文本响应体。
         */
        public Response body(String content) {
            this.content = content;
            return this;
        }

        /**
         * 设置二进制响应体。
         */
        public Response body(byte[] bytes) {
            this.bytes = bytes;
            return this;
        }

        // ===== Response 接口实现 =====

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            return new LinkedHashMap<>(headers);
        }

        @Override
        public void addHeader(String name, String value) {
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }

        @Override
        public Cookie[] getCookies() {
            return cookies.toArray(new Cookie[0]);
        }

        @Override
        public void addCookie(Cookie cookie) {
            cookies.add(cookie);
        }

        @Override
        public void addCookie(String name, String value) {
            cookies.add(new Cookie(name, value));
        }

        @Override
        public String getContent() {
            return content;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }
    }

    private static abstract class AbstractResponse implements Response {
        protected final Map<String, List<String>> headers = new HashMap<>();
        protected final List<Cookie> cookies = new ArrayList<>();

        @Override
        public Map<String, List<String>> getHeaders() {
            return new HashMap<>(headers);
        }

        @Override
        public void addHeader(String name, String value) {
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }

        @Override
        public Cookie[] getCookies() {
            return cookies.toArray(new Cookie[0]);
        }

        @Override
        public void addCookie(Cookie cookie) {
            cookies.add(cookie);
        }

        @Override
        public void addCookie(String name, String value) {
            cookies.add(new Cookie(name, value));
        }
    }
}