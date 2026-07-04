package com.archipelago.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuthAuditEvent {
    private Long id;
    private LocalDateTime eventTime;
    private Long userId;
    private String eventType;
    private String outcome;
    private String ipHash;
    private String userAgentHash;
}
