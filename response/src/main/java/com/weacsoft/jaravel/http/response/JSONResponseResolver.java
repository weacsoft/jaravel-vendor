package com.weacsoft.jaravel.http.response;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class JSONResponseResolver {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Map<String, Object> createErrorResponse(String message) {
        return createResponse(false, message, null);
    }

    public static Map<String, Object> createSuccessResponse() {
        return createSuccessResponse(null);
    }

    public static Map<String, Object> createSuccessResponse(Object[] data) {
        return createResponse(true, "ok", data);
    }

    public static Map<String, Object> createResponse(boolean success, String message, Object[] data) {
        Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("success", success);
        jsonObject.put("message", message);
        jsonObject.put("data", data);
        return jsonObject;
    }
}
