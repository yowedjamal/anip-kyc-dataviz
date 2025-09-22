package com.anip.kyc.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SessionCreationRequest {

    @NotNull
    @Size(max = 255)
    private String userId;

    private String sessionType;
    private String clientIp;
    private String userAgent;
    private String deviceFingerprint;
    private String referrer;
    private String requestId;

    // Getters / Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSessionType() { return sessionType; }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
