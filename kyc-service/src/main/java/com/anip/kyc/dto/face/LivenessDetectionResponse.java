package com.anip.kyc.dto.face;

public class LivenessDetectionResponse {
    private boolean alive;
    private double livenessScore;

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public double getLivenessScore() { return livenessScore; }
    public void setLivenessScore(double livenessScore) { this.livenessScore = livenessScore; }
}
