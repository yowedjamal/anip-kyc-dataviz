package com.anip.kyc.dto.session;

import java.util.UUID;

public class SessionReportRequest {
    private UUID sessionId;
    private String format;
    private boolean includeRawData;
    private String requestingUserId;

    public static Builder builder(){return new Builder();}
    public static class Builder{private final SessionReportRequest r=new SessionReportRequest();
        public Builder sessionId(UUID id){r.sessionId=id;return this;} public Builder format(String f){r.format=f;return this;}
        public Builder includeRawData(boolean b){r.includeRawData=b;return this;} public Builder requestingUserId(String u){r.requestingUserId=u;return this;}
        public SessionReportRequest build(){return r;}}

    public UUID getSessionId(){return sessionId;} public String getFormat(){return format;} public boolean isIncludeRawData(){return includeRawData;} public String getRequestingUserId(){return requestingUserId;}
}
