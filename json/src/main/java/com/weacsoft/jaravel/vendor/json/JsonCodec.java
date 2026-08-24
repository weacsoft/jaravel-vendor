package com.weacsoft.jaravel.vendor.json;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * JSON 编解码 SPI 接口。
 * <p>
 * 统一抽象 Jackson 2 ({@code com.fasterxml.jackson.databind.ObjectMapper})
 * 与 Jackson 3 ({@code tools.jackson.databind.ObjectMapper}) 的常用操作，
 * 使上层代码无需关心运行时 Jackson 版本。
 * <ul>
 *   <li>SB3 运行时：{@link Jackson2JsonCodec} 生效</li>
 *   <li>SB4 运行时：{@link Jackson3JsonCodec} 生效</li>
 * </ul>
 */
public interface JsonCodec {

    /**
     * 将对象序列化为 JSON 字符串。
     */
    String toJson(Object value);

    /**
     * 将对象序列化为带缩进的 JSON 字符串。
     */
    String toPrettyJson(Object value);

    /**
     * 将 JSON 字符串反序列化为指定类型。
     */
    <T> T fromJson(String json, Class<T> type);

    /**
     * 将 JSON 字符串反序列化为指定泛型类型（配合 {@code TypeReference.getType()} 使用）。
     */
    <T> T fromJson(String json, Type type);

    /**
     * 将 JSON 字符串解析为 {@code Map<String, Object>}。
     * 替代 Jackson 的 {@code readTree} 场景。
     */
    Map<String, Object> fromJsonToMap(String json);

    /**
     * 将一个对象转换为指定类型（浅拷贝，基于序列化-反序列化）。
     */
    <T> T convertValue(Object from, Class<T> toType);

    /**
     * 将对象序列化写入文件。
     */
    void writeToFile(File file, Object value);

    /**
     * 将对象序列化为带缩进的 JSON 写入文件。
     */
    void writeToPrettyFile(File file, Object value);

    /**
     * 从文件读取 JSON 并反序列化为指定类型。
     */
    <T> T readFromFile(File file, Class<T> type);
}
