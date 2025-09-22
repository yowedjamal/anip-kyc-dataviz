package com.anip.kyc.dto.face;

import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

public class BiometricExtractionRequest {
    private MultipartFile image;
    private UUID sessionId;
    private double minQuality;
    private String userId;

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final BiometricExtractionRequest r = new BiometricExtractionRequest();
        public Builder image(MultipartFile i) { r.image = i; return this; }
        public Builder sessionId(UUID id) { r.sessionId = id; return this; }
        public Builder minQuality(double q) { r.minQuality = q; return this; }
        public Builder userId(String u) { r.userId = u; return this; }
        public BiometricExtractionRequest build() { return r; }
    }

    public MultipartFile getImage() { return image; }
    public UUID getSessionId() { return sessionId; }
    public double getMinQuality() { return minQuality; }
    public String getUserId() { return userId; }
}
