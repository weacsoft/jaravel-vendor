package com.weacsoft.jaravel.http.request;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
            if (contentType == null) {
                handleFormUrlEncodedRequest(request);
            } else if (contentType.contains("multipart/form-data")) {
                handleMultipartRequest(baseRequest, request);
            } else if (contentType.contains("application/json")) {
                handleJsonRequest(baseRequest, request);
            } else if (contentType.contains("application/x-www-form-urlencoded")) {
                handleFormUrlEncodedRequest(request);
            }
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
            JSONObject obj = JSONUtil.parseObj(body);
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
        } else {
            handleFormUrlEncodedRequest(request);
        }
        return request;
    }

    private static void handleMultipartRequest(ServerRequest base, Request request) {
        try {
            base.multipartData().forEach((name, parts) -> parts.forEach(part -> {
                if (part.getContentType() == null ||
                        !part.getContentType().startsWith("application/") &&
                                !part.getContentType().startsWith("image/") &&
                                !part.getContentType().startsWith("file/")) {
                    // try-with-resources 自动关闭流，避免资源泄漏
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    try (InputStream inputStream = part.getInputStream();
                         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                        // 循环读取字节到缓冲区，直到流末尾（-1）
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }

                        // 转为字节数组（等价于 readAllBytes()）
                        byte[] allBytes = outputStream.toByteArray();
                        request.addInput(part.getName(), new String(allBytes));
                        // 后续业务逻辑...
                    } catch (IOException e) {
                        throw new RuntimeException("读取输入流失败", e);
                    }
                } else {
                    MultipartFile multipartFile = new Request.FluxMultipartFile(part.getName(), part);
                    request.addFile(part.getName(), multipartFile);
                }
            }));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleFormUrlEncodedRequest(Request request) {
        try {
            generateUrlencode(request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void generateUrlencode(Request request) throws IOException {
        BufferedReader reader = request.getRequest().getReader();
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
            JSONObject obj = JSONUtil.parseObj(body);
            obj.forEach((key, value) -> {
                request.addInput(key, value);
            });
        } catch (ServletException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
