package com.example.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DirectMessageRequest {
    @NotBlank
    private String withUsername;
}
