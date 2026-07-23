package com.weacsoft.jaravel.vendor.json;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Jackson 3 ({@code tools.jackson.databind}) 实现。
 * <p>
 * 在 SB4 运行时（Jackson 3 在 classpath）时由 {@link JsonCodecHolder} 自动选择。
 * <p>
 * 注意：Jackson 3 将大部分 {@code IOException} 改为运行时异常，
 * 因此文件操作不再需要声明 checked exception。
 */
public class Jackson3JsonCodec implements JsonCodec {

    private final ObjectMapper mapper;

    public Jackson3JsonCodec() {
        this.mapper = JsonMapper.builder()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
    }

    @Override
    public String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to serialize to JSON", e);
        }
    }

    @Override
    public String toPrettyJson(Object value) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to serialize to pretty JSON", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to deserialize JSON", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try {
            return mapper.readValue(json, mapper.constructType(type));
        } catch (Exception e) {
            throw new JsonCodecException("Failed to deserialize JSON", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> fromJsonToMap(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to parse JSON to Map", e);
        }
    }

    @Override
    public <T> T convertValue(Object from, Class<T> toType) {
        return mapper.convertValue(from, toType);
    }

    @Override
    public void writeToFile(File file, Object value) {
        try {
            mapper.writeValue(file, value);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to write JSON to file", e);
        }
    }

    @Override
    public void writeToPrettyFile(File file, Object value) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, value);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to write pretty JSON to file", e);
        }
    }

    @Override
    public <T> T readFromFile(File file, Class<T> type) {
        try {
            return mapper.readValue(file, type);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to read JSON from file", e);
        }
    }
}
