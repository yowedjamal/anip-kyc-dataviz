package com.anip.kyc.dto.session;

import java.util.UUID;

public class SessionValidationRequest {
    private UUID sessionId;
    private String validatingUserId;

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public String getValidatingUserId() { return validatingUserId; }
    public void setValidatingUserId(String validatingUserId) { this.validatingUserId = validatingUserId; }
}
