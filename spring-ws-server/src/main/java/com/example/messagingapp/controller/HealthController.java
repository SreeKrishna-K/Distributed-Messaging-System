package com.example.messagingapp.controller;

import java.util.HashMap;
import java.util.Map;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.messagingapp.service.ServerProperties;

@RestController
public class HealthController {

    private final ServerProperties serverProperties;

    public HealthController(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "ok");
        status.put("serverId", serverProperties.getServerId());
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }
}
