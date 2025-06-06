package com.example.messagingapp.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.messagingapp.model.ChatMessage;
import com.example.messagingapp.service.MessageService;
import com.example.messagingapp.service.RedisService;
import com.example.messagingapp.service.ServerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ChatWebSocketHandlerTest {

    private ChatWebSocketHandler chatWebSocketHandler;
    
    @Mock
    private WebSocketSession session;
    
    @Mock
    private RedisService redisService;
    
    @Mock
    private MessageService messageService;
    
    @Mock
    private ServerProperties serverProperties;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        chatWebSocketHandler = new ChatWebSocketHandler(objectMapper, messageService, redisService, serverProperties);
        
        when(serverProperties.getServerId()).thenReturn("test-server-id");
        
        // Mock ObjectMapper behavior
        when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());
        
        // Mock session attributes
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "test-user");
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("test-session-id");
        
        // Mock Redis and Message service behavior
        when(redisService.setUserServer(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(messageService.sendDirectMessage(any(ChatMessage.class))).thenReturn(CompletableFuture.completedFuture(true));
    }
    
    @Test
    public void testHandleTextMessage_ChatMessage() throws Exception {
        // Prepare a chat message
        ObjectNode messageNode = new ObjectMapper().createObjectNode();
        messageNode.put("type", "chat");
        messageNode.put("message", "Hello everyone!");
        messageNode.put("recipientId", "");
        
        TextMessage textMessage = new TextMessage(messageNode.toString());
        
        // Test the handler
        chatWebSocketHandler.handleTextMessage(session, textMessage);
        
        // Verify message was sent to Kafka
        verify(messageService, times(1)).sendDirectMessage(any(ChatMessage.class));
    }
    
    @Test
    public void testAfterConnectionEstablished() throws Exception {
        // Mock the send method on the session
        doNothing().when(session).sendMessage(any(TextMessage.class));
        
        // Test connection established
        chatWebSocketHandler.afterConnectionEstablished(session);
        
        // Verify user-server mapping was saved
        verify(redisService, times(1)).setUserServer(anyString(), anyString());
        
        // Verify an info message was sent back to the client
        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
}
