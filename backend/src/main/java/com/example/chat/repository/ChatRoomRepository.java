package com.example.chat.repository;

import com.example.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByName(String name);

    @Query("SELECT DISTINCT r FROM ChatRoom r WHERE :username MEMBER OF r.participants ORDER BY r.id DESC")
    List<ChatRoom> findByParticipantUsername(@Param("username") String username);

    @Query("SELECT r FROM ChatRoom r WHERE r.type = :type AND :u1 MEMBER OF r.participants AND :u2 MEMBER OF r.participants")
    List<ChatRoom> findPrivateRoomsBetween(
            @Param("type") ChatRoom.RoomType type,
            @Param("u1") String u1,
            @Param("u2") String u2);
}
