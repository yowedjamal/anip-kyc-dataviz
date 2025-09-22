package com.anip.kyc.dto.document;

import java.util.UUID;
import java.util.List;

public class DocumentDetailsResponse {
    private UUID documentId;
    private String documentType;
    private List<String> ocrLines;

    public UUID getDocumentId(){ return documentId; }
    public String getDocumentType(){ return documentType; }
    public List<String> getOcrLines(){ return ocrLines; }

    public void setDocumentId(UUID id){ this.documentId = id; }
    public void setDocumentType(String t){ this.documentType = t; }
    public void setOcrLines(List<String> l){ this.ocrLines = l; }
}
