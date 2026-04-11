package com.example.chat.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

@Data
public class NewMessageDto {
    private Long roomId;
    private String content;
    private String fileUrl;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static NewMessageDto fromJson(String payload) throws JsonProcessingException {
        return MAPPER.readValue(payload, NewMessageDto.class);
    }
}
