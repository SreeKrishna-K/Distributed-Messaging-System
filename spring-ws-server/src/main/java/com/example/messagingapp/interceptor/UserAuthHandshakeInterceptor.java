package com.example.messagingapp.interceptor;

import java.util.Map;


import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.messagingapp.service.ServerProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class UserAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final RedisTemplate<String, String> redisTemplate;
    private final ServerProperties serverProperties;
    
    public UserAuthHandshakeInterceptor(RedisTemplate<String, String> redisTemplate, ServerProperties serverProperties) {
        this.redisTemplate = redisTemplate;
        this.serverProperties = serverProperties;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                  WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // Log the full URL to help with debugging
        log.info("WebSocket connection attempt URL: {}", request.getURI());

        // Extract user ID from query parameters
        String uri = request.getURI().toString();
        Map<String, String> queryParams = UriComponentsBuilder.fromUriString(uri)
                .build()
                .getQueryParams()
                .toSingleValueMap();
                
        log.info("Query parameters: {}", queryParams);

        // Try to get user ID from multiple sources
        String userId = queryParams.get("X-Auth-User-Id");
        if (userId == null) {
            userId = request.getHeaders().getFirst("x-auth-user-id");
        }

        log.info("Extracted user ID: {}", userId);

        // Check if user ID is provided
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("Connection rejected: Missing user ID");
            return false;
        }

        // Store user ID in attributes for later use
        attributes.put("userId", userId);

        // Save the user's server association in Redis
        try {
            redisTemplate.opsForValue().set("user:" + userId + ":server", serverProperties.getServerId());
            log.info("User {} assigned to server {}", userId, serverProperties.getServerId());
        } catch (Exception e) {
            log.error("Redis error during user assignment: {}", e.getMessage());
            // Continue even if Redis fails - don't block the user connection
        }

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                              WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do after handshake
        log.info("This after the connection is established");
    }
}
