package com.anip.kyc.dto.face;

import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

public class LivenessDetectionRequest {
    private MultipartFile media;
    private UUID sessionId;
    private String testType;
    private double livenessThreshold;
    private String userId;

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final LivenessDetectionRequest r = new LivenessDetectionRequest();
        public Builder media(MultipartFile m) { r.media = m; return this; }
        public Builder sessionId(UUID id) { r.sessionId = id; return this; }
        public Builder testType(String t) { r.testType = t; return this; }
        public Builder livenessThreshold(double v) { r.livenessThreshold = v; return this; }
        public Builder userId(String u) { r.userId = u; return this; }
        public LivenessDetectionRequest build() { return r; }
    }

    public MultipartFile getMedia() { return media; }
    public UUID getSessionId() { return sessionId; }
    public String getTestType() { return testType; }
    public double getLivenessThreshold() { return livenessThreshold; }
    public String getUserId() { return userId; }
}
