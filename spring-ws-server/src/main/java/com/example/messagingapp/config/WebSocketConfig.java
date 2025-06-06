package com.example.messagingapp.config;

// import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.messagingapp.handler.ChatWebSocketHandler;
import com.example.messagingapp.interceptor.UserAuthHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler webSocketHandler;
    private final UserAuthHandshakeInterceptor handshakeInterceptor;

    // @Autowired
    public WebSocketConfig(ChatWebSocketHandler webSocketHandler, UserAuthHandshakeInterceptor handshakeInterceptor) {
        this.webSocketHandler = webSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
