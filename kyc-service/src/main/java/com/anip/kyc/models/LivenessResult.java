package com.anip.kyc.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité LivenessResult pour stocker les résultats de détection de vivacité
 * 
 * Champs chiffrés selon constitution.md:
 * - video_path (chemin vers la vidéo de test)
 * - challenge_metadata (détails du défi de vivacité)
 * - analysis_details (résultats détaillés de l'analyse)
 */
@Entity
@Table(name = "liveness_results", indexes = {
    @Index(name = "idx_liveness_session_id", columnList = "session_id"),
    @Index(name = "idx_liveness_is_live", columnList = "is_live"),
    @Index(name = "idx_liveness_score", columnList = "liveness_score"),
    @Index(name = "idx_liveness_created", columnList = "created_at"),
    @Index(name = "idx_liveness_challenge", columnList = "challenge_type")
})
public class LivenessResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "liveness_id", updatable = false, nullable = false)
    private UUID livenessId;

    @NotNull
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @NotNull
    @Column(name = "video_path", nullable = false, length = 500)
    private String videoPath; // Chiffré - chemin vers MinIO

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_type", nullable = false, length = 50)
    private ChallengeType challengeType;

    @NotNull
    @Column(name = "is_live", nullable = false)
    private Boolean isLive;

    @NotNull
    @Column(name = "liveness_score", nullable = false)
    private Double livenessScore;

    @NotNull
    @Column(name = "confidence_level", nullable = false)
    private Double confidenceLevel;

    @Lob
    @Column(name = "challenge_metadata", columnDefinition = "JSONB")
    private String challengeMetadata; // Chiffré - détails du défi

    @Lob
    @Column(name = "analysis_details", columnDefinition = "JSONB")
    private String analysisDetails; // Chiffré - résultats détaillés

    @Column(name = "video_duration_seconds")
    private Integer videoDurationSeconds;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "detection_algorithm", nullable = false, length = 50)
    private DetectionAlgorithm detectionAlgorithm;

    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private KycSession kycSession;

    // Constructeurs
    public LivenessResult() {}

    public LivenessResult(UUID sessionId, String videoPath, ChallengeType challengeType,
                         Boolean isLive, Double livenessScore, Double confidenceLevel,
                         DetectionAlgorithm algorithm) {
        this.sessionId = sessionId;
        this.videoPath = videoPath;
        this.challengeType = challengeType;
        this.isLive = isLive;
        this.livenessScore = livenessScore;
        this.confidenceLevel = confidenceLevel;
        this.detectionAlgorithm = algorithm;
    }

    // Enums
    public enum ChallengeType {
        BLINK_DETECTION,        // Détection de clignement
        HEAD_MOVEMENT,          // Mouvement de tête
        SMILE_DETECTION,        // Détection de sourire
        EYE_TRACKING,          // Suivi oculaire
        VOICE_CHALLENGE,       // Défi vocal
        RANDOM_GESTURE,        // Geste aléatoire
        MULTI_CHALLENGE        // Combinaison de défis
    }

    public enum DetectionAlgorithm {
        OPENCV_CASCADE,        // OpenCV avec classificateurs Haar
        DLIB_LANDMARKS,        // Dlib pour points de repère faciaux
        MEDIAPIPE,            // MediaPipe de Google
        FACE_MESH,            // Maillage facial 3D
        CUSTOM_CNN,           // Réseau neuronal personnalisé
        ENSEMBLE_METHOD       // Méthode d'ensemble
    }

    // Getters et Setters
    public UUID getLivenessId() {
        return livenessId;
    }

    public void setLivenessId(UUID livenessId) {
        this.livenessId = livenessId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public ChallengeType getChallengeType() {
        return challengeType;
    }

    public void setChallengeType(ChallengeType challengeType) {
        this.challengeType = challengeType;
    }

    public Boolean getIsLive() {
        return isLive;
    }

    public void setIsLive(Boolean isLive) {
        this.isLive = isLive;
    }

    public Double getLivenessScore() {
        return livenessScore;
    }

    public void setLivenessScore(Double livenessScore) {
        this.livenessScore = livenessScore;
    }

    public Double getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(Double confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public String getChallengeMetadata() {
        return challengeMetadata;
    }

    public void setChallengeMetadata(String challengeMetadata) {
        this.challengeMetadata = challengeMetadata;
    }

    public String getAnalysisDetails() {
        return analysisDetails;
    }

    public void setAnalysisDetails(String analysisDetails) {
        this.analysisDetails = analysisDetails;
    }

    public Integer getVideoDurationSeconds() {
        return videoDurationSeconds;
    }

    public void setVideoDurationSeconds(Integer videoDurationSeconds) {
        this.videoDurationSeconds = videoDurationSeconds;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public DetectionAlgorithm getDetectionAlgorithm() {
        return detectionAlgorithm;
    }

    public void setDetectionAlgorithm(DetectionAlgorithm detectionAlgorithm) {
        this.detectionAlgorithm = detectionAlgorithm;
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

    // Méthodes utilitaires
    public boolean isHighConfidenceLive() {
        return isLive && confidenceLevel >= 0.8 && livenessScore >= 0.7;
    }

    public boolean isLowConfidenceLive() {
        return isLive && (confidenceLevel < 0.6 || livenessScore < 0.5);
    }

    public String getLivenessQuality() {
        if (!isLive) {
            return "NOT_LIVE";
        }
        
        if (isHighConfidenceLive()) {
            return "HIGH_CONFIDENCE";
        } else if (isLowConfidenceLive()) {
            return "LOW_CONFIDENCE";
        } else {
            return "MEDIUM_CONFIDENCE";
        }
    }

    public boolean requiresManualReview() {
        // Révision manuelle si:
        // - Vivacité détectée avec faible confiance
        // - Score proche du seuil de décision
        // - Vidéo trop courte ou trop longue
        // - Temps de traitement anormalement long
        return isLowConfidenceLive() ||
               (livenessScore >= 0.4 && livenessScore <= 0.6) ||
               (videoDurationSeconds != null && (videoDurationSeconds < 2 || videoDurationSeconds > 10)) ||
               (processingTimeMs != null && processingTimeMs > 15000);
    }

    public boolean isValidVideoDuration() {
        return videoDurationSeconds != null && 
               videoDurationSeconds >= 2 && 
               videoDurationSeconds <= 30;
    }

    public boolean isProcessingTimeAcceptable() {
        return processingTimeMs != null && processingTimeMs <= 10000; // 10 secondes max
    }

    @Override
    public String toString() {
        return "LivenessResult{" +
                "livenessId=" + livenessId +
                ", sessionId=" + sessionId +
                ", challengeType=" + challengeType +
                ", isLive=" + isLive +
                ", livenessScore=" + livenessScore +
                ", confidenceLevel=" + confidenceLevel +
                ", detectionAlgorithm=" + detectionAlgorithm +
                ", createdAt=" + createdAt +
                '}';
    }
}