package com.anip.kyc.dto.face;

import org.springframework.web.multipart.MultipartFile;

public class FaceQualityRequest {
    private MultipartFile image;
    private boolean detailedAnalysis;
    private String userId;

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final FaceQualityRequest r = new FaceQualityRequest();
        public Builder image(MultipartFile i) { r.image = i; return this; }
        public Builder detailedAnalysis(boolean b) { r.detailedAnalysis = b; return this; }
        public Builder userId(String u) { r.userId = u; return this; }
        public FaceQualityRequest build() { return r; }
    }

    public MultipartFile getImage() { return image; }
    public boolean isDetailedAnalysis() { return detailedAnalysis; }
    public String getUserId() { return userId; }
}
