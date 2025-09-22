package com.anip.kyc.dto.face;

import java.util.UUID;

public class BiometricTemplatesRequest {
    private UUID sessionId;
    private boolean includeMetadata;
    private String requestingUserId;

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final BiometricTemplatesRequest r = new BiometricTemplatesRequest();
        public Builder sessionId(UUID id) { r.sessionId = id; return this; }
        public Builder includeMetadata(boolean b) { r.includeMetadata = b; return this; }
        public Builder requestingUserId(String u) { r.requestingUserId = u; return this; }
        public BiometricTemplatesRequest build() { return r; }
    }

    public UUID getSessionId() { return sessionId; }
    public boolean isIncludeMetadata() { return includeMetadata; }
    public String getRequestingUserId() { return requestingUserId; }
}
