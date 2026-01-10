package com.weacsoft.jaravel.jwt;

public class JwtInvalidException extends JwtException {

    public JwtInvalidException(String message) {
        super(message);
    }

    public JwtInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
