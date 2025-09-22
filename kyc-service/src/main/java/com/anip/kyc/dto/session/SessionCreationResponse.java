package com.anip.kyc.dto.session;

import java.util.UUID;

public class SessionCreationResponse {
    private UUID sessionId;
    private String status;

    public SessionCreationResponse() {}

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
