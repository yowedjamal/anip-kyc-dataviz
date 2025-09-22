package com.anip.kyc.dto.face;

import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

public class FaceDetectionRequest {
    private MultipartFile image;
    private UUID sessionId;
    private double confidenceThreshold;
    private boolean detectMultiple;
    private String userId;
    private String clientIp;

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final FaceDetectionRequest r = new FaceDetectionRequest();
        public Builder image(MultipartFile image) { r.image = image; return this; }
        public Builder sessionId(UUID id) { r.sessionId = id; return this; }
        public Builder confidenceThreshold(double v) { r.confidenceThreshold = v; return this; }
        public Builder detectMultiple(boolean b) { r.detectMultiple = b; return this; }
        public Builder userId(String u) { r.userId = u; return this; }
        public Builder clientIp(String ip) { r.clientIp = ip; return this; }
        public FaceDetectionRequest build() { return r; }
    }

    // getters
    public MultipartFile getImage() { return image; }
    public UUID getSessionId() { return sessionId; }
    public double getConfidenceThreshold() { return confidenceThreshold; }
    public boolean isDetectMultiple() { return detectMultiple; }
    public String getUserId() { return userId; }
    public String getClientIp() { return clientIp; }
}
