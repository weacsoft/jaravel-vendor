package com.weacsoft.jaravel.vendor.http.controller.request;

import com.weacsoft.jaravel.vendor.json.Json;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class RequestFactory {

    private static final ThreadLocal<Request> currentRequest = new ThreadLocal<>();

    public static Request getCurrentRequest() {
        return currentRequest.get();
    }

    public static void setCurrentRequest(Request request) {
        currentRequest.set(request);
    }

    public static void clearCurrentRequest() {
        currentRequest.remove();
    }

    public static Request buildFromHttpServletRequest(HttpServletRequest baseRequest) {
        Request request = new Request();
        if (baseRequest != null) {
            request.setRequest(baseRequest);
            Map<String, List<String>> result = new LinkedHashMap<>();
            String pairs = baseRequest.getQueryString();
            if (pairs != null) {
                generateParam(result, pairs.split("&"));
            }
            result.forEach((name, values) -> {
                values.forEach(v -> {
                    request.addQuery(name, v);
                });
            });
            String contentType = null;
            contentType = baseRequest.getContentType();
            if (contentType != null) {
                if (contentType.contains("multipart/form-data")) {
                    handleMultipartRequest(baseRequest, request);
                } else if (contentType.contains("application/json")) {
                    handleJsonRequest(baseRequest, request);
                } else if (contentType.contains("application/x-www-form-urlencoded")) {
                    handleFormUrlEncodedRequest(request);
                }
            }
            // contentType 为 null 时不尝试解析 body（如 GET 请求）
        }
        return request;
    }

    private static void handleMultipartRequest(HttpServletRequest baseRequest, Request request) {
        try {
            MultipartHttpServletRequest multipartRequest;
            if (baseRequest instanceof StandardMultipartHttpServletRequest) {
                multipartRequest = (StandardMultipartHttpServletRequest) baseRequest;
            } else {
                multipartRequest = null;
            }
            if (multipartRequest != null) {
                Method method = StandardMultipartHttpServletRequest.class.getDeclaredMethod("initializeMultipart");
                method.setAccessible(true);
                method.invoke(multipartRequest);
                Field field = StandardMultipartHttpServletRequest.class.getDeclaredField("multipartParameterNames");
                field.setAccessible(true);
                Set<String> parameterName = (Set<String>) field.get(multipartRequest);
                parameterName.forEach(name -> {
                    request.addInput(name, multipartRequest.getParameter(name));
                });

                Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
                for (Map.Entry<String, MultipartFile> entry : fileMap.entrySet()) {
                    request.addFile(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void handleJsonRequest(HttpServletRequest baseRequest, Request request) {
        try {
            String body = baseRequest.getReader()
                    .lines()
                    .collect(Collectors.joining());
            if (body == null || body.trim().isEmpty()) {
                return;
            }
            Map<String, Object> obj = Json.parseToMap(body);
            obj.forEach((key, value) -> {
                request.addInput(key, value);
            });
        } catch (Exception e) {
            throw new RuntimeException("JSON 解析失败", e);
        }
    }

    public static Request buildFromServerRequest(ServerRequest baseRequest) {
        MediaType contentType = baseRequest.headers().contentType().orElse(null);
        Request request = new Request();
        request.setRequest(baseRequest.servletRequest());
        setCurrentRequest(request);
        UriComponentsBuilder.fromUri(baseRequest.uri())
                .build()
                .getQueryParams().forEach((name, values) -> {
                    values.forEach(v -> {
                        request.addQuery(name, v);
                    });
                });
        if (contentType != null) {
            if (contentType.includes(MediaType.MULTIPART_FORM_DATA)) {
                handleMultipartRequest(baseRequest, request);
            } else if (contentType.includes(MediaType.APPLICATION_JSON)) {
                handleJsonRequest(baseRequest, request);
            } else if (contentType.includes(MediaType.APPLICATION_FORM_URLENCODED)) {
                handleFormUrlEncodedRequest(request);
            }
        }
        // contentType 为 null 时不尝试解析 body（如 GET 请求），仅保留已解析的 query 参数
        return request;
    }

    private static void handleMultipartRequest(ServerRequest base, Request request) {
        try {
            base.multipartData().forEach((name, parts) -> parts.forEach(part -> {
                // 通过 submittedFileName 判断是否为文件：文件部分有文件名，文本字段没有
                if (part.getSubmittedFileName() != null) {
                    MultipartFile multipartFile = new Request.FluxMultipartFile(part.getName(), part);
                    request.addFile(part.getName(), multipartFile);
                } else {
                    // 文本字段：读取内容并转为字符串
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    try (InputStream inputStream = part.getInputStream();
                         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }

                        byte[] allBytes = outputStream.toByteArray();
                        request.addInput(part.getName(), new String(allBytes, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new RuntimeException("读取输入流失败", e);
                    }
                }
            }));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleFormUrlEncodedRequest(Request request) {
        HttpServletRequest httpServletRequest = request.getRequest();
        if (httpServletRequest != null) {
            // 优先使用 Servlet API 的 getParameterMap，Spring 已解析 form 参数
            // 这比手动读取 body 更可靠，因为 Spring 可能已消费 body
            Map<String, String[]> paramMap = httpServletRequest.getParameterMap();
            if (paramMap != null && !paramMap.isEmpty()) {
                paramMap.forEach((name, values) -> {
                    for (String value : values) {
                        request.addInput(name, value);
                    }
                });
                return;
            }
            // 回退：手动读取 body（适用于未被 Spring 消费的情况）
            try {
                generateUrlencode(request);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void generateUrlencode(Request request) throws IOException {
        HttpServletRequest httpServletRequest = request.getRequest();
        if (httpServletRequest != null) {
            BufferedReader reader = httpServletRequest.getReader();
            if (reader != null) {
                String body = reader
                        .lines()
                        .collect(Collectors.joining());
                if (body.isEmpty()) {
                    return;
                }
                Map<String, List<String>> result = new LinkedHashMap<>();
                String[] pairs = body.split("&");
                generateParam(result, pairs);
                result.forEach((name, values) -> {
                    values.forEach(v -> request.addInput(name, v));
                });
            }
        }
    }

    private static void generateParam(Map<String, List<String>> result, String[] pairs) {
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            String key = decode(keyValue[0]);
            String value = keyValue.length > 1 ? decode(keyValue[1]) : "";
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
    }

    private static String decode(String encoded) {
        try {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return encoded;
        }
    }

    private static void handleJsonRequest(ServerRequest base, Request request) {
        String body = null;
        try {
            body = base.body(String.class);
            if (body == null || body.trim().isEmpty()) {
                return;
            }
            Map<String, Object> obj = Json.parseToMap(body);
            obj.forEach((key, value) -> {
                request.addInput(key, value);
            });
        } catch (ServletException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}