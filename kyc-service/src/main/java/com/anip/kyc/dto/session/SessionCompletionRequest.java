package com.anip.kyc.dto.session;

import java.util.UUID;

public class SessionCompletionRequest {
    private UUID sessionId;
    private String completingUserId;
    private String notes;

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public String getCompletingUserId() { return completingUserId; }
    public void setCompletingUserId(String completingUserId) { this.completingUserId = completingUserId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
