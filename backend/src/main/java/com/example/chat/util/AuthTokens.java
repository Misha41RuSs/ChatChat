package com.example.chat.util;

public final class AuthTokens {
    private AuthTokens() {
    }

    public static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7).trim();
    }
}
