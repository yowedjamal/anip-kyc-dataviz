package com.anip.kyc.dto.face;

public class FaceRecognitionStatsResponse {
    private long totalDetections;
    private long totalLivenessPassed;

    public long getTotalDetections() { return totalDetections; }
    public void setTotalDetections(long totalDetections) { this.totalDetections = totalDetections; }
    public long getTotalLivenessPassed() { return totalLivenessPassed; }
    public void setTotalLivenessPassed(long totalLivenessPassed) { this.totalLivenessPassed = totalLivenessPassed; }
}
