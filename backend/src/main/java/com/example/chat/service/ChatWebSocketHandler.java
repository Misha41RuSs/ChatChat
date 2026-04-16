package com.example.chat.service;

import com.example.chat.dto.MessageDto;
import com.example.chat.dto.NewMessageDto;
import com.example.chat.entity.Message;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final MessageService messageService;
    private final ChatService chatService;
    private final UserService userService;
    private final Map<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(MessageService messageService, ChatService chatService, UserService userService) {
        this.messageService = messageService;
        this.chatService = chatService;
        this.userService = userService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String username = (String) session.getAttributes().get("username");
        if (username != null) {
            sessionsByUser.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String username = (String) session.getAttributes().get("username");
        if (username == null) {
            return;
        }
        NewMessageDto newMessage;
        try {
            newMessage = NewMessageDto.fromJson(message.getPayload());
        } catch (Exception ignored) {
            return;
        }
        if (newMessage.getRoomId() == null) {
            return;
        }
        if (!chatService.isParticipant(newMessage.getRoomId(), username)) {
            return;
        }
        var user = userService.findByUsername(username).orElseThrow();
        String senderIp = session.getRemoteAddress() != null ? session.getRemoteAddress().toString() : username;
        Message saved = messageService.saveMessage(newMessage, user, senderIp, Instant.now().toEpochMilli());
        MessageDto dto = MessageDto.fromEntity(saved);
        final String json;
        try {
            json = dto.toJson();
        } catch (Exception e) {
            return;
        }
        chatService.getRoomParticipants(saved.getChatRoom().getId()).forEach(participant -> {
            Set<WebSocketSession> sockets = sessionsByUser.get(participant);
            if (sockets == null) {
                return;
            }
            for (WebSocketSession target : sockets) {
                if (target != null && target.isOpen()) {
                    try {
                        target.sendMessage(new TextMessage(json));
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        String name = (String) session.getAttributes().get("username");
        if (name == null) {
            return;
        }
        Set<WebSocketSession> set = sessionsByUser.get(name);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessionsByUser.remove(name);
            }
        }
    }

    public void broadcastSystemMessage(Long roomId, String content) {
        try {
            Message systemMessage = messageService.createSystemMessage(roomId, content);
            MessageDto dto = MessageDto.fromEntity(systemMessage);
            String json = dto.toJson();

            chatService.getRoomParticipants(roomId).forEach(participant -> {
                Set<WebSocketSession> sockets = sessionsByUser.get(participant);
                if (sockets == null) {
                    return;
                }
                for (WebSocketSession target : sockets) {
                    if (target != null && target.isOpen()) {
                        try {
                            target.sendMessage(new TextMessage(json));
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }
}
