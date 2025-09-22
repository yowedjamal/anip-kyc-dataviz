package com.anip.kyc.dto.document;

import java.util.UUID;

public class DocumentDetailsRequest {
    private UUID documentId;
    private boolean includeOcrData;
    private boolean includeValidationHistory;
    private String requestingUserId;

    public static Builder builder(){ return new Builder(); }
    public static class Builder{
        private final DocumentDetailsRequest r = new DocumentDetailsRequest();
        public Builder documentId(UUID id){ r.documentId = id; return this; }
        public Builder includeOcrData(boolean v){ r.includeOcrData = v; return this; }
        public Builder includeValidationHistory(boolean v){ r.includeValidationHistory = v; return this; }
        public Builder requestingUserId(String u){ r.requestingUserId = u; return this; }
        public DocumentDetailsRequest build(){ return r; }
    }

    public UUID getDocumentId(){ return documentId; }
    public boolean isIncludeOcrData(){ return includeOcrData; }
    public boolean isIncludeValidationHistory(){ return includeValidationHistory; }
    public String getRequestingUserId(){ return requestingUserId; }
}
