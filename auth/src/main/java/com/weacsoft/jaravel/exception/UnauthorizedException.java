package com.weacsoft.jaravel.exception;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UnauthorizedException extends HttpException {
    public UnauthorizedException(String challenge) {
        this(challenge, "");
    }

    public UnauthorizedException(String challenge, String message) {
        this(challenge, message, new ConcurrentHashMap<>());
    }

    public UnauthorizedException(String challenge, String message, Map<String, String> headers) {
        super(401, message, headers);
        headers.put("WWW-Authenticate", challenge);
    }
}
