package com.anip.kyc.dto.document;

import java.util.UUID;

public class DocumentListRequest {
    private UUID sessionId;
    private String documentType;
    private String validationStatus;
    private String requestingUserId;

    public static Builder builder(){ return new Builder(); }
    public static class Builder{ private final DocumentListRequest r = new DocumentListRequest();
        public Builder sessionId(UUID s){ r.sessionId = s; return this; }
        public Builder documentType(String t){ r.documentType = t; return this; }
        public Builder validationStatus(String v){ r.validationStatus = v; return this; }
        public Builder requestingUserId(String u){ r.requestingUserId = u; return this; }
        public DocumentListRequest build(){ return r; }
    }
    public UUID getSessionId(){ return sessionId; }
    public String getDocumentType(){ return documentType; }
    public String getValidationStatus(){ return validationStatus; }
    public String getRequestingUserId(){ return requestingUserId; }
}
