package com.weacsoft.jaravel.http.response;

import cn.hutool.json.JSONObject;

public class JSONResponseResolver {
    public static JSONObject createErrorResponse(String message) {
        return createResponse(false, message, null);
    }

    public static JSONObject createSuccessResponse() {
        return createSuccessResponse(null);
    }

    public static JSONObject createSuccessResponse(Object[] data) {
        return createResponse(true, "ok", data);
    }

    public static JSONObject createResponse(boolean success, String message, Object[] data) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("success", success);
        jsonObject.set("message", message);
        jsonObject.set("data", data);
        return jsonObject;
    }
}
