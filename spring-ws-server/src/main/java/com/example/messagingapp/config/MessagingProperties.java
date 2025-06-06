package com.example.messagingapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration properties for the messaging application
 * Maps to the 'messaging' prefix in application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "messaging")
@Data
public class MessagingProperties {
    
    private Server server = new Server();
    
    @Data
    public static class Server {
        private String id;
    }
}
