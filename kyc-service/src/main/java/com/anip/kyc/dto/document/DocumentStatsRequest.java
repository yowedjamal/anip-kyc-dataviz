package com.anip.kyc.dto.document;

import java.util.UUID;

public class DocumentStatsRequest {
    private UUID sessionId;
    private String startDate;
    private String endDate;
    private String requestingUserId;

    public static Builder builder(){ return new Builder(); }
    public static class Builder{ private final DocumentStatsRequest r = new DocumentStatsRequest();
        public Builder sessionId(UUID s){ r.sessionId = s; return this; }
        public Builder startDate(String s){ r.startDate = s; return this; }
        public Builder endDate(String e){ r.endDate = e; return this; }
        public Builder requestingUserId(String u){ r.requestingUserId = u; return this; }
        public DocumentStatsRequest build(){ return r; }
    }

    public UUID getSessionId(){ return sessionId; }
    public String getStartDate(){ return startDate; }
    public String getEndDate(){ return endDate; }
    public String getRequestingUserId(){ return requestingUserId; }
}
