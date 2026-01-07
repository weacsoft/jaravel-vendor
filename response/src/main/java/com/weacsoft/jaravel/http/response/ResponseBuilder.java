package com.weacsoft.jaravel.http.response;

import cn.hutool.json.JSONUtil;
import com.weacsoft.jaravel.jblade.BladeEngine;
import jakarta.servlet.http.Cookie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResponseBuilder {
    private static BladeEngine bladeEngine;
    private static String templateDir = "templates";

    public static void setTemplateDir(String dir) {
        templateDir = dir;
        bladeEngine = null;
    }

    private static BladeEngine getBladeEngine() {
        if (bladeEngine == null) {
            bladeEngine = new BladeEngine(templateDir);
        }
        return bladeEngine;
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
                    return getBladeEngine().render(templateName, data);
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
                return JSONUtil.toJsonStr(data);
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
}
