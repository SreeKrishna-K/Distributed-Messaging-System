package com.example.messagingapp.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebRTCSignal {
    // Signal types: "offer", "answer", "ice-candidate", "call-request", "call-response", "call-end"
    private String type;
    
    // Who is initiating/sending the signal
    private String from;
    
    // Who should receive the signal
    private String to;
    
    // The actual signal data (SDP for offer/answer, candidate for ICE)
    private Object payload;
    
    // For tracking when the signal was sent
    private Instant timestamp;
    
    // Additional metadata
    private Boolean video;
    private Boolean audio;
    private Boolean screenShare;
    private String callId;
    private Boolean accepted;
}
