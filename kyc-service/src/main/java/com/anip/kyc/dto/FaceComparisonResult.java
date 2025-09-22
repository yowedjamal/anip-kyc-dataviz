package com.anip.kyc.dto;

import java.awt.Point;
import java.util.List;

public class FaceComparisonResult {
    private double similarityScore;
    private double confidenceLevel;
    private double qualityScore;
    private double antiSpoofingScore;
    private List<Point> faceLandmarks;

    public double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(double similarityScore) { this.similarityScore = similarityScore; }
    // Alias pour compatibilité avec le modèle qui utilise matchScore
    public double getMatchScore() { return similarityScore; }
    public void setMatchScore(double matchScore) { this.similarityScore = matchScore; }
    public double getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(double confidenceLevel) { this.confidenceLevel = confidenceLevel; }
    public double getQualityScore() { return qualityScore; }
    public void setQualityScore(double qualityScore) { this.qualityScore = qualityScore; }
    public double getAntiSpoofingScore() { return antiSpoofingScore; }
    public void setAntiSpoofingScore(double antiSpoofingScore) { this.antiSpoofingScore = antiSpoofingScore; }
    public List<Point> getFaceLandmarks() { return faceLandmarks; }
    public void setFaceLandmarks(List<Point> faceLandmarks) { this.faceLandmarks = faceLandmarks; }
}
