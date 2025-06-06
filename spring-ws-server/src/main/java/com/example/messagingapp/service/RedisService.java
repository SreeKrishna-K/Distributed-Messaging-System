package com.example.messagingapp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Get all connected users from Redis
     */
    @Async
    public CompletableFuture<List<String>> getAllConnectedUsers() {
        List<String> connectedUsers = new ArrayList<>();
        try {
            Set<String> keys = redisTemplate.keys("user:*:server");
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    String userId = key.split(":")[1];
                    connectedUsers.add(userId);
                }
            }
        } catch (Exception e) {
            log.error("Error getting connected users from Redis: {}", e.getMessage());
        }
        return CompletableFuture.completedFuture(connectedUsers);
    }

    /**
     * Get the server ID for a specific user
     */
    @Async
    public CompletableFuture<String> getUserServer(String userId) {
        try {
            String serverId = redisTemplate.opsForValue().get("user:" + userId + ":server");
            return CompletableFuture.completedFuture(serverId);
        } catch (Exception e) {
            log.error("Error getting server for user {} from Redis: {}", userId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Remove the server mapping for a user
     */
    @Async
    public CompletableFuture<Void> removeUserServer(String userId) {
        try {
            redisTemplate.delete("user:" + userId + ":server");
            log.info("Removed server mapping for user {}", userId);
        } catch (Exception e) {
            log.error("Error removing server mapping for user {} from Redis: {}", userId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Set the server ID for a specific user
     */
    @Async
    public CompletableFuture<Void> setUserServer(String userId, String serverId) {
        try {
            redisTemplate.opsForValue().set("user:" + userId + ":server", serverId);
            log.info("Set server mapping for user {} to server {}", userId, serverId);
        } catch (Exception e) {
            log.error("Error setting server for user {} in Redis: {}", userId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }
}
