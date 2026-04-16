package com.example.chat.service;

import com.example.chat.entity.ChatRoom;
import com.example.chat.entity.RoomInvitation;
import com.example.chat.repository.ChatRoomRepository;
import com.example.chat.repository.RoomInvitationRepository;
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
    private final RoomInvitationRepository invitationRepository;

    public ChatService(ChatRoomRepository chatRoomRepository, UserRepository userRepository, RoomInvitationRepository invitationRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
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

        LinkedHashSet<String> invitees = new LinkedHashSet<>();
        if (extraParticipants != null) {
            for (String u : extraParticipants) {
                if (u == null || u.isBlank()) {
                    continue;
                }
                String trimmed = u.trim();
                if (!userRepository.existsByUsername(trimmed)) {
                    throw new IllegalArgumentException("Неизвестный пользователь: " + trimmed);
                }
                if (!trimmed.equals(creatorUsername)) {
                    invitees.add(trimmed);
                }
            }
        }

        ChatRoom room = new ChatRoom();
        room.setName(name.trim());
        room.setType(type);
        room.setCreatorUsername(creatorUsername);
        room.setParticipants(List.of(creatorUsername));
        ChatRoom savedRoom = chatRoomRepository.save(room);

        for (String invitee : invitees) {
            RoomInvitation invitation = new RoomInvitation();
            invitation.setChatRoom(savedRoom);
            invitation.setInvitedUsername(invitee);
            invitation.setInvitedBy(creatorUsername);
            invitationRepository.save(invitation);
        }

        return savedRoom;
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

    public boolean isAdmin(Long roomId, String username) {
        ChatRoom room = findById(roomId);
        return username != null && username.equals(room.getCreatorUsername());
    }

    public void kickParticipant(Long roomId, String adminUsername, String targetUsername) {
        ChatRoom room = findById(roomId);

        if (!isAdmin(roomId, adminUsername)) {
            throw new IllegalArgumentException("Только создатель группы может удалять участников");
        }

        if (adminUsername.equals(targetUsername)) {
            throw new IllegalArgumentException("Нельзя удалить самого себя");
        }

        if (room.getParticipants() == null || !room.getParticipants().contains(targetUsername)) {
            throw new IllegalArgumentException("Пользователь не является участником группы");
        }

        room.getParticipants().remove(targetUsername);
        chatRoomRepository.save(room);
    }

    public List<RoomInvitation> getPendingInvitations(String username) {
        return invitationRepository.findByInvitedUsernameAndStatus(username, RoomInvitation.InvitationStatus.PENDING);
    }

    public ChatRoom acceptInvitation(Long invitationId, String username) {
        RoomInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Приглашение не найдено"));

        if (!invitation.getInvitedUsername().equals(username)) {
            throw new IllegalArgumentException("Это приглашение не для вас");
        }

        if (invitation.getStatus() != RoomInvitation.InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Приглашение уже обработано");
        }

        ChatRoom room = invitation.getChatRoom();
        if (!room.getParticipants().contains(username)) {
            room.getParticipants().add(username);
            chatRoomRepository.save(room);
        }

        invitation.setStatus(RoomInvitation.InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        return room;
    }

    public void declineInvitation(Long invitationId, String username) {
        RoomInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Приглашение не найдено"));

        if (!invitation.getInvitedUsername().equals(username)) {
            throw new IllegalArgumentException("Это приглашение не для вас");
        }

        if (invitation.getStatus() != RoomInvitation.InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Приглашение уже обработано");
        }

        invitation.setStatus(RoomInvitation.InvitationStatus.DECLINED);
        invitationRepository.save(invitation);
    }
}
