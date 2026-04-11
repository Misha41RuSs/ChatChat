package com.example.chat.controller;

import com.example.chat.dto.ChatRoomDto;
import com.example.chat.dto.DirectMessageRequest;
import com.example.chat.dto.InvitationDto;
import com.example.chat.dto.MessageDto;
import com.example.chat.entity.ChatRoom;
import com.example.chat.entity.User;
import com.example.chat.service.ChatService;
import com.example.chat.service.ChatWebSocketHandler;
import com.example.chat.service.MessageService;
import com.example.chat.service.UserService;
import com.example.chat.util.AuthTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final ChatService chatService;
    private final MessageService messageService;
    private final UserService userService;
    private final ChatWebSocketHandler webSocketHandler;

    public ChatController(ChatService chatService, MessageService messageService, UserService userService, ChatWebSocketHandler webSocketHandler) {
        this.chatService = chatService;
        this.messageService = messageService;
        this.userService = userService;
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request,
                                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = AuthTokens.bearer(authorization);
        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("message", "Требуется авторизация"));
        }
        return userService.findByToken(token)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(
                        java.util.Map.of(
                                "username", user.getUsername(),
                                "ip", request.getRemoteAddr()
                        )
                ))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("message", "Недействительный токен")));
    }

    @GetMapping("/users")
    public List<String> searchUsers(
            @RequestParam(value = "q", required = false) String q,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        User me = requireUser(authorization);
        String prefix = q == null ? "" : q.trim();
        if (prefix.isEmpty()) {
            return userService.listOtherUsernames(me.getUsername(), 50);
        }
        return userService.searchUsernames(prefix, me.getUsername(), 50);
    }

    @GetMapping("/rooms")
    public List<ChatRoomDto> rooms(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        User user = requireUser(authorization);
        return chatService.listRoomsForUser(user.getUsername()).stream()
                .map(ChatRoomDto::fromEntity)
                .collect(Collectors.toList());
    }

    @PostMapping("/rooms")
    public ChatRoomDto createRoom(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                  @RequestBody ChatRoomDto request) {
        User user = requireUser(authorization);
        ChatRoom.RoomType type;
        try {
            type = ChatRoom.RoomType.valueOf(request.getType() == null ? "GROUP" : request.getType());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Некорректный тип комнаты");
        }
        ChatRoom room = chatService.createGroupRoom(
                user.getUsername(),
                request.getName(),
                type,
                request.getParticipants() == null ? List.of() : request.getParticipants());
        return ChatRoomDto.fromEntity(room);
    }

    @PostMapping("/rooms/dm")
    public ChatRoomDto openDirect(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                  @Valid @RequestBody DirectMessageRequest body) {
        User user = requireUser(authorization);
        ChatRoom room = chatService.openOrCreateDirect(user.getUsername(), body.getWithUsername());
        return ChatRoomDto.fromEntity(room);
    }

    @GetMapping("/rooms/{roomId}/messages")
    public List<MessageDto> messages(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("roomId") Long roomId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        User user = requireUser(authorization);
        Instant start = from != null ? from.toInstant(ZoneOffset.UTC) : null;
        Instant end = to != null ? to.toInstant(ZoneOffset.UTC) : null;
        return messageService.getMessages(roomId, user.getUsername(), start, end).stream()
                .map(MessageDto::fromEntity)
                .collect(Collectors.toList());
    }

    @PostMapping("/rooms/{roomId}/kick")
    public ResponseEntity<?> kickParticipant(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("roomId") Long roomId,
            @RequestBody java.util.Map<String, String> body) {
        User admin = requireUser(authorization);
        String targetUsername = body.get("username");
        if (targetUsername == null || targetUsername.isBlank()) {
            throw new IllegalArgumentException("Укажите имя пользователя для удаления");
        }
        chatService.kickParticipant(roomId, admin.getUsername(), targetUsername.trim());
        webSocketHandler.broadcastSystemMessage(roomId, targetUsername.trim() + " был удалён из группы");
        return ResponseEntity.ok(java.util.Map.of("message", "Участник удалён"));
    }

    @GetMapping("/invitations")
    public List<InvitationDto> getInvitations(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        User user = requireUser(authorization);
        return chatService.getPendingInvitations(user.getUsername()).stream()
                .map(InvitationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<?> acceptInvitation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("invitationId") Long invitationId) {
        User user = requireUser(authorization);
        ChatRoom room = chatService.acceptInvitation(invitationId, user.getUsername());
        webSocketHandler.broadcastSystemMessage(room.getId(), user.getUsername() + " присоединился к группе");
        return ResponseEntity.ok(java.util.Map.of("message", "Приглашение принято"));
    }

    @PostMapping("/invitations/{invitationId}/decline")
    public ResponseEntity<?> declineInvitation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("invitationId") Long invitationId) {
        User user = requireUser(authorization);
        chatService.declineInvitation(invitationId, user.getUsername());
        return ResponseEntity.ok(java.util.Map.of("message", "Приглашение отклонено"));
    }

    private User requireUser(String authorization) {
        String token = AuthTokens.bearer(authorization);
        if (token == null || token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется заголовок Authorization: Bearer …");
        }
        return userService.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Недействительный токен"));
    }
}
