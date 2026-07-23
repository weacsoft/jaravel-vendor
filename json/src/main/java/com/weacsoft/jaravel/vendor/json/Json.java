package com.weacsoft.jaravel.vendor.json;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * JSON 静态工具门面。
 * <p>
 * 所有方法委托给 {@link JsonCodecHolder#codec()}，一行替换原 {@code new ObjectMapper()} 用法。
 * <pre>
 *   // 旧代码
 *   private static final ObjectMapper mapper = new ObjectMapper();
 *   String json = mapper.writeValueAsString(data);
 *
 *   // 新代码
 *   String json = Json.stringify(data);
 * </pre>
 */
public final class Json {

    private Json() {
    }

    public static String stringify(Object value) {
        return JsonCodecHolder.codec().toJson(value);
    }

    public static String pretty(Object value) {
        return JsonCodecHolder.codec().toPrettyJson(value);
    }

    public static <T> T parse(String json, Class<T> type) {
        return JsonCodecHolder.codec().fromJson(json, type);
    }

    public static <T> T parse(String json, Type type) {
        return JsonCodecHolder.codec().fromJson(json, type);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseToMap(String json) {
        return JsonCodecHolder.codec().fromJsonToMap(json);
    }

    public static <T> T convert(Object from, Class<T> toType) {
        return JsonCodecHolder.codec().convertValue(from, toType);
    }

    public static void writeToFile(File file, Object value) {
        JsonCodecHolder.codec().writeToFile(file, value);
    }

    public static void writeToPrettyFile(File file, Object value) {
        JsonCodecHolder.codec().writeToPrettyFile(file, value);
    }

    public static <T> T readFromFile(File file, Class<T> type) {
        return JsonCodecHolder.codec().readFromFile(file, type);
    }
}
