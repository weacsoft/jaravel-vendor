package com.weacsoft.jaravel.http.request;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import lombok.Getter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

public class Request {

    private final LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    private final LinkedMultiValueMap<String, String> input = new LinkedMultiValueMap<>();
    private final LinkedMultiValueMap<String, MultipartFile> file = new LinkedMultiValueMap<>();
    private final LinkedMultiValueMap<String, String> header = new LinkedMultiValueMap<>();
    private final List<Cookie> cookies = new ArrayList<>();
    private final LinkedMultiValueMap<String, Object> session = new LinkedMultiValueMap<>();
    @Getter
    private HttpServletRequest request;


    public Request() {
    }

    public Request addInput(String key, Object value) {
        input.add(key, value.toString());
        return this;
    }

    public Request addQuery(String key, String value) {
        query.add(key, value);
        return this;
    }

    public Request addHeader(String key, String value) {
        header.add(key, value);
        return this;
    }

    public Request addFile(String key, MultipartFile value) {
        file.add(key, value);
        return this;
    }

    public Request addCookie(String key, String newValue) {
        return addCookie(new Cookie(key, newValue));
    }

    public Request addCookie(Cookie cookie) {
        cookies.add(cookie);
        return this;
    }

    public Request addSession(String key, Object value) {
        session.add(key, value);
        request.getSession(true).setAttribute(key, value);
        return this;
    }

    public Request replaceInput(String key, Object newValue) {
        input.set(key, newValue.toString());
        return this;
    }

    public Request replaceQuery(String key, String newValue) {
        query.set(key, newValue);
        return this;
    }

    public Request replaceHeader(String key, String newValue) {
        header.set(key, newValue);
        return this;
    }

    public Request replaceFile(String key, MultipartFile newValue) {
        file.set(key, newValue);
        return this;
    }

    public Request replaceCookie(String key, String newValue) {
        for (int i = 0; i < cookies.size(); i++) {
            if (cookies.get(i).getName().equals(key)) {
                cookies.get(i).setValue(newValue);
                return this;
            }
        }
        return addCookie(key, newValue);
    }

    public Request replaceCookie(Cookie cookie) {
        replaceCookie(cookie.getName(), cookie.getValue());
        return this;
    }

    public Request replaceSession(String key, Object newValue) {
        session.set(key, newValue);
        request.getSession(true).setAttribute(key, newValue);
        return this;
    }

    public Request removeInput(String key) {
        input.remove(key);
        return this;
    }

    public Request removeQuery(String key) {
        query.remove(key);
        return this;
    }

    public Request removeHeader(String key) {
        header.remove(key);
        return this;
    }

    public Request removeFile(String key) {
        file.remove(key);
        return this;
    }

    public Request removeCookie(String key) {
        cookies.removeIf(cookie -> cookie.getName().equals(key));
        return this;
    }

    public Request removeSession(String key) {
        session.remove(key);
        request.getSession(true).removeAttribute(key);
        return this;
    }

    public Set<String> getKeys() {
        Set<String> names = new HashSet<>();
        names.addAll(inputNames());
        names.addAll(queryNames());
        return names;
    }

    public String get(String key) {
        return get(key, "");
    }

    public String get(String key, String defaultValue) {
        String result = defaultValue;
        if (input.containsKey(key)) {
            result = input.getFirst(key);
        } else if (query.containsKey(key)) {
            result = query.getFirst(key);
        }
        return result;
    }

    public List<String> gets(String key) {
        List<String> result = new ArrayList<>(1);
        if (input.containsKey(key)) {
            result = input.get(key);
        } else if (query.containsKey(key)) {
            result = query.get(key);
        }
        return result;
    }

    public MultiValueMap<String, String> all() {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.putAll(query());
        map.putAll(input());
        return map;
    }

    public Set<String> queryNames() {
        return query.keySet();
    }

    public MultiValueMap<String, String> query() {
        return query.deepCopy();
    }

    public String query(String key) {
        return query(key, "");
    }

    public String query(String key, String defaultValue) {
        String result = defaultValue;
        if (query.containsKey(key)) {
            result = query.getFirst(key);
        }
        return result;
    }

    public List<String> queries(String key) {
        return query.get(key);
    }

    public Set<String> inputNames() {
        return input.keySet();
    }

    public MultiValueMap<String, String> input() {
        return input.deepCopy();
    }

    public String input(String key) {
        return input(key, "");
    }

    public String input(String key, String defaultValue) {
        String result = defaultValue;
        if (input.containsKey(key)) {
            result = input.getFirst(key);
        }
        return result;
    }

    public List<String> inputs(String key) {
        return input.get(key);
    }

    public Set<String> fileNames() {
        return file.keySet();
    }

    public MultiValueMap<String, MultipartFile> file() {
        return file.deepCopy();
    }

    public MultipartFile file(String key) {
        return file.getFirst(key);
    }

    public List<MultipartFile> files(String key) {
        return file.get(key);
    }

    public Set<String> headerNames() {
        return header.keySet();
    }

    public MultiValueMap<String, String> header() {
        return header.deepCopy();
    }

    public String header(String key) {
        return header(key, "");
    }

    public String header(String key, String defaultValue) {
        String result = defaultValue;
        if (header.containsKey(key)) {
            result = header.getFirst(key);
        }
        return result;
    }

    public List<String> headers(String key) {
        return header.get(key);
    }

    public Set<String> cookieNames() {
        Set<String> names = new HashSet<>();
        for (Cookie cookie : cookies) {
            names.add(cookie.getName());
        }
        return names;
    }

    public MultiValueMap<String, String> cookie() {
        MultiValueMap<String, String> cookieMap = new LinkedMultiValueMap<>();
        for (Cookie cookie : cookies) {
            cookieMap.add(cookie.getName(), cookie.getValue());
        }
        return cookieMap;
    }

    public String cookie(String key) {
        return cookie(key, "");
    }

    public String cookie(String key, String defaultValue) {
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(key)) {
                return cookie.getValue();
            }
        }
        return defaultValue;
    }

    public List<String> cookies(String key) {
        List<String> values = new ArrayList<>();
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(key)) {
                values.add(cookie.getValue());
            }
        }
        return values;
    }

    public Set<String> sessionNames() {
        return session.keySet();
    }

    public MultiValueMap<String, Object> session() {
        return session.deepCopy();
    }

    public String session(String key) {
        return session(key, "");
    }

    public Object objectSession(String key) {
        return session(key, (Object) null);
    }

    public String session(String key, String defaultValue) {
        return session(key, (Object) defaultValue).toString();
    }

    public Object session(String key, Object defaultValue) {
        Object value = defaultValue;
        if (session.containsKey(key)) {
            value = session.getFirst(key);
        }
        return value;
    }


    public List<Object> sessions(String key) {
        return session.get(key);
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
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(key)) {
                return true;
            }
        }
        return false;
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
        Cookie[] requestCookies = request.getCookies();
        if (requestCookies != null) {
            for (Cookie cookie : requestCookies) {
                this.cookies.add(cookie);
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
        private byte[] cachedBytes;

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
                return cachedBytes = outputStream.toByteArray();
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
