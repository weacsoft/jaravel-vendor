package com.weacsoft.jaravel.jwt;

public class TokenResult {

    private final JwtPayload payload;

    private final boolean valid;

    private final boolean expired;

    private final String newToken;

    private final String refreshToken;

    private TokenResult(Builder builder) {
        this.payload = builder.payload;
        this.valid = builder.valid;
        this.expired = builder.expired;
        this.newToken = builder.newToken;
        this.refreshToken = builder.refreshToken;
    }

    public JwtPayload getPayload() {
        return payload;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isExpired() {
        return expired;
    }

    public String getNewToken() {
        return newToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public boolean hasNewToken() {
        return newToken != null;
    }

    public boolean hasRefreshToken() {
        return refreshToken != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(JwtPayload payload) {
        return new Builder(payload);
    }

    public static class Builder {

        private JwtPayload payload;

        private boolean valid;

        private boolean expired;

        private String newToken;

        private String refreshToken;

        public Builder() {
        }

        public Builder(JwtPayload payload) {
            this.payload = payload;
        }

        public Builder payload(JwtPayload payload) {
            this.payload = payload;
            return this;
        }

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder expired(boolean expired) {
            this.expired = expired;
            return this;
        }

        public Builder newToken(String newToken) {
            this.newToken = newToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public TokenResult build() {
            return new TokenResult(this);
        }
    }
}
