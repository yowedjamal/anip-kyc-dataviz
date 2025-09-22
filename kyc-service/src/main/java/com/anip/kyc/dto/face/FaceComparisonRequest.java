package com.anip.kyc.dto.face;

import java.util.UUID;

public class FaceComparisonRequest {
    private UUID sessionId;
    private UUID documentId;
    private String userId;

    public UUID getSessionId() { return sessionId; }
    public UUID getDocumentId() { return documentId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
