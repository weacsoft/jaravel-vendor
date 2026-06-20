package com.weacsoft.jaravel.vendor.http.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
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