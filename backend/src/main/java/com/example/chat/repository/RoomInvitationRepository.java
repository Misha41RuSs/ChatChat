package com.example.chat.repository;

import com.example.chat.entity.RoomInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomInvitationRepository extends JpaRepository<RoomInvitation, Long> {
    List<RoomInvitation> findByInvitedUsernameAndStatus(String invitedUsername, RoomInvitation.InvitationStatus status);
}
