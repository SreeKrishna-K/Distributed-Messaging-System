package com.example.messagingapp.service;

import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.messagingapp.model.ChatMessage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MessageService {

    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;
    private final RedisService redisService;

    public MessageService(KafkaTemplate<String, ChatMessage> kafkaTemplate, RedisService redisService) {
        this.kafkaTemplate = kafkaTemplate;
        this.redisService = redisService;
    }

    /**
     * Send a direct message to a specific user via Kafka
     */
    public CompletableFuture<Boolean> sendDirectMessage(ChatMessage message) {
        String recipientId = message.getRecipientId();
        if (recipientId == null || recipientId.isEmpty()) {
            log.error("Cannot send direct message: Missing recipient ID");
            return CompletableFuture.completedFuture(false);
        }

        return redisService.getUserServer(recipientId)
            .thenApply(targetServerId -> {
                if (targetServerId == null) {
                    log.warn("Recipient {} is not connected to any server", recipientId);
                    return false;
                }

                log.info("Sending message to user {} on server {}", recipientId, targetServerId);
                String targetTopic = "messages-" + targetServerId;
                
                try {
                    // Use KafkaTemplate to send to the dynamic destination
                    kafkaTemplate.send(targetTopic, message)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                log.info("Message sent to topic {} for user {}", targetTopic, recipientId);
                            } else {
                                log.error("Failed to send message to topic {} for user {}: {}", 
                                    targetTopic, recipientId, ex.getMessage());
                            }
                        });
                    return true;
                } catch (Exception e) {
                    log.error("Error sending message to Kafka: {}", e.getMessage());
                    return false;
                }
            });
    }

    // No longer need the Consumer bean as we'll use @KafkaListener instead
}
