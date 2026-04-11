package com.example.chat.repository;

import com.example.chat.entity.ChatRoom;
import com.example.chat.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChatRoomAndSentAtBetweenOrderBySentAtAsc(ChatRoom chatRoom, Instant from, Instant to);
    List<Message> findByChatRoomOrderBySentAtAsc(ChatRoom chatRoom);
}
