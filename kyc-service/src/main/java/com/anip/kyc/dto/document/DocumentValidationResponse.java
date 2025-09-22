package com.anip.kyc.dto.document;

public class DocumentValidationResponse {
    private double validationScore;
    private String validationStatus;

    public double getValidationScore(){ return validationScore; }
    public String getValidationStatus(){ return validationStatus; }
    public void setValidationScore(double s){ this.validationScore = s; }
    public void setValidationStatus(String s){ this.validationStatus = s; }

    // Backwards-compatible aliases: new naming used across models/services
    public double getConfidenceScore() { return validationScore; }
    public void setConfidenceScore(double confidenceScore) { this.validationScore = confidenceScore; }

    public String getProcessingStatus() { return validationStatus; }
    public void setProcessingStatus(String processingStatus) { this.validationStatus = processingStatus; }
}
