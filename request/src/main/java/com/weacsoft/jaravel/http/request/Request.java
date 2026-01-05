package com.weacsoft.jaravel.http.request;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

public class Request {

    @Getter
    private HttpServletRequest request;
    private final Map<String, Object> query = new LinkedHashMap<>();
    private final Map<String, Object> input = new LinkedHashMap<>();
    private final Map<String, Object> file = new LinkedHashMap<>();
    private final Map<String, Object> header = new LinkedHashMap<>();
    private final Map<String, Object> cookie = new LinkedHashMap<>();
    private final Map<String, Object> session = new LinkedHashMap<>();


    public Request() {
    }

    public void addInput(String key, Object newValue) {
        Object value = input.getOrDefault(key, null);
        if (value == null) {
            value = newValue;
        } else if (value instanceof List) {
            ((List<Object>) value).add(newValue);
        } else {
            value = Arrays.asList(value, newValue);
        }
        input.put(key, value);
    }

    public void addQuery(String key, Object newValue) {
        Object value = query.getOrDefault(key, null);
        if (value == null) {
            value = newValue;
        } else if (value instanceof List) {
            ((List<Object>) value).add(newValue);
        } else {
            value = Arrays.asList(value, newValue);
        }
        query.put(key, value);
    }

    public void addHeader(String key, Object newValue) {
        Object value = header.getOrDefault(key, null);
        if (value == null) {
            value = newValue;
        } else if (value instanceof List) {
            ((List<Object>) value).add(newValue);
        } else {
            value = Arrays.asList(value, newValue);
        }
        header.put(key, value);
    }

    public void addFile(String key, MultipartFile newValue) {
        Object value = file.getOrDefault(key, null);
        if (value == null) {
            value = newValue;
        } else if (value instanceof List) {
            ((List<MultipartFile>) value).add(newValue);
        } else {
            value = Arrays.asList(value, newValue);
        }
        file.put(key, value);
    }

    public void addCookie(String key, Object newValue) {
        Object value = cookie.getOrDefault(key, null);
        if (value == null) {
            value = newValue;
        } else if (value instanceof List) {
            ((List<Object>) value).add(newValue);
        } else {
            value = Arrays.asList(value, newValue);
        }
        cookie.put(key, value);
    }

    public void addSession(String key, Object newValue) {
        Object value = session.getOrDefault(key, null);
        if (value == null) {
            value = newValue;
        } else if (value instanceof List) {
            ((List<Object>) value).add(newValue);
        } else {
            value = Arrays.asList(value, newValue);
        }
        session.put(key, value);
    }

    public void replaceInput(String key, Object newValue) {
        input.put(key, newValue);
    }

    public void replaceQuery(String key, Object newValue) {
        query.put(key, newValue);
    }

    public void replaceHeader(String key, Object newValue) {
        header.put(key, newValue);
    }

    public void replaceFile(String key, Object newValue) {
        file.put(key, newValue);
    }

    public void replaceCookie(String key, Object newValue) {
        cookie.put(key, newValue);
    }

    public void replaceSession(String key, Object newValue) {
        session.put(key, newValue);
    }

    public void removeInput(String key) {
        input.remove(key);
    }

    public void removeQuery(String key) {
        query.remove(key);
    }

    public void removeHeader(String key) {
        header.remove(key);
    }

    public void removeFile(String key) {
        file.remove(key);
    }

    public void removeCookie(String key) {
        cookie.remove(key);
    }

    public void removeSession(String key) {
        session.remove(key);
    }

    public Set<String> getNames() {
        Set<String> names = new HashSet<>();
        names.addAll(inputNames());
        names.addAll(queryNames());
        return names;
    }

    public Map<String, Object> get() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(query);
        result.putAll(input);
        return result;
    }

    public String get(String key) {
        return get(key, (String) null);
    }

    public String get(String key, String defaultValue) {
        String value = get(key, defaultValue.getClass());
        if (value == null) {
            if (input.containsKey(key)) {
                value = input.get(key).toString();
            }
            if (query.containsKey(key)) {
                value = query.get(key).toString();
            }
        }
        return value != null ? value : defaultValue;
    }

    public <T> T get(String key, T defaultValue) {
        T value = get(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    public <T> T get(String key, Class<T> clazz) {
        if (input.containsKey(key)) {
            Object value = input.get(key);
            if (value instanceof List) {
                return (T) ((List<Object>) value).get(0);
            } else if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        if (query.containsKey(key)) {
            Object value = query.get(key);
            if (value instanceof List) {
                return (T) ((List<Object>) value).get(0);
            } else if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    public List<Object> gets(String key) {
        Object value = input.get(key);
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        value = query.get(key);
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        return Collections.singletonList(value);
    }

    public Map<String, Object> all() {
        Map<String, Object> map = new HashMap<>();
        map.putAll(query());
        map.putAll(input());
        return map;
    }

    public Set<String> queryNames() {
        return query.keySet();
    }

    public Map<String, Object> query() {
        return new LinkedHashMap<>(query);
    }

    public String query(String key) {
        return query(key, "");
    }

    public String query(String key, String defaultValue) {
        String value = query(key, defaultValue.getClass());
        if (value == null) {
            if (query.containsKey(key)) {
                value = query.get(key).toString();
            }
        }
        return value != null ? value : defaultValue;
    }

    public <T> T query(String key, T defaultValue) {
        T value = query(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    public <T> T query(String key, Class<T> clazz) {
        if (query.containsKey(key)) {
            Object value = query.get(key);
            if (value instanceof List) {
                return (T) ((List<Object>) value).get(0);
            } else if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    public List<Object> queries(String key) {
        Object value = query.get(key);
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        return Collections.singletonList(value);
    }

    public Set<String> inputNames() {
        return input.keySet();
    }

    public Map<String, Object> input() {
        return new LinkedHashMap<>(input);
    }

    public String input(String key) {
        return input(key, "");
    }

    public String input(String key, String defaultValue) {
        String value = input(key, defaultValue.getClass());
        if (value == null) {
            if (input.containsKey(key)) {
                value = input.get(key).toString();
            }
        }
        return value != null ? value : defaultValue;
    }

    public <T> T input(String key, T defaultValue) {
        T value = input(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    public <T> T input(String key, Class<T> clazz) {
        if (input.containsKey(key)) {
            Object value = input.get(key);
            if (value instanceof List) {
                return (T) ((List<Object>) value).get(0);
            } else if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    public List<Object> inputs(String key) {
        Object value = input.get(key);
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        return Arrays.asList(value);
    }

    public Set<String> fileNames() {
        return file.keySet();
    }

    public Map<String, Object> file() {
        return new LinkedHashMap<>(file);
    }

    public MultipartFile file(String key) {
        Object value = files(key);
        if (value instanceof List) {
            return ((List<MultipartFile>) value).get(0);
        }
        return (MultipartFile) value;
    }

    public List<MultipartFile> files(String key) {
        Object value = file.get(key);
        if (value instanceof List) {
            return (List<MultipartFile>) value;
        }
        return Arrays.asList((MultipartFile) value);
    }

    public Set<String> headerNames() {
        return header.keySet();
    }

    public Map<String, Object> header() {
        return new LinkedHashMap<>(header);
    }

    public String header(String key) {
        return header(key, "");
    }

    public String header(String key, String defaultValue) {
        String value = header(key, defaultValue.getClass());
        if (value == null) {
            if (header.containsKey(key)) {
                value = header.get(key).toString();
            }
        }
        return value != null ? value : defaultValue;
    }

    public <T> T header(String key, T defaultValue) {
        T value = header(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    public <T> T header(String key, Class<T> clazz) {
        if (header.containsKey(key)) {
            Object value = header.get(key);
            if (value instanceof List) {
                return (T) ((List<Object>) value).get(0);
            } else if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    public List<Object> headers(String key) {
        Object value = header.get(key);
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        return Collections.singletonList(value);
    }

    public Set<String> cookieNames() {
        return cookie.keySet();
    }

    public Map<String, Object> cookie() {
        return new LinkedHashMap<>(cookie);
    }

    public String cookie(String key) {
        return cookie(key, "");
    }

    public String cookie(String key, String defaultValue) {
        String value = cookie(key, defaultValue.getClass());
        if (value == null) {
            if (cookie.containsKey(key)) {
                value = cookie.get(key).toString();
            }
        }
        return value != null ? value : defaultValue;
    }

    public <T> T cookie(String key, T defaultValue) {
        T value = cookie(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    public <T> T cookie(String key, Class<T> clazz) {
        if (cookie.containsKey(key)) {
            Object value = cookie.get(key);
            if (value instanceof List) {
                return (T) ((List<Object>) value).get(0);
            } else if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    public List<Object> cookies(String key) {
        Object value = cookie.get(key);
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        return Collections.singletonList(value);
    }

    public Set<String> sessionNames() {
        return session.keySet();
    }

    public Map<String, Object> session() {
        return new LinkedHashMap<>(session);
    }

    public String session(String key) {
        return session(key, "");
    }

    public String session(String key, String defaultValue) {
        String value = session(key, defaultValue.getClass());
        if (value == null) {
            if (session.containsKey(key)) {
                value = session.get(key).toString();
            }
        }
        return value != null ? value : defaultValue;
    }

    public <T> T session(String key, T defaultValue) {
        T value = session(key, (Class<T>) defaultValue.getClass());
        return value != null ? value : defaultValue;
    }

    public <T> T session(String key, Class<T> clazz) {
        if (session.containsKey(key)) {
            Object value = session.get(key);
            if (value instanceof List) {
                return (T) ((List<Object>) value).get(0);
            } else if (clazz.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    public List<Object> sessions(String key) {
        Object value = session.get(key);
        if (value instanceof List) {
            return ((List<Object>) value);
        }
        return Collections.singletonList(value);
    }

    public boolean has(String key) {
        return query.containsKey(key) || input.containsKey(key);
    }

    public boolean hasFile(String key) {
        return file.containsKey(key);
    }

    public boolean hasHeader(String key) {
        return header.containsKey(key);
    }

    public boolean hasCookie(String key) {
        return cookie.containsKey(key);
    }

    public boolean hasSession(String key) {
        return session.containsKey(key);
    }

    public void setRequest(HttpServletRequest request) {
        this.request = request;
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValue = request.getHeaders(headerName);
            while (headerValue.hasMoreElements()) {
                this.addHeader(headerName, headerValue.nextElement());
            }
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                this.addCookie(cookie.getName(), cookie.getValue());
            }
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            Enumeration<String> sessionNames = session.getAttributeNames();
            while (sessionNames.hasMoreElements()) {
                String sessionName = sessionNames.nextElement();
                this.addSession(sessionName, session.getAttribute(sessionName));
            }
        }
    }

    public static class FluxMultipartFile implements MultipartFile {
        private final Part filePart;
        private final String name;

        public FluxMultipartFile(String name, Part filePart) {
            this.name = name;
            this.filePart = filePart;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return filePart.getSubmittedFileName();
        }

        @Override
        public String getContentType() {
            return filePart.getContentType();
        }

        @Override
        public boolean isEmpty() {
            try {
                return getBytes().length > 0;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long getSize() {
            try {
                return this.getBytes().length;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private byte[] cachedBytes;

        @Override
        public byte[] getBytes() throws IOException {
            if (cachedBytes != null) {
                return cachedBytes;
            }
            try (InputStream inputStream = filePart.getInputStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                // 调用自定义的copy方法替代transferTo
                byte[] buffer = new byte[4096];
                int bytesRead;
                // 循环读取字节到缓冲区，直到读取完毕（返回-1）
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    // 将缓冲区中的字节写入输出流（注意只写实际读取到的字节数）
                    outputStream.write(buffer, 0, bytesRead);
                }
                // 刷新输出流，确保所有数据都被写入
                outputStream.flush();
                return cachedBytes=outputStream.toByteArray();
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(getBytes());
        }

        @Override
        public void transferTo(File dest) throws IllegalStateException {
        }
    }
}
