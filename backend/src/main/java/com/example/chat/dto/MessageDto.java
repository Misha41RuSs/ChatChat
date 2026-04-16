package com.example.chat.dto;

import com.example.chat.entity.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;

import java.time.Instant;

@Data
public class MessageDto {
    private Long id;
    private Long roomId;
    private String sender;
    private String content;
    private String fileUrl;
    private String senderIp;
    private Instant sentAt;
    private Boolean isSystem;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static MessageDto fromEntity(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setRoomId(message.getChatRoom().getId());
        dto.setSender(message.getSender());
        dto.setContent(message.getContent());
        dto.setFileUrl(message.getFileUrl());
        dto.setSenderIp(message.getSenderIp());
        dto.setSentAt(message.getSentAt());
        dto.setIsSystem(message.getIsSystem());
        return dto;
    }

    public String toJson() throws JsonProcessingException {
        return MAPPER.writeValueAsString(this);
    }
}
