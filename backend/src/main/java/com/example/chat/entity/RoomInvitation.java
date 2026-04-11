package com.example.chat.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "room_invitations")
@Data
public class RoomInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "room_id")
    private ChatRoom chatRoom;

    private String invitedUsername;
    private String invitedBy;
    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    private InvitationStatus status = InvitationStatus.PENDING;

    public enum InvitationStatus {
        PENDING, ACCEPTED, DECLINED
    }
}
