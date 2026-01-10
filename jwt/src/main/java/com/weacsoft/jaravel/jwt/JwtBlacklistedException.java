package com.weacsoft.jaravel.jwt;

public class JwtBlacklistedException extends JwtException {

    public JwtBlacklistedException(String message) {
        super(message);
    }

    public JwtBlacklistedException(String message, Throwable cause) {
        super(message, cause);
    }
}
