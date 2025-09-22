package com.anip.kyc.dto.document;

import java.util.UUID;

public class DocumentValidationRequest {
    private UUID documentId;
    private String validatingUserId;

    public void setDocumentId(UUID id){ this.documentId = id; }
    public void setValidatingUserId(String u){ this.validatingUserId = u; }
    public UUID getDocumentId(){ return documentId; }
}
