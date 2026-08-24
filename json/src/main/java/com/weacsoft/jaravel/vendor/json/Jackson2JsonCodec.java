package com.weacsoft.jaravel.vendor.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Jackson 2 ({@code com.fasterxml.jackson.databind}) 实现。
 * <p>
 * 在 SB3 运行时（Jackson 2 在 classpath）时由 {@link JsonCodecHolder} 自动选择。
 */
public class Jackson2JsonCodec implements JsonCodec {

    private final ObjectMapper mapper;

    public Jackson2JsonCodec() {
        this(new ObjectMapper());
    }

    /**
     * 用已有的 ObjectMapper 构造（例如 Spring 容器中配置好的 Bean）。
     */
    public Jackson2JsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
        this.mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
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
        } catch (IOException e) {
            throw new JsonCodecException("Failed to write JSON to file", e);
        }
    }

    @Override
    public void writeToPrettyFile(File file, Object value) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, value);
        } catch (IOException e) {
            throw new JsonCodecException("Failed to write pretty JSON to file", e);
        }
    }

    @Override
    public <T> T readFromFile(File file, Class<T> type) {
        try {
            return mapper.readValue(file, type);
        } catch (IOException e) {
            throw new JsonCodecException("Failed to read JSON from file", e);
        }
    }
}
