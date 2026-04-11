package com.example.chat.dto;

import com.example.chat.entity.ChatRoom;
import lombok.Data;

import java.util.List;

@Data
public class ChatRoomDto {
    private Long id;
    private String name;
    private String type;
    private List<String> participants;

    public static ChatRoomDto fromEntity(ChatRoom room) {
        ChatRoomDto dto = new ChatRoomDto();
        dto.setId(room.getId());
        dto.setName(room.getName());
        dto.setType(room.getType().name());
        dto.setParticipants(room.getParticipants());
        return dto;
    }
}
