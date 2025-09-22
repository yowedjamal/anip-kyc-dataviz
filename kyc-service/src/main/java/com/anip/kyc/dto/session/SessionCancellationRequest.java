package com.anip.kyc.dto.session;

import java.util.UUID;

public class SessionCancellationRequest {
    private UUID sessionId;
    private String reason;
    private String cancellingUserId;

    public static Builder builder(){return new Builder();}
    public static class Builder{private final SessionCancellationRequest r=new SessionCancellationRequest();
        public Builder sessionId(UUID id){r.sessionId=id;return this;} public Builder reason(String s){r.reason=s;return this;} public Builder cancellingUserId(String u){r.cancellingUserId=u;return this;} public SessionCancellationRequest build(){return r;}}

    public UUID getSessionId(){return sessionId;} public String getReason(){return reason;} public String getCancellingUserId(){return cancellingUserId;}
}
