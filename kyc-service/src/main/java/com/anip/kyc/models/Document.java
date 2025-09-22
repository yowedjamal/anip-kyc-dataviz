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
 * Entité Document pour le stockage des documents KYC
 * 
 * Champs chiffrés selon constitution.md:
 * - file_path (chemin vers MinIO)
 * - extracted_text (contenu OCR)
 * - metadata (informations sensibles)
 */
@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_document_session_id", columnList = "session_id"),
        @Index(name = "idx_document_type", columnList = "document_type"),
        @Index(name = "idx_document_status", columnList = "processing_status"),
        @Index(name = "idx_document_created", columnList = "created_at")
})
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "document_id", updatable = false, nullable = false)
    private UUID documentId;

    @NotNull
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @NotNull
    @Size(max = 500)
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath; // Chiffré - chemin vers MinIO

    @Column(name = "file_size")
    private Long fileSize;

    @Size(max = 100)
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 30)
    private ProcessingStatus processingStatus;

    @Lob
    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText; // Chiffré - contenu OCR

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Lob
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata; // Chiffré - JSON avec infos techniques

    @Column(name = "page_number")
    private Integer pageNumber;

    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // Relation avec KycSession
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private KycSession kycSession;

    // Relation avec FaceMatch (pour documents avec visages)
    @OneToMany(mappedBy = "referenceDocument", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FaceMatch> faceMatches;

    // Constructeurs
    public Document() {
    }

    public Document(UUID sessionId, DocumentType documentType, String filePath,
            String mimeType, Long fileSize) {
        this.sessionId = sessionId;
        this.documentType = documentType;
        this.filePath = filePath;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.processingStatus = ProcessingStatus.PENDING;
    }

    // Enums
    public enum DocumentType {
        // Legacy/simple aliases kept for compatibility with older code
        ID_CARD,
        DRIVING_LICENSE,
        ID_CARD_FRONT,
        ID_CARD_BACK,
        PASSPORT,
        DRIVING_LICENSE_FRONT,
        DRIVING_LICENSE_BACK,
        BIRTH_CERTIFICATE,
        UTILITY_BILL,
        BANK_STATEMENT,
        OTHER
    }

    public enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        REJECTED
    }

    // Getters et Setters
    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
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

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public KycSession getKycSession() {
        return kycSession;
    }

    public void setKycSession(KycSession kycSession) {
        this.kycSession = kycSession;
    }

    public List<FaceMatch> getFaceMatches() {
        return faceMatches;
    }

    public void setFaceMatches(List<FaceMatch> faceMatches) {
        this.faceMatches = faceMatches;
    }

    // Méthodes utilitaires
    public void markAsProcessing() {
        this.processingStatus = ProcessingStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsCompleted(String extractedText, Double confidenceScore) {
        this.processingStatus = ProcessingStatus.COMPLETED;
        this.extractedText = extractedText;
        this.confidenceScore = confidenceScore;
        this.processedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.processingStatus = ProcessingStatus.FAILED;
        this.metadata = reason; // Stocke la raison de l'échec
        this.processedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isProcessingComplete() {
        return processingStatus == ProcessingStatus.COMPLETED;
    }

    public boolean hasExtractedText() {
        return extractedText != null && !extractedText.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "Document{" +
                "documentId=" + documentId +
                ", sessionId=" + sessionId +
                ", documentType=" + documentType +
                ", processingStatus=" + processingStatus +
                ", createdAt=" + createdAt +
                '}';
    }
}