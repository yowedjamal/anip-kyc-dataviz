package com.anip.kyc.dto.session;

import java.util.UUID;
import org.springframework.data.domain.Pageable;

public class SessionDetailsRequest {
    private UUID sessionId;
    private boolean includeHistory;
    private boolean includeScoring;
    private boolean includeDocuments;
    private String requestingUserId;

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final SessionDetailsRequest r = new SessionDetailsRequest();
        public Builder sessionId(UUID id) { r.sessionId = id; return this; }
        public Builder includeHistory(boolean v) { r.includeHistory = v; return this; }
        public Builder includeScoring(boolean v) { r.includeScoring = v; return this; }
        public Builder includeDocuments(boolean v) { r.includeDocuments = v; return this; }
        public Builder requestingUserId(String id) { r.requestingUserId = id; return this; }
        public SessionDetailsRequest build() { return r; }
    }

    // getters/setters
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public boolean isIncludeHistory() { return includeHistory; }
    public void setIncludeHistory(boolean includeHistory) { this.includeHistory = includeHistory; }
    public boolean isIncludeScoring() { return includeScoring; }
    public void setIncludeScoring(boolean includeScoring) { this.includeScoring = includeScoring; }
    public boolean isIncludeDocuments() { return includeDocuments; }
    public void setIncludeDocuments(boolean includeDocuments) { this.includeDocuments = includeDocuments; }
    public String getRequestingUserId() { return requestingUserId; }
    public void setRequestingUserId(String requestingUserId) { this.requestingUserId = requestingUserId; }
}
