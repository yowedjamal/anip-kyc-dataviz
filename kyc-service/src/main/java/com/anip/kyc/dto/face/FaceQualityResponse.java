package com.anip.kyc.dto.face;

public class FaceQualityResponse {
    private double qualityScore;
    private String details;

    public double getQualityScore() { return qualityScore; }
    public void setQualityScore(double qualityScore) { this.qualityScore = qualityScore; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
