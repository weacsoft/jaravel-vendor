package com.weacsoft.jaravel.error;

public class HttpError extends RuntimeException {
    public HttpError(String message) {
        super(message);
    }

    public HttpError(String message, int code) {
        super(message);
    }
}
