package com.anip.kyc.dto.face;

public class FaceRecognitionStatsRequest {
    private String startDate;
    private String endDate;
    private String requestingUserId;

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final FaceRecognitionStatsRequest r = new FaceRecognitionStatsRequest();
        public Builder startDate(String s) { r.startDate = s; return this; }
        public Builder endDate(String e) { r.endDate = e; return this; }
        public Builder requestingUserId(String u) { r.requestingUserId = u; return this; }
        public FaceRecognitionStatsRequest build() { return r; }
    }

    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public String getRequestingUserId() { return requestingUserId; }
}
