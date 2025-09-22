package com.anip.kyc.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SessionStatusResponse {
    private java.util.UUID sessionId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
    private boolean isExpired;
    private double confidenceScore;
    private int progress;
    private List<Object> steps;
    private String failureReason;


    // Getters/Setters
    public java.util.UUID getSessionId() { return sessionId; }
    public void setSessionId(java.util.UUID sessionId) { this.sessionId = sessionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public boolean getIsExpired() { return isExpired; }
    public void setIsExpired(boolean isExpired) { this.isExpired = isExpired; }
    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public List<Object> getSteps() { return steps; }
    public void setSteps(List<Object> steps) { this.steps = steps; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getFailureReason() { return failureReason; }
}
