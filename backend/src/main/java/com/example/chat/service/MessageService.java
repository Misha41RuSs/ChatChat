package com.example.chat.service;

import com.example.chat.dto.NewMessageDto;
import com.example.chat.entity.ChatRoom;
import com.example.chat.entity.Message;
import com.example.chat.entity.User;
import com.example.chat.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final ChatService chatService;

    public MessageService(MessageRepository messageRepository, ChatService chatService) {
        this.messageRepository = messageRepository;
        this.chatService = chatService;
    }

    public Message saveMessage(NewMessageDto payload, User user, String senderIp, long timestamp) {
        chatService.assertParticipant(payload.getRoomId(), user.getUsername());
        ChatRoom room = chatService.findById(payload.getRoomId());
        Message message = new Message();
        message.setChatRoom(room);
        message.setSender(user.getUsername());
        message.setContent(payload.getContent());
        message.setFileUrl(payload.getFileUrl());
        message.setSenderIp(senderIp);
        message.setSentAt(Instant.ofEpochMilli(timestamp));
        return messageRepository.save(message);
    }

    public List<Message> getMessages(Long roomId, String readerUsername, Instant from, Instant to) {
        chatService.assertParticipant(roomId, readerUsername);
        ChatRoom room = chatService.findById(roomId);
        if (from != null && to != null) {
            return messageRepository.findByChatRoomAndSentAtBetweenOrderBySentAtAsc(room, from, to);
        }
        return messageRepository.findByChatRoomOrderBySentAtAsc(room);
    }
}
