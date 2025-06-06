package com.example.messagingapp.handler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.messagingapp.model.ChatMessage;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Handler for Kafka messages received via Spring Cloud Stream
 */
@Slf4j
@Component
public class KafkaMessageHandler {

    private final ChatWebSocketHandler webSocketHandler;

    public KafkaMessageHandler(ChatWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

        @Value("${messaging.server.id}")
    private String serverId;

    /**
     * Kafka listener for processing messages from the server's topic
     */
    @KafkaListener(topics = "messages-${messaging.server.id}", groupId = "ws-server-group-${messaging.server.id}")
    public void handleMessage(ChatMessage chatMessage) {
        log.info("Received message from Kafka: {}", chatMessage);
        
        // Handle the message by delivering it to the intended recipient via WebSocket
        try {
            webSocketHandler.deliverMessage(chatMessage);
        } catch (IOException e) {
            log.error("Error delivering message to client: {}", e.getMessage());
        }
    }
}
