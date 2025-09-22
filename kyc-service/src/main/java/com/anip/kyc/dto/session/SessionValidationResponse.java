package com.anip.kyc.dto.session;

public class SessionValidationResponse {
    private String validationStatus;
    private double overallScore;

    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public double getOverallScore() { return overallScore; }
    public void setOverallScore(double overallScore) { this.overallScore = overallScore; }

    private java.util.UUID sessionId;
    public java.util.UUID getSessionId() { return sessionId; }
    public void setSessionId(java.util.UUID sessionId) { this.sessionId = sessionId; }

    private String message;
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    private boolean isValid;
    public boolean getValid() { return isValid; }
    public void setValid(boolean isValid) { this.isValid = isValid; }

    // Backwards/forwards-compatible aliases
    public String getProcessingStatus() { return validationStatus; }
    public void setProcessingStatus(String processingStatus) { this.validationStatus = processingStatus; }

    public double getConfidenceScore() { return overallScore; }
    public void setConfidenceScore(double confidenceScore) { this.overallScore = confidenceScore; }

    public boolean isValid() { return isValid; }
}
