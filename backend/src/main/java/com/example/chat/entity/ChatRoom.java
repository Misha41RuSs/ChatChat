package com.example.chat.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_rooms")
@Data
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private RoomType type = RoomType.GROUP;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chat_room_participants", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "username")
    private List<String> participants = new ArrayList<>();

    public enum RoomType {
        PRIVATE, GROUP
    }
}
