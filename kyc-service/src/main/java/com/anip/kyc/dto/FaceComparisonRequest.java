package com.anip.kyc.dto;

import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

public class FaceComparisonRequest {
    private UUID documentId;
    private MultipartFile liveCaptureImage;

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public MultipartFile getLiveCaptureImage() { return liveCaptureImage; }
    public void setLiveCaptureImage(MultipartFile liveCaptureImage) { this.liveCaptureImage = liveCaptureImage; }
}
