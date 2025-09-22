package com.anip.kyc.dto.session;

import org.springframework.data.domain.Pageable;

public class SessionListRequest {
    private String status;
    private String sessionType;
    private String startDate;
    private String endDate;
    private String userId;
    private String requestingUserId;
    private Pageable pageable;

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final SessionListRequest r = new SessionListRequest();
        public Builder status(String s) { r.status = s; return this; }
        public Builder sessionType(String s) { r.sessionType = s; return this; }
        public Builder startDate(String s) { r.startDate = s; return this; }
        public Builder endDate(String s) { r.endDate = s; return this; }
        public Builder userId(String s) { r.userId = s; return this; }
        public Builder requestingUserId(String s) { r.requestingUserId = s; return this; }
        public Builder pageable(Pageable p) { r.pageable = p; return this; }
        public SessionListRequest build() { return r; }
    }

    // getters/setters omitted for brevity
    public String getStatus(){return status;} public String getSessionType(){return sessionType;}
    public String getStartDate(){return startDate;} public String getEndDate(){return endDate;}
    public String getUserId(){return userId;} public String getRequestingUserId(){return requestingUserId;}
    public Pageable getPageable(){return pageable;}
}
