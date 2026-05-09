package com.weacsoft.jaravel.http.request;

import com.weacsoft.jaravel.contract.http.Request;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import lombok.Getter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

public class HttpRequest implements Request {

    private final LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    private final LinkedMultiValueMap<String, String> input = new LinkedMultiValueMap<>();
    private final LinkedMultiValueMap<String, MultipartFile> file = new LinkedMultiValueMap<>();
    private final LinkedMultiValueMap<String, String> header = new LinkedMultiValueMap<>();
    private final List<jakarta.servlet.http.Cookie> servletCookies = new ArrayList<>();
    private final LinkedMultiValueMap<String, Object> session = new LinkedMultiValueMap<>();
    @Getter
    private HttpServletRequest request;

    public HttpRequest() {
    }

    public HttpRequest addInput(String key, Object value) {
        input.add(key, value.toString());
        return this;
    }

    public HttpRequest addQuery(String key, String value) {
        query.add(key, value);
        return this;
    }

    public HttpRequest addHeader(String key, String value) {
        header.add(key, value);
        return this;
    }

    public HttpRequest addFile(String key, MultipartFile value) {
        file.add(key, value);
        return this;
    }

    public HttpRequest addCookie(String key, String newValue) {
        return addCookie(new jakarta.servlet.http.Cookie(key, newValue));
    }

    public HttpRequest addCookie(jakarta.servlet.http.Cookie cookie) {
        servletCookies.add(cookie);
        return this;
    }

    public HttpRequest addSession(String key, Object value) {
        session.add(key, value);
        request.getSession(true).setAttribute(key, value);
        return this;
    }

    public HttpRequest replaceInput(String key, Object newValue) {
        input.set(key, newValue.toString());
        return this;
    }

    public HttpRequest replaceQuery(String key, String newValue) {
        query.set(key, newValue);
        return this;
    }

    public HttpRequest replaceHeader(String key, String newValue) {
        header.set(key, newValue);
        return this;
    }

    public HttpRequest replaceFile(String key, MultipartFile newValue) {
        file.set(key, newValue);
        return this;
    }

    public HttpRequest replaceCookie(String key, String newValue) {
        for (int i = 0; i < servletCookies.size(); i++) {
            if (servletCookies.get(i).getName().equals(key)) {
                servletCookies.get(i).setValue(newValue);
                return this;
            }
        }
        return addCookie(key, newValue);
    }

    public HttpRequest replaceCookie(jakarta.servlet.http.Cookie cookie) {
        replaceCookie(cookie.getName(), cookie.getValue());
        return this;
    }

    public HttpRequest replaceSession(String key, Object newValue) {
        session.set(key, newValue);
        request.getSession(true).setAttribute(key, newValue);
        return this;
    }

    public HttpRequest removeInput(String key) {
        input.remove(key);
        return this;
    }

    public HttpRequest removeQuery(String key) {
        query.remove(key);
        return this;
    }

    public HttpRequest removeHeader(String key) {
        header.remove(key);
        return this;
    }

    public HttpRequest removeFile(String key) {
        file.remove(key);
        return this;
    }

    public HttpRequest removeCookie(String key) {
        servletCookies.removeIf(cookie -> cookie.getName().equals(key));
        return this;
    }

    public HttpRequest removeSession(String key) {
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

    @Override
    public String get(String key) {
        return get(key, "");
    }

    @Override
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

    @Override
    public Set<String> queryNames() {
        return query.keySet();
    }

    public MultiValueMap<String, String> query() {
        return query.deepCopy();
    }

    @Override
    public String query(String key) {
        return query(key, "");
    }

    @Override
    public String query(String key, String defaultValue) {
        String result = defaultValue;
        if (query.containsKey(key)) {
            result = query.getFirst(key);
        }
        return result;
    }

    @Override
    public List<String> queries(String key) {
        return query.get(key);
    }

    @Override
    public Set<String> inputNames() {
        return input.keySet();
    }

    public MultiValueMap<String, String> input() {
        return input.deepCopy();
    }

    @Override
    public String input(String key) {
        return input(key, "");
    }

    @Override
    public String input(String key, String defaultValue) {
        String result = defaultValue;
        if (input.containsKey(key)) {
            result = input.getFirst(key);
        }
        return result;
    }

    @Override
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

    @Override
    public Set<String> headerNames() {
        return header.keySet();
    }

    public MultiValueMap<String, String> header() {
        return header.deepCopy();
    }

    @Override
    public String header(String key) {
        return header(key, "");
    }

    @Override
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

    @Override
    public Set<String> cookieNames() {
        Set<String> names = new HashSet<>();
        for (jakarta.servlet.http.Cookie cookie : servletCookies) {
            names.add(cookie.getName());
        }
        return names;
    }

    public MultiValueMap<String, String> cookie() {
        MultiValueMap<String, String> cookieMap = new LinkedMultiValueMap<>();
        for (jakarta.servlet.http.Cookie cookie : servletCookies) {
            cookieMap.add(cookie.getName(), cookie.getValue());
        }
        return cookieMap;
    }

    @Override
    public String cookie(String key) {
        return cookie(key, "");
    }

    @Override
    public String cookie(String key, String defaultValue) {
        for (jakarta.servlet.http.Cookie cookie : servletCookies) {
            if (cookie.getName().equals(key)) {
                return cookie.getValue();
            }
        }
        return defaultValue;
    }

    public List<String> cookies(String key) {
        List<String> values = new ArrayList<>();
        for (jakarta.servlet.http.Cookie cookie : servletCookies) {
            if (cookie.getName().equals(key)) {
                values.add(cookie.getValue());
            }
        }
        return values;
    }

    @Override
    public Set<String> sessionNames() {
        return session.keySet();
    }

    public MultiValueMap<String, Object> session() {
        return session.deepCopy();
    }

    @Override
    public Object session(String key) {
        return session(key, (Object) null);
    }

    @Override
    public Object session(String key, Object defaultValue) {
        Object value = defaultValue;
        if (session.containsKey(key)) {
            value = session.getFirst(key);
        }
        return value;
    }

    public String session(String key, String defaultValue) {
        return session(key, (Object) defaultValue).toString();
    }

    public List<Object> sessions(String key) {
        return session.get(key);
    }

    @Override
    public boolean has(String key) {
        return query.containsKey(key) || input.containsKey(key);
    }

    @Override
    public boolean hasFile(String key) {
        return file.containsKey(key);
    }

    @Override
    public boolean hasHeader(String key) {
        return header.containsKey(key);
    }

    @Override
    public boolean hasCookie(String key) {
        for (jakarta.servlet.http.Cookie cookie : servletCookies) {
            if (cookie.getName().equals(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
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
        jakarta.servlet.http.Cookie[] requestCookies = request.getCookies();
        if (requestCookies != null) {
            for (jakarta.servlet.http.Cookie cookie : requestCookies) {
                this.servletCookies.add(cookie);
            }
        }
        HttpSession httpSession = request.getSession(false);
        if (httpSession != null) {
            Enumeration<String> sessionNames = httpSession.getAttributeNames();
            while (sessionNames.hasMoreElements()) {
                String sessionName = sessionNames.nextElement();
                this.addSession(sessionName, httpSession.getAttribute(sessionName));
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
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
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
