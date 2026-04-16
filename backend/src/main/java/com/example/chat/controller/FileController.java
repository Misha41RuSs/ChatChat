package com.example.chat.controller;

import com.example.chat.entity.User;
import com.example.chat.service.FileStorageService;
import com.example.chat.service.UserService;
import com.example.chat.util.AuthTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileStorageService fileStorageService;
    private final UserService userService;

    public FileController(FileStorageService fileStorageService, UserService userService) {
        this.fileStorageService = fileStorageService;
        this.userService = userService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam("file") MultipartFile file) throws IOException {
        requireUser(authorization);
        String url = fileStorageService.storeFile(file);
        return ResponseEntity.ok(Map.of("fileUrl", url));
    }

    private User requireUser(String authorization) {
        String token = AuthTokens.bearer(authorization);
        if (token == null || token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется авторизация");
        }
        return userService.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Недействительный токен"));
    }
}
