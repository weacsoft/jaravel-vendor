package com.weacsoft.jaravel.vendor.wechat.transport;

import com.weacsoft.jaravel.vendor.json.Json;

import java.util.Map;

/**
 * 基于 json 模块门面的默认 JSON 编码器。
 *
 * @author weacsoft
 */
public class JacksonJsonEncoder implements RequestJsonEncoder {

    @Override
    public String encode(Object body) {
        return Json.stringify(body);
    }

    @Override
    public Map<String, Object> parseToMap(String json) {
        return Json.parseToMap(json);
    }
}
