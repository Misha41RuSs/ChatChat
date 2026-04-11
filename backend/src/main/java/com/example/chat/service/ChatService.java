package com.example.chat.service;

import com.example.chat.entity.ChatRoom;
import com.example.chat.repository.ChatRoomRepository;
import com.example.chat.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class ChatService {
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    public ChatService(ChatRoomRepository chatRoomRepository, UserRepository userRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.userRepository = userRepository;
    }

    public List<ChatRoom> listRoomsForUser(String username) {
        return chatRoomRepository.findByParticipantUsername(username);
    }

    public ChatRoom createGroupRoom(String creatorUsername, String name, ChatRoom.RoomType type, List<String> extraParticipants) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Укажите название комнаты");
        }
        userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        LinkedHashSet<String> participants = new LinkedHashSet<>();
        participants.add(creatorUsername);
        if (extraParticipants != null) {
            for (String u : extraParticipants) {
                if (u == null || u.isBlank()) {
                    continue;
                }
                String trimmed = u.trim();
                if (!userRepository.existsByUsername(trimmed)) {
                    throw new IllegalArgumentException("Неизвестный пользователь: " + trimmed);
                }
                participants.add(trimmed);
            }
        }

        if (type == ChatRoom.RoomType.GROUP && participants.size() < 2) {
            throw new IllegalArgumentException("В группе должны быть как минимум два участника (вы и ещё кто-то)");
        }

        ChatRoom room = new ChatRoom();
        room.setName(name.trim());
        room.setType(type);
        room.setParticipants(new ArrayList<>(participants));
        return chatRoomRepository.save(room);
    }

    public ChatRoom openOrCreateDirect(String currentUsername, String otherUsername) {
        if (otherUsername == null || otherUsername.isBlank()) {
            throw new IllegalArgumentException("Укажите имя пользователя");
        }
        String other = otherUsername.trim();
        if (other.equalsIgnoreCase(currentUsername)) {
            throw new IllegalArgumentException("Нельзя открыть личный чат с самим собой");
        }
        if (!userRepository.existsByUsername(other)) {
            throw new IllegalArgumentException("Неизвестный пользователь: " + other);
        }

        List<ChatRoom> candidates = chatRoomRepository.findPrivateRoomsBetween(
                ChatRoom.RoomType.PRIVATE, currentUsername, other);
        Optional<ChatRoom> existing = candidates.stream()
                .filter(r -> r.getParticipants() != null && r.getParticipants().size() == 2)
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        ChatRoom room = new ChatRoom();
        room.setName(dmTitle(currentUsername, other));
        room.setType(ChatRoom.RoomType.PRIVATE);
        room.setParticipants(List.of(currentUsername, other));
        return chatRoomRepository.save(room);
    }

    private static String dmTitle(String a, String b) {
        List<String> sorted = new ArrayList<>(List.of(a, b));
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted.get(0) + " ↔ " + sorted.get(1);
    }

    public ChatRoom findById(Long id) {
        return chatRoomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Комната не найдена"));
    }

    public boolean isParticipant(Long roomId, String username) {
        ChatRoom room = findById(roomId);
        return room.getParticipants() != null && room.getParticipants().contains(username);
    }

    public void assertParticipant(Long roomId, String username) {
        if (!isParticipant(roomId, username)) {
            throw new IllegalArgumentException("Нет доступа к этой комнате");
        }
    }

    public List<String> getRoomParticipants(Long roomId) {
        return new ArrayList<>(findById(roomId).getParticipants());
    }
}
