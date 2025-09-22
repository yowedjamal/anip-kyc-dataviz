package com.anip.kyc.dto.face;

public class BiometricEncodingResponse {
    private double qualityScore;
    private int encodingDimensions;
    private byte[] encodedTemplate;

    public double getQualityScore() { return qualityScore; }
    public void setQualityScore(double qualityScore) { this.qualityScore = qualityScore; }
    public int getEncodingDimensions() { return encodingDimensions; }
    public void setEncodingDimensions(int encodingDimensions) { this.encodingDimensions = encodingDimensions; }
    public byte[] getEncodedTemplate() { return encodedTemplate; }
    public void setEncodedTemplate(byte[] encodedTemplate) { this.encodedTemplate = encodedTemplate; }
}
