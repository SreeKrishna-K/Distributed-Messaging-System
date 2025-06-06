package com.example.messagingapp.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String type;
    private String message;
    private String userId;      // Sender
    private String recipientId; // For direct messages
    private String clientId;
    private String serverId;
    private Instant timestamp;
    private Map<String, Object> additionalData;
    private List<String> users; // Connected users list - specifically for user-list messages
}
