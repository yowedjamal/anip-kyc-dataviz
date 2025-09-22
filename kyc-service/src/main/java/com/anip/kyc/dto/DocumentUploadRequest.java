package com.anip.kyc.dto;

import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

public class DocumentUploadRequest {
    private UUID sessionId;
    private MultipartFile file;
    private String documentType;
    private String uploadedBy;

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public MultipartFile getFile() { return file; }
    public void setFile(MultipartFile file) { this.file = file; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
}
