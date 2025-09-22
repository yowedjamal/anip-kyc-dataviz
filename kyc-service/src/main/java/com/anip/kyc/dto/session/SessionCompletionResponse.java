package com.anip.kyc.dto.session;

public class SessionCompletionResponse {
    private String finalStatus;
    private double finalScore;

    public String getFinalStatus() { return finalStatus; }
    public void setFinalStatus(String finalStatus) { this.finalStatus = finalStatus; }
    public double getFinalScore() { return finalScore; }
    public void setFinalScore(double finalScore) { this.finalScore = finalScore; }
}
