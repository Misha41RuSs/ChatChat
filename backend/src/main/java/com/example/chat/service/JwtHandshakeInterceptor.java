package com.example.chat.service;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final UserService userService;

    public JwtHandshakeInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        if (query == null) {
            return false;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq);
            if (!"token".equals(key)) {
                continue;
            }
            String raw = pair.substring(eq + 1);
            String token = URLDecoder.decode(raw, StandardCharsets.UTF_8);
            userService.findByToken(token).ifPresent(user -> attributes.put("username", user.getUsername()));
            break;
        }
        return attributes.containsKey("username");
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
