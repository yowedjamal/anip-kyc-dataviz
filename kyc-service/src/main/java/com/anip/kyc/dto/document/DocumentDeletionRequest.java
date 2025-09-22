package com.anip.kyc.dto.document;

import java.util.UUID;

public class DocumentDeletionRequest {
    private UUID documentId;
    private String reason;
    private String deletingUserId;
    private boolean adminAction;

    public static Builder builder(){ return new Builder(); }
    public static class Builder{ private final DocumentDeletionRequest r = new DocumentDeletionRequest();
        public Builder documentId(UUID id){ r.documentId = id; return this; }
        public Builder reason(String s){ r.reason = s; return this; }
        public Builder deletingUserId(String u){ r.deletingUserId = u; return this; }
        public Builder adminAction(boolean a){ r.adminAction = a; return this; }
        public DocumentDeletionRequest build(){ return r; }
    }
    // Getters
    public UUID getDocumentId() { return documentId; }
    public String getReason() { return reason; }
    public String getDeletingUserId() { return deletingUserId; }
    public boolean isAdminAction() { return adminAction; }
}
