package com.anip.kyc.dto.face;

import java.util.UUID;

public class BiometricDeletionRequest {
    private UUID sessionId;
    private String reason;
    private String deletingUserId;
    private boolean adminAction;

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final BiometricDeletionRequest r = new BiometricDeletionRequest();
        public Builder sessionId(UUID id) { r.sessionId = id; return this; }
        public Builder reason(String s) { r.reason = s; return this; }
        public Builder deletingUserId(String u) { r.deletingUserId = u; return this; }
        public Builder adminAction(boolean b) { r.adminAction = b; return this; }
        public BiometricDeletionRequest build() { return r; }
    }

    public UUID getSessionId() { return sessionId; }
    public String getReason() { return reason; }
    public String getDeletingUserId() { return deletingUserId; }
    public boolean isAdminAction() { return adminAction; }
}
