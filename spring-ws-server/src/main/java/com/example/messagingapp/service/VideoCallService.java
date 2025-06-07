package com.example.messagingapp.service;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.messagingapp.model.WebRTCSignal;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoCallService {
    
    private final ObjectMapper objectMapper;
    
    // Store active calls: callId -> {caller, callee}
    private final Map<String, Map<String, String>> activeCalls = new ConcurrentHashMap<>();
    
    /**
     * Process WebRTC signal and route to the appropriate recipient
     */
    public void processSignal(WebRTCSignal signal, String userId, 
                             Map<String, Map<String, WebSocketSession>> userSessionsMap) throws IOException {
        
        // Ensure signal has timestamp
        if (signal.getTimestamp() == null) {
            signal.setTimestamp(Instant.now());
        }
        
        // Ensure sender info is set
        signal.setFrom(userId);
        
        log.info("Processing WebRTC signal: {} from: {} to: {}", signal.getType(), signal.getFrom(), signal.getTo());
        
        switch (signal.getType()) {
            case "call-request":
                handleCallRequest(signal, userSessionsMap);
                break;
                
            case "call-response":
                handleCallResponse(signal, userSessionsMap);
                break;
                
            case "call-end":
                handleCallEnd(signal, userSessionsMap);
                break;
                
            case "offer":
            case "answer":
            case "ice-candidate":
                relaySignal(signal, userSessionsMap);
                break;
                
            default:
                log.warn("Unknown WebRTC signal type: {}", signal.getType());
        }
    }
    
    /**
     * Handle initial call request
     */
    private void handleCallRequest(WebRTCSignal signal, 
                                  Map<String, Map<String, WebSocketSession>> userSessionsMap) throws IOException {
        String callId = UUID.randomUUID().toString();
        signal.setCallId(callId);
        
        // Store call info
        Map<String, String> callInfo = new ConcurrentHashMap<>();
        callInfo.put("caller", signal.getFrom());
        callInfo.put("callee", signal.getTo());
        activeCalls.put(callId, callInfo);
        
        // Forward to recipient
        relaySignal(signal, userSessionsMap);
    }
    
    /**
     * Handle response to call request (accept/reject)
     */
    private void handleCallResponse(WebRTCSignal signal, 
                                   Map<String, Map<String, WebSocketSession>> userSessionsMap) throws IOException {
        String callId = signal.getCallId();
        
        if (callId != null && activeCalls.containsKey(callId)) {
            if (Boolean.TRUE.equals(signal.getAccepted())) {
                log.info("Call accepted: {}", callId);
                // Call continues, relay the response
                relaySignal(signal, userSessionsMap);
            } else {
                log.info("Call rejected: {}", callId);
                // Call rejected, clean up
                activeCalls.remove(callId);
                relaySignal(signal, userSessionsMap);
            }
        } else {
            log.warn("Received call response for unknown call ID: {}", callId);
        }
    }
    
    /**
     * Handle call end
     */
    private void handleCallEnd(WebRTCSignal signal, 
                              Map<String, Map<String, WebSocketSession>> userSessionsMap) throws IOException {
        String callId = signal.getCallId();
        
        if (callId != null && activeCalls.containsKey(callId)) {
            log.info("Ending call: {}", callId);
            // Clean up call
            activeCalls.remove(callId);
        }
        
        // Relay end signal
        relaySignal(signal, userSessionsMap);
    }
    
    /**
     * Relay signal to recipient
     */
    private void relaySignal(WebRTCSignal signal, 
                            Map<String, Map<String, WebSocketSession>> userSessionsMap) throws IOException {
        String recipientId = signal.getTo();
        
        if (recipientId == null || recipientId.isEmpty()) {
            log.warn("Cannot relay signal: no recipient specified");
            return;
        }
        
        Map<String, WebSocketSession> recipientSessions = userSessionsMap.get(recipientId);
        if (recipientSessions == null || recipientSessions.isEmpty()) {
            log.warn("Cannot relay signal: no sessions found for recipient {}", recipientId);
            
            // If this was a call request, send back rejection
            if ("call-request".equals(signal.getType())) {
                WebRTCSignal rejection = WebRTCSignal.builder()
                    .type("call-response")
                    .from(recipientId)
                    .to(signal.getFrom())
                    .callId(signal.getCallId())
                    .accepted(false)
                    .timestamp(Instant.now())
                    .build();
                
                // Send rejection to caller
                sendSignalToUser(rejection, signal.getFrom(), userSessionsMap);
                
                // Remove the call
                if (signal.getCallId() != null) {
                    activeCalls.remove(signal.getCallId());
                }
            }
            return;
        }
        
        // Convert signal to JSON
        String jsonSignal = objectMapper.writeValueAsString(signal);
        
        // Send to all sessions of the recipient
        for (WebSocketSession session : recipientSessions.values()) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(jsonSignal));
            }
        }
    }
    
    /**
     * Send signal to specific user
     */
    private void sendSignalToUser(WebRTCSignal signal, String userId,
                                 Map<String, Map<String, WebSocketSession>> userSessionsMap) throws IOException {
        Map<String, WebSocketSession> userSessions = userSessionsMap.get(userId);
        
        if (userSessions != null && !userSessions.isEmpty()) {
            String jsonSignal = objectMapper.writeValueAsString(signal);
            
            for (WebSocketSession session : userSessions.values()) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonSignal));
                }
            }
        }
    }
}
