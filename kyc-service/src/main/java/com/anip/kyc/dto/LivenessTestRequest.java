package com.anip.kyc.dto;

public class LivenessTestRequest {
    private String livenessType;
    private byte[] imageData;
    private Object challengeResponseData;
    private Object deviceInfo;

    public String getLivenessType() { return livenessType; }
    public void setLivenessType(String livenessType) { this.livenessType = livenessType; }
    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }
    public Object getChallengeResponseData() { return challengeResponseData; }
    public void setChallengeResponseData(Object challengeResponseData) { this.challengeResponseData = challengeResponseData; }
    public Object getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(Object deviceInfo) { this.deviceInfo = deviceInfo; }
}
