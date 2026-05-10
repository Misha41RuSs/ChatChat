package com.example.chat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path uploadsDir;

    public FileStorageService(@Value("${chat.uploads-dir}") String uploadsDir) throws IOException {
        this.uploadsDir = Paths.get(uploadsDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadsDir);
    }

    public String storeFile(MultipartFile file) throws IOException {
        String fileName = UUID.randomUUID() + "-" + StringUtils.cleanPath(file.getOriginalFilename());
        Path target = uploadsDir.resolve(fileName);
        file.transferTo(target);
        return "/uploads/" + fileName;
    }
}
