package com.anip.kyc.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entité KycSession pour orchestrer le processus KYC
 * 
 * Champs chiffrés selon constitution.md:
 * - user_id (identifiant utilisateur)
 * - metadata (informations de session)
 * - verification_results (résultats sensibles)
 */
@Entity
@Table(name = "kyc_sessions", indexes = {
        @Index(name = "idx_session_user_id", columnList = "user_id"),
        @Index(name = "idx_session_status", columnList = "session_status"),
        @Index(name = "idx_session_verification_type", columnList = "verification_type"),
        @Index(name = "idx_session_created", columnList = "created_at"),
        @Index(name = "idx_session_expires", columnList = "expires_at")
})
public class KycSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id", updatable = false, nullable = false)
    private UUID sessionId;

    @NotNull
    @Size(max = 255)
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId; // Chiffré - identifiant utilisateur

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 30)
    private VerificationType verificationType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 30)
    private SessionStatus sessionStatus;

    @Lob
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata; // Chiffré - informations de session

    @Column(name = "progress_percentage")
    private Integer progressPercentage = 0;

    @Lob
    @Column(name = "verification_results", columnDefinition = "JSONB")
    private String verificationResults; // Chiffré - résultats de vérification

    @Column(name = "risk_score")
    private Double riskScore;

    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Relations
    @OneToMany(mappedBy = "kycSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Document> documents;

    @OneToMany(mappedBy = "kycSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FaceMatch> faceMatches;

    @OneToMany(mappedBy = "kycSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LivenessResult> livenessResults;

    @Column(name = "client_ip", length = 255)
    private String clientIpEncrypted;
    
    // Constructeurs
    public KycSession() {
    }

    public KycSession(String userId, VerificationType verificationType) {
        this.userId = userId;
        this.verificationType = verificationType;
        this.sessionStatus = SessionStatus.INITIATED;
        this.progressPercentage = 0;
        // Session expire dans 24 heures par défaut
        this.expiresAt = LocalDateTime.now().plusHours(24);
    }

    // Enums
    public enum VerificationType {
        DOCUMENT_ONLY, // Vérification documents uniquement
        FACE_ONLY, // Vérification faciale uniquement
        FULL_KYC, // KYC complet (documents + face + liveness)
        ENHANCED_KYC // KYC renforcé avec vérifications additionnelles
    }

    public enum SessionStatus {
        INITIATED, // Session créée
        IN_PROGRESS, // Vérification en cours
        DOCUMENT_VERIFIED, // Documents validés
        PENDING_REVIEW, // En attente de validation manuelle
        COMPLETED, // Vérification terminée avec succès
        APPROVED, // Approuvé après révision
        REJECTED, // Rejeté
        FAILED, // Échec technique
        EXPIRED, // Session expirée
        CANCELLED // Annulée par l'utilisateur
    }

    // Enum pour le résultat de vérification
    public enum VerificationResult {
        PASSED,
        FAILED,
        PENDING,
        IN_PROGRESS
    }

    // Getters et Setters
    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public VerificationType getVerificationType() {
        return verificationType;
    }

    public void setVerificationType(VerificationType verificationType) {
        this.verificationType = verificationType;
    }

    public SessionStatus getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(SessionStatus sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Integer getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(Integer progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public String getVerificationResults() {
        return verificationResults;
    }

    public void setVerificationResults(String verificationResults) {
        this.verificationResults = verificationResults;
    }

    public VerificationResult getVerificationResult() {
        if (this.sessionStatus == SessionStatus.COMPLETED || this.sessionStatus == SessionStatus.APPROVED) {
            return VerificationResult.PASSED;
        } else if (this.sessionStatus == SessionStatus.REJECTED || this.sessionStatus == SessionStatus.FAILED) {
            return VerificationResult.FAILED;
        } else if (this.sessionStatus == SessionStatus.PENDING_REVIEW) {
            return VerificationResult.PENDING;
        } else {
            return VerificationResult.IN_PROGRESS;
        }
    }

    public String getFailureReason() {
        if (this.sessionStatus == SessionStatus.REJECTED) {
            return this.verificationResults;
        } else if (this.sessionStatus == SessionStatus.FAILED) {
            return "Technical failure during verification.";
        }
        return null;
    }

    /**
     * Définit la raison d'échec de la session et marque la session comme REJECTED.
     * Les services appellent souvent cette méthode pour enregistrer la cause métier d'un rejet.
     */
    public void setFailureReason(String reason) {
        this.verificationResults = reason;
        this.sessionStatus = SessionStatus.REJECTED;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getSuccessMessage() {
        if (this.sessionStatus == SessionStatus.APPROVED) {
            return "KYC process completed and approved.";
        } else if (this.sessionStatus == SessionStatus.COMPLETED) {
            return "KYC process completed successfully.";
        }
        return null;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    /**
     * Alias attendu par certains services pour définir un score de confiance global
     * sur la session. Mappe vers le champ riskScore.
     */
    public void setConfidenceScore(double confidenceScore) {
        this.riskScore = confidenceScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public List<Document> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Document> documents) {
        this.documents = documents;
    }

    public List<FaceMatch> getFaceMatches() {
        return faceMatches;
    }

    public void setFaceMatches(List<FaceMatch> faceMatches) {
        this.faceMatches = faceMatches;
    }

    public List<LivenessResult> getLivenessResults() {
        return livenessResults;
    }

    public void setLivenessResults(List<LivenessResult> livenessResults) {
        this.livenessResults = livenessResults;
    }

    // Méthodes utilitaires
    public void updateProgress(int percentage) {
        this.progressPercentage = Math.max(0, Math.min(100, percentage));
        this.updatedAt = LocalDateTime.now();

        if (percentage >= 100) {
            this.sessionStatus = SessionStatus.COMPLETED;
            this.completedAt = LocalDateTime.now();
        } else if (this.sessionStatus == SessionStatus.INITIATED) {
            this.sessionStatus = SessionStatus.IN_PROGRESS;
        }
    }

    public void markAsCompleted(String results, Double riskScore) {
        this.sessionStatus = SessionStatus.COMPLETED;
        this.verificationResults = results;
        this.riskScore = riskScore;
        this.progressPercentage = 100;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsRejected(String reason) {
        this.sessionStatus = SessionStatus.REJECTED;
        this.verificationResults = reason;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return !isExpired() && (sessionStatus == SessionStatus.INITIATED ||
                sessionStatus == SessionStatus.IN_PROGRESS);
    }

    /**
     * Indique si des documents peuvent être uploadés pour cette session.
     */
    public boolean canUploadDocument() {
        return isActive() && sessionStatus != SessionStatus.COMPLETED && sessionStatus != SessionStatus.CANCELLED && sessionStatus != SessionStatus.EXPIRED;
    }

    public boolean isCompleted() {
        return sessionStatus == SessionStatus.COMPLETED ||
                sessionStatus == SessionStatus.APPROVED;
    }

    public boolean requiresDocuments() {
        return verificationType == VerificationType.DOCUMENT_ONLY ||
                verificationType == VerificationType.FULL_KYC ||
                verificationType == VerificationType.ENHANCED_KYC;
    }

    public boolean requiresFaceVerification() {
        return verificationType == VerificationType.FACE_ONLY ||
                verificationType == VerificationType.FULL_KYC ||
                verificationType == VerificationType.ENHANCED_KYC;
    }

    public boolean requiresLivenessDetection() {
        return verificationType == VerificationType.FULL_KYC ||
                verificationType == VerificationType.ENHANCED_KYC;
    }

    @Override
    public String toString() {
        return "KycSession{" +
                "sessionId=" + sessionId +
                ", userId='" + userId + '\'' +
                ", verificationType=" + verificationType +
                ", sessionStatus=" + sessionStatus +
                ", progressPercentage=" + progressPercentage +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                '}';
    }

    public boolean needsManualReview() {
        return sessionStatus == SessionStatus.PENDING_REVIEW ||
               (sessionStatus == SessionStatus.COMPLETED && riskScore != null && riskScore > 0.7);
    }

    public void setSessionMetadataEncrypted(byte[] metadata) {
        if (metadata != null) {
            this.metadata = java.util.Base64.getEncoder().encodeToString(metadata);
        } else {
            this.metadata = null;
        }
    }

    public byte[] getSessionMetadataEncrypted() {
        if (this.metadata != null) {
            return java.util.Base64.getDecoder().decode(this.metadata);
        }
        return null;
    }

    public void setUserAgentHash(byte[] userAgentHash) {
        this.userAgentHash = userAgentHash;
    }

    public byte[] getUserAgentHash() {
        return this.userAgentHash;
    }

    public void setStatus(KycSession.SessionStatus status) {
        this.sessionStatus = status;
    }

    @Column(name = "user_agent_hash", length = 64)
    private byte[] userAgentHash;


    @Column(name = "user_id_hash", length = 64)
    private byte[] userIdHash;
    public void setUserIdHash(byte[] userIdHash) {
        this.userIdHash = userIdHash;
    }

    public byte[] getUserIdHash() {
        return this.userIdHash;
    }
    public void setClientIpEncrypted(byte[] clientIp) {
        if (clientIp != null) {
            this.clientIpEncrypted = java.util.Base64.getEncoder().encodeToString(clientIp);
        } else {
            this.clientIpEncrypted = null;
        }
    }

    public byte[] getClientIpEncrypted() {
        if (this.clientIpEncrypted != null) {
            return java.util.Base64.getDecoder().decode(this.clientIpEncrypted);
        }
        return null;
    }

    public boolean canBeCompleted() {
        if (this.sessionStatus == SessionStatus.COMPLETED ||
            this.sessionStatus == SessionStatus.APPROVED ||
            this.sessionStatus == SessionStatus.REJECTED ||
            this.sessionStatus == SessionStatus.FAILED ||
            this.sessionStatus == SessionStatus.EXPIRED ||
            this.sessionStatus == SessionStatus.CANCELLED) {
            return false;
        }
        if (requiresDocuments() && (documents == null || documents.isEmpty())) {
            return false;
        }
        if (requiresFaceVerification() && (faceMatches == null || faceMatches.isEmpty())) {
            return false;
        }
        if (requiresLivenessDetection() && (livenessResults == null || livenessResults.isEmpty())) {
            return false;
        }
        return true;
    }
}