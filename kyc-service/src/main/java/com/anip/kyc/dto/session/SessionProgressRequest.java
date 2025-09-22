package com.anip.kyc.dto.session;

import java.util.UUID;

public class SessionProgressRequest {
    private UUID sessionId;
    private String targetStep;
    private String progressingUserId;

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public String getTargetStep() { return targetStep; }
    public void setTargetStep(String targetStep) { this.targetStep = targetStep; }
    public String getProgressingUserId() { return progressingUserId; }
    public void setProgressingUserId(String progressingUserId) { this.progressingUserId = progressingUserId; }
}
