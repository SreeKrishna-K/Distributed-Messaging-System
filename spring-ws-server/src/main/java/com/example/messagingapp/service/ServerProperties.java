package com.example.messagingapp.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.messagingapp.config.MessagingProperties;
import lombok.Getter;

@Component
@Getter
public class ServerProperties {

    private final MessagingProperties messagingProperties;
    private String serverId;
    
    public ServerProperties(MessagingProperties messagingProperties) {
        this.messagingProperties = messagingProperties;
    }
    
    public String getServerId() {
        if (serverId != null && !serverId.isEmpty()) {
            return serverId;
        }
        
        // Try to get from configuration
        String configServerId = messagingProperties.getServer().getId();
        if (configServerId != null && !configServerId.isEmpty()) {
            serverId = configServerId;
            return serverId;
        }
        
        // Generate a random server ID if not set
        serverId = UUID.randomUUID().toString();
        return serverId;
    }
}
