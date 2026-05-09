package com.weacsoft.jaravel.http.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weacsoft.jaravel.contract.http.Cookie;
import com.weacsoft.jaravel.contract.http.Response;
import com.weacsoft.jaravel.contract.http.SimpleCookie;
import com.weacsoft.jaravel.jblade.BladeEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResponseBuilder {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static BladeEngine bladeEngine;

    private static BladeEngine getBladeEngine() {
        if (bladeEngine == null) {
            throw new RuntimeException("bladeEngine is null");
        }
        return bladeEngine;
    }

    public static void setBladeEngine(BladeEngine engine) {
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
}
