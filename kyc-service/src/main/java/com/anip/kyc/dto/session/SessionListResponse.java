package com.anip.kyc.dto.session;

import java.util.List;

public class SessionListResponse {
    private List<SessionSummary> sessions;

    public static class SessionSummary {
        private String sessionId;
        private String status;
        private String userId;
        public String getSessionId(){return sessionId;} public void setSessionId(String s){this.sessionId=s;}
        public String getStatus(){return status;} public void setStatus(String s){this.status=s;}
        public String getUserId(){return userId;} public void setUserId(String u){this.userId=u;}
    }

    public List<SessionSummary> getSessions(){return sessions;} public void setSessions(List<SessionSummary> s){this.sessions=s;}
}
