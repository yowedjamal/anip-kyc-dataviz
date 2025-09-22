package com.anip.kyc.dto;

public class LivenessTestResult {
    private boolean live;
    private double confidenceScore;
    private double antiSpoofingScore;
    private int qualityChecksPassed;
    private int qualityChecksTotal;
    private byte[] biometricTemplate;

    public boolean isLive() { return live; }
    public void setLive(boolean live) { this.live = live; }
    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
    public double getAntiSpoofingScore() { return antiSpoofingScore; }
    public void setAntiSpoofingScore(double antiSpoofingScore) { this.antiSpoofingScore = antiSpoofingScore; }
    public int getQualityChecksPassed() { return qualityChecksPassed; }
    public void setQualityChecksPassed(int qualityChecksPassed) { this.qualityChecksPassed = qualityChecksPassed; }
    public int getQualityChecksTotal() { return qualityChecksTotal; }
    public void setQualityChecksTotal(int qualityChecksTotal) { this.qualityChecksTotal = qualityChecksTotal; }
    public byte[] getBiometricTemplate() { return biometricTemplate; }
    public void setBiometricTemplate(byte[] biometricTemplate) { this.biometricTemplate = biometricTemplate; }
}
