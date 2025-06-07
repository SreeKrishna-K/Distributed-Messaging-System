package com.example.messagingapp.handler;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.messagingapp.model.ChatMessage;
import com.example.messagingapp.model.WebRTCSignal;
import com.example.messagingapp.service.MessageService;
import com.example.messagingapp.service.RedisService;
import com.example.messagingapp.service.ServerProperties;
import com.example.messagingapp.service.VideoCallService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    // Store connected clients: sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Store client-user mapping: sessionId -> userId
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();
    
    // Store user-sessionId mapping for reverse lookups: userId -> Set<sessionId>
    private final Map<String, Map<String, WebSocketSession>> userSessionsMap = new ConcurrentHashMap<>();

    private final ServerProperties serverProperties;
    private final RedisService redisService;
    private final MessageService messageService;
    private final VideoCallService videoCallService;
    private final ObjectMapper objectMapper;
    
    public ChatWebSocketHandler(ObjectMapper objectMapper, 
                               MessageService messageService,
                               RedisService redisService,
                               ServerProperties serverProperties,
                               VideoCallService videoCallService) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.redisService = redisService;
        this.serverProperties = serverProperties;
        this.videoCallService = videoCallService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        String userId = (String) session.getAttributes().get("userId");
        
        if (userId == null) {
            log.error("No userId found in session attributes");
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Authentication required"));
            return;
        }
        
        // Store session
        sessions.put(sessionId, session);
        sessionUserMap.put(sessionId, userId);
        
        // Add to user-session mapping
        userSessionsMap.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                      .put(sessionId, session);
        
        log.info("Client connected: {}, User: {} to server: {}", sessionId, userId, serverProperties.getServerId());
        
        // Send welcome message
        ChatMessage welcomeMessage = ChatMessage.builder()
                .type("info")
                .serverId(serverProperties.getServerId())
                .clientId(sessionId)
                .userId(userId)
                .message("Connected to WebSocket Server " + serverProperties.getServerId() + " as " + userId)
                .timestamp(Instant.now())
                .build();
                
        // Get connected users and add to message
        redisService.getAllConnectedUsers().thenAccept(connectedUsers -> {
            try {
                welcomeMessage.setAdditionalData(Map.of(
                    "clients", sessions.size(),
                    "connectedUsers", connectedUsers
                ));
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMessage)));
                
                // Broadcast user joined notification
                broadcastUserUpdate(userId, "joined");
            } catch (IOException e) {
                log.error("Error sending welcome message to user {}: {}", userId, e.getMessage());
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String userId = sessionUserMap.get(sessionId);
        
        if (userId == null) {
            log.warn("Message received from unknown session: {}", sessionId);
            return;
        }
        
        try {
            // First, try to parse as a WebRTC signal
            try {
                WebRTCSignal webRTCSignal = objectMapper.readValue(message.getPayload(), WebRTCSignal.class);
                if (webRTCSignal.getType() != null && isValidWebRTCSignalType(webRTCSignal.getType())) {
                    log.info("Received WebRTC signal from {} ({}): {}", userId, sessionId, webRTCSignal.getType());
                    videoCallService.processSignal(webRTCSignal, userId, userSessionsMap);
                    return;
                }
            } catch (JsonProcessingException e) {
                // Not a WebRTC signal, continue with ChatMessage parsing
            }
            
            // If not a WebRTC signal, treat as a normal chat message
            ChatMessage chatMessage = objectMapper.readValue(message.getPayload(), ChatMessage.class);
            log.info("Received message from {} ({}): {}", userId, sessionId, chatMessage);
            
            // Enrich message with metadata
            chatMessage.setServerId(serverProperties.getServerId());
            chatMessage.setClientId(sessionId);
            chatMessage.setUserId(userId);
            chatMessage.setTimestamp(Instant.now());
            
            // Process message based on recipient
            if (chatMessage.getRecipientId() != null && !chatMessage.getRecipientId().isEmpty()) {
                // Direct message
                messageService.sendDirectMessage(chatMessage)
                    .thenAccept(result -> {
                        try {
                            if (result) {
                                // Notify sender of success
                                ChatMessage confirmation = ChatMessage.builder()
                                    .type("sent")
                                    .recipientId(chatMessage.getRecipientId())
                                    .timestamp(Instant.now())
                                    .build();
                                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(confirmation)));
                            } else {
                                // Notify sender of failure
                                ChatMessage error = ChatMessage.builder()
                                    .type("error")
                                    .message("Error: Failed to send message")
                                    .timestamp(Instant.now())
                                    .build();
                                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
                            }
                        } catch (IOException e) {
                            log.error("Error sending confirmation to user {}: {}", userId, e.getMessage());
                        }
                    });
            } else {
                // Broadcast message to all clients
                broadcast(chatMessage);
            }
        } catch (Exception e) {
            log.error("Error parsing message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        String userId = sessionUserMap.get(sessionId);
        
        if (userId != null) {
            log.info("Client disconnected: {} ({}) from server: {}", userId, sessionId, serverProperties.getServerId());
            
            // Remove from mappings
            sessions.remove(sessionId);
            sessionUserMap.remove(sessionId);
            
            if (userSessionsMap.containsKey(userId)) {
                userSessionsMap.get(userId).remove(sessionId);
                
                // If this was the last session for this user on this server
                if (userSessionsMap.get(userId).isEmpty()) {
                    userSessionsMap.remove(userId);
                    
                    // Remove user-server mapping from Redis
                    redisService.removeUserServer(userId).thenRun(() -> {
                        log.info("Removed server mapping for user {}", userId);
                        
                        // Broadcast user left notification
                        broadcastUserUpdate(userId, "left");
                    });
                }
            }
            
            // Broadcast status update
            broadcastStatus();
        }
    }

    /**
     * Broadcasts a message to all connected clients
     */
    private void broadcast(ChatMessage message) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
            for (WebSocketSession session : sessions.values()) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
            }
        } catch (IOException e) {
            log.error("Error broadcasting message: {}", e.getMessage());
        }
    }

    /**
     * Broadcasts status update to all clients
     */
    private void broadcastStatus() {
        ChatMessage statusMessage = ChatMessage.builder()
            .type("status")
            .serverId(serverProperties.getServerId())
            .timestamp(Instant.now())
            .additionalData(Map.of("clients", sessions.size()))
            .build();
            
        broadcast(statusMessage);
    }

    /**
     * Broadcasts user update (join/leave) to all clients
     */
    private void broadcastUserUpdate(String userId, String action) {
        ChatMessage userUpdate = ChatMessage.builder()
            .type("user-" + action)
            .userId(userId)
            .serverId(serverProperties.getServerId())
            .timestamp(Instant.now())
            .build();
            
        broadcast(userUpdate);
        
        // Also update the user list
        redisService.getAllConnectedUsers().thenAccept(users -> {
            log.info("Broadcasting updated user list with {} users", users.size());
            for (String user : users) {
                log.info("Connected user: {}", user);
            }
            
            // We can't use .users() directly since Lombok doesn't know about it yet
            // Create a map with all the data we want to send
            Map<String, Object> additionalData = new HashMap<>();
            additionalData.put("users", users);
            
            ChatMessage userListUpdate = ChatMessage.builder()
                .type("user-list")
                .serverId(serverProperties.getServerId())
                .timestamp(Instant.now())
                .additionalData(additionalData)
                .build();
                
            // Manually set the users field
            try {
                Field usersField = userListUpdate.getClass().getDeclaredField("users");
                usersField.setAccessible(true);
                usersField.set(userListUpdate, users);
            } catch (Exception e) {
                log.error("Error setting users field: {}", e.getMessage());
            }
            
            log.info("Broadcasting user list update: {}", userListUpdate);
            broadcast(userListUpdate);
        });
    }
    
    /**
     * Delivers a message received from Kafka to the appropriate WebSocket clients
     * This method is called by the KafkaMessageHandler
     */
    public void deliverMessage(ChatMessage message) throws IOException {
        log.info("Delivering message from Kafka: {}", message);
        
        // If the message has a recipient, deliver it to both recipient and sender
        if (message.getRecipientId() != null && !message.getRecipientId().isEmpty()) {
            String jsonMessage = objectMapper.writeValueAsString(message);
            boolean delivered = false;
            
            // 1. Deliver to recipient
            Map<String, WebSocketSession> recipientSessions = userSessionsMap.get(message.getRecipientId());
            if (recipientSessions != null && !recipientSessions.isEmpty()) {
                // Send to all sessions for this recipient
                for (WebSocketSession session : recipientSessions.values()) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(jsonMessage));
                        delivered = true;
                    }
                }
                log.info("Message delivered to recipient: {}", message.getRecipientId());
            } else {
                log.warn("No connected sessions found for recipient: {}", message.getRecipientId());
            }
            
            // 2. Deliver to sender (so they see their own messages)
            // Only if the sender is different from the recipient
            if (!message.getUserId().equals(message.getRecipientId())) {
                Map<String, WebSocketSession> senderSessions = userSessionsMap.get(message.getUserId());
                if (senderSessions != null && !senderSessions.isEmpty()) {
                    // Send to all sessions for this sender
                    for (WebSocketSession session : senderSessions.values()) {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(jsonMessage));
                            delivered = true;
                        }
                    }
                    log.info("Message delivered back to sender: {}", message.getUserId());
                }
            }
            
            if (!delivered) {
                log.warn("Message could not be delivered to any session");
            }
        } else {
            // If no recipient specified, broadcast to everyone
            broadcast(message);
        }
    }
    
    /**
     * Check if the signal type is a valid WebRTC signal
     */
    private boolean isValidWebRTCSignalType(String type) {
        return "offer".equals(type) || "answer".equals(type) || "ice-candidate".equals(type) || 
               "call-request".equals(type) || "call-response".equals(type) || "call-end".equals(type);
    }
}
