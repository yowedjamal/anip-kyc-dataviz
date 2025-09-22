package com.anip.kyc.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité FaceMatch pour stocker les résultats de comparaison faciale
 * 
 * Champs chiffrés selon constitution.md:
 * - live_image_path (chemin vers l'image de vérification)
 * - comparison_metadata (détails techniques de la comparaison)
 */
@Entity
@Table(name = "face_matches", indexes = {
    @Index(name = "idx_face_match_session_id", columnList = "session_id"),
    @Index(name = "idx_face_match_score", columnList = "match_score"),
    @Index(name = "idx_face_match_created", columnList = "created_at"),
    @Index(name = "idx_face_match_is_match", columnList = "is_match")
})
public class FaceMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "match_id", updatable = false, nullable = false)
    private UUID matchId;

    @NotNull
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @NotNull
    @Column(name = "reference_document_id", nullable = false)
    private UUID referenceDocumentId;

    @NotNull
    @Column(name = "live_image_path", nullable = false, length = 500)
    private String liveImagePath; // Chiffré - chemin vers MinIO

    @NotNull
    @Column(name = "match_score", nullable = false)
    private Double matchScore;

    @NotNull
    @Column(name = "is_match", nullable = false)
    private Boolean isMatch;

    @NotNull
    @Column(name = "confidence_level", nullable = false)
    private Double confidenceLevel;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_algorithm", nullable = false, length = 50)
    private VerificationAlgorithm verificationAlgorithm;

    @Lob
    @Column(name = "comparison_metadata", columnDefinition = "JSONB")
    private String comparisonMetadata; // Chiffré - détails techniques

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private KycSession kycSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_document_id", insertable = false, updatable = false)
    private Document referenceDocument;

    // Constructeurs
    public FaceMatch() {}

    public FaceMatch(UUID sessionId, UUID referenceDocumentId, String liveImagePath,
                    Double matchScore, Boolean isMatch, Double confidenceLevel,
                    VerificationAlgorithm algorithm) {
        this.sessionId = sessionId;
        this.referenceDocumentId = referenceDocumentId;
        this.liveImagePath = liveImagePath;
        this.matchScore = matchScore;
        this.isMatch = isMatch;
        this.confidenceLevel = confidenceLevel;
        this.verificationAlgorithm = algorithm;
    }

    // Enums
    public enum VerificationAlgorithm {
        DEEPFACE,           // Bibliothèque DeepFace
        INSIGHTFACE,        // InsightFace pour reconnaissance faciale
        FACENET,            // FaceNet de Google
        ARCFACE,            // ArcFace algorithm
        OPENCV_DNN,         // OpenCV Deep Neural Network
        CUSTOM_ENSEMBLE     // Combinaison de plusieurs algorithmes
    }

    // Getters et Setters
    public UUID getMatchId() {
        return matchId;
    }

    public void setMatchId(UUID matchId) {
        this.matchId = matchId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getReferenceDocumentId() {
        return referenceDocumentId;
    }

    public void setReferenceDocumentId(UUID referenceDocumentId) {
        this.referenceDocumentId = referenceDocumentId;
    }

    public String getLiveImagePath() {
        return liveImagePath;
    }

    public void setLiveImagePath(String liveImagePath) {
        this.liveImagePath = liveImagePath;
    }

    public Double getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }

    public Boolean getIsMatch() {
        return isMatch;
    }

    public void setIsMatch(Boolean isMatch) {
        this.isMatch = isMatch;
    }

    public Double getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(Double confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public VerificationAlgorithm getVerificationAlgorithm() {
        return verificationAlgorithm;
    }

    public void setVerificationAlgorithm(VerificationAlgorithm verificationAlgorithm) {
        this.verificationAlgorithm = verificationAlgorithm;
    }

    public String getComparisonMetadata() {
        return comparisonMetadata;
    }

    public void setComparisonMetadata(String comparisonMetadata) {
        this.comparisonMetadata = comparisonMetadata;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public KycSession getKycSession() {
        return kycSession;
    }

    public void setKycSession(KycSession kycSession) {
        this.kycSession = kycSession;
    }

    public Document getReferenceDocument() {
        return referenceDocument;
    }

    public void setReferenceDocument(Document referenceDocument) {
        this.referenceDocument = referenceDocument;
    }

    // Méthodes utilitaires
    public boolean isHighConfidenceMatch() {
        return isMatch && confidenceLevel >= 0.8 && matchScore >= 0.7;
    }

    public boolean isLowConfidenceMatch() {
        return isMatch && (confidenceLevel < 0.6 || matchScore < 0.5);
    }

    public String getMatchQuality() {
        if (!isMatch) {
            return "NO_MATCH";
        }
        
        if (isHighConfidenceMatch()) {
            return "HIGH_CONFIDENCE";
        } else if (isLowConfidenceMatch()) {
            return "LOW_CONFIDENCE";
        } else {
            return "MEDIUM_CONFIDENCE";
        }
    }

    public boolean requiresManualReview() {
        // Révision manuelle si:
        // - Match avec faible confiance
        // - Score proche du seuil de décision
        // - Temps de traitement anormalement long
        return isLowConfidenceMatch() || 
               (matchScore >= 0.4 && matchScore <= 0.6) ||
               (processingTimeMs != null && processingTimeMs > 10000);
    }

    @Override
    public String toString() {
        return "FaceMatch{" +
                "matchId=" + matchId +
                ", sessionId=" + sessionId +
                ", matchScore=" + matchScore +
                ", isMatch=" + isMatch +
                ", confidenceLevel=" + confidenceLevel +
                ", verificationAlgorithm=" + verificationAlgorithm +
                ", createdAt=" + createdAt +
                '}';
    }
}