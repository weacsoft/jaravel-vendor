package com.weacsoft.jaravel.vendor.wechat.transport;

import java.util.Map;

/**
 * 微信请求体/响应 JSON 编解码器（SPI 抽象）。
 * <p>
 * 默认实现 {@link JacksonJsonEncoder} 委托 json 模块门面（{@code Json.stringify}/{@code Json.parseToMap}），
 * 与模块其余部分共享同一 JSON 编解码配置；测试可注入 mock 以断言序列化输出。
 *
 * @author weacsoft
 */
public interface RequestJsonEncoder {

    /**
     * 序列化请求体为 JSON 字符串。
     *
     * @param body 请求体对象（Map/List/POJO）
     * @return JSON 字符串
     */
    String encode(Object body);

    /**
     * 解析响应体为 Map。
     *
     * @param json JSON 字符串
     * @return Map（嵌套对象保持 Map/List 结构，与 Jackson 默认行为一致）
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> parseToMap(String json);
}
