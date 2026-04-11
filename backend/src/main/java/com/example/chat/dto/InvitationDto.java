package com.example.chat.dto;

import com.example.chat.entity.RoomInvitation;
import lombok.Data;

import java.time.Instant;

@Data
public class InvitationDto {
    private Long id;
    private Long roomId;
    private String roomName;
    private String invitedBy;
    private Instant createdAt;
    private String status;

    public static InvitationDto fromEntity(RoomInvitation invitation) {
        InvitationDto dto = new InvitationDto();
        dto.setId(invitation.getId());
        dto.setRoomId(invitation.getChatRoom().getId());
        dto.setRoomName(invitation.getChatRoom().getName());
        dto.setInvitedBy(invitation.getInvitedBy());
        dto.setCreatedAt(invitation.getCreatedAt());
        dto.setStatus(invitation.getStatus().name());
        return dto;
    }
}
