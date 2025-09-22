package com.anip.kyc.dto.document;

import java.util.UUID;
import java.util.Map;

public class DocumentExtractionRequest {
    private UUID documentId;
    private String requestingUserId;
    private Map<String,String> options;

    public void setDocumentId(UUID id){ this.documentId = id; }
    public void setRequestingUserId(String u){ this.requestingUserId = u; }
    public UUID getDocumentId(){ return documentId; }
}
