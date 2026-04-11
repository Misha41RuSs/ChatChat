package com.example.chat.config;

import com.example.chat.service.ChatWebSocketHandler;
import com.example.chat.service.JwtHandshakeInterceptor;
import com.example.chat.service.UserService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final UserService userService;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler, UserService userService) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.userService = userService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .setAllowedOrigins("*")
                .addInterceptors(new JwtHandshakeInterceptor(userService));
    }
}
