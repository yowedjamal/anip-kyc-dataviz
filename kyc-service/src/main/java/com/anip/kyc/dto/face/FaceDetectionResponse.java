package com.anip.kyc.dto.face;

import java.util.List;

public class FaceDetectionResponse {
    private List<DetectedFace> detectedFaces;
    private double maxConfidence;

    public static FaceDetectionResponse of(List<DetectedFace> faces, double maxConfidence) {
        FaceDetectionResponse r = new FaceDetectionResponse();
        r.detectedFaces = faces;
        r.maxConfidence = maxConfidence;
        return r;
    }

    public List<DetectedFace> getDetectedFaces() { return detectedFaces; }
    public double getMaxConfidence() { return maxConfidence; }

    public static class DetectedFace {
        private int x, y, width, height;
        private double confidence;
        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double c) { confidence = c; }
    }
}
