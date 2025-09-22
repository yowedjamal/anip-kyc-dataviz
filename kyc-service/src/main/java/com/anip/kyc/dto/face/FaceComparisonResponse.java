package com.anip.kyc.dto.face;

public class FaceComparisonResponse {
    private double similarityScore;
    private boolean match;

    public double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(double similarityScore) { this.similarityScore = similarityScore; }
    public boolean isMatch() { return match; }
    public void setMatch(boolean match) { this.match = match; }

    // Alias for new model naming
    public double getMatchScore() { return similarityScore; }
    public void setMatchScore(double matchScore) { this.similarityScore = matchScore; }
}
