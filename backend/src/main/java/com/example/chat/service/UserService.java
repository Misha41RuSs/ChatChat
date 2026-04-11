package com.example.chat.service;

import com.example.chat.entity.User;
import com.example.chat.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        return userRepository.save(user);
    }

    public String login(String username, String password) {
        Optional<User> optional = userRepository.findByUsername(username);
        if (optional.isEmpty() || !optional.get().getPassword().equals(password)) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        String token = UUID.randomUUID().toString();
        tokens.put(token, username);
        return token;
    }

    public Optional<User> findByToken(String token) {
        return Optional.ofNullable(tokens.get(token)).flatMap(userRepository::findByUsername);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<String> listOtherUsernames(String exclude, int limit) {
        return userRepository.findAll().stream()
                .map(User::getUsername)
                .filter(u -> !u.equalsIgnoreCase(exclude))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<String> searchUsernames(String prefix, String exclude, int limit) {
        return userRepository.findByUsernameStartingWithIgnoreCase(prefix).stream()
                .map(User::getUsername)
                .filter(u -> !u.equalsIgnoreCase(exclude))
                .sorted(Comparator.comparing(String::toLowerCase))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
