package com.weacsoft.jaravel.exception;

import java.util.Map;

public class HttpException extends RuntimeException {
    private final int statusCode;
    private Map<String, String> headers;

    public HttpException(int statusCode, String message) {
        this(statusCode, message, null);
    }

    public HttpException(int statusCode, String message, Map<String, String> headers) {
        super(message);
        this.statusCode = statusCode;
        this.headers = headers;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
}
