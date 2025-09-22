package com.anip.kyc.dto.document;

import java.util.UUID;

public class DocumentUploadResponse {
    private UUID documentId;
    private double confidenceScore;

    public UUID getDocumentId(){ return documentId; }
    public double getConfidenceScore(){ return confidenceScore; }

    public void setDocumentId(UUID id){ this.documentId = id; }
    public void setConfidenceScore(double s){ this.confidenceScore = s; }
}
