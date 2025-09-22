package com.anip.kyc.dto.session;

public class SessionStatsRequest {
    private String startDate;
    private String endDate;
    private String granularity;
    private String requestingUserId;

    public static Builder builder(){return new Builder();}
    public static class Builder{private final SessionStatsRequest r=new SessionStatsRequest();
        public Builder startDate(String s){r.startDate=s;return this;} public Builder endDate(String s){r.endDate=s;return this;}
        public Builder granularity(String g){r.granularity=g;return this;} public Builder requestingUserId(String u){r.requestingUserId=u;return this;}
        public SessionStatsRequest build(){return r;}}
    public String getStartDate(){return startDate;} public String getEndDate(){return endDate;} public String getGranularity(){return granularity;} public String getRequestingUserId(){return requestingUserId;}
}
