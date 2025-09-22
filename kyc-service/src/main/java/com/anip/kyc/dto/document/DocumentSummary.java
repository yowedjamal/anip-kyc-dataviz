package com.anip.kyc.dto.document;

import java.util.UUID;

public class DocumentSummary {
    private UUID documentId;
    private String documentType;
    private String processingStatus;

    public UUID getDocumentId(){ return documentId; }
    public String getDocumentType(){ return documentType; }
    public String getProcessingStatus(){ return processingStatus; }

    public void setDocumentId(UUID id){ this.documentId = id; }
    public void setDocumentType(String t){ this.documentType = t; }
    public void setProcessingStatus(String s){ this.processingStatus = s; }
}
