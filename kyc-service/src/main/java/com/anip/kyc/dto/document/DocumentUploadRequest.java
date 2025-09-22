package com.anip.kyc.dto.document;

import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

public class DocumentUploadRequest {
    private MultipartFile file;
    private String documentType;
    private UUID sessionId;
    private String processingOptions;
    private String userId;
    private String clientIp;
    private String userAgent;

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final DocumentUploadRequest r = new DocumentUploadRequest();
        public Builder file(MultipartFile f){ r.file = f; return this; }
        public Builder documentType(String t){ r.documentType = t; return this; }
        public Builder sessionId(UUID s){ r.sessionId = s; return this; }
        public Builder processingOptions(String o){ r.processingOptions = o; return this; }
        public Builder userId(String u){ r.userId = u; return this; }
        public Builder clientIp(String ip){ r.clientIp = ip; return this; }
        public Builder userAgent(String ua){ r.userAgent = ua; return this; }
        public DocumentUploadRequest build(){ return r; }
    }

    // getters
    public MultipartFile getFile(){ return file; }
    public String getDocumentType(){ return documentType; }
    public UUID getSessionId(){ return sessionId; }
    public String getProcessingOptions(){ return processingOptions; }
    public String getUserId(){ return userId; }
    public String getClientIp(){ return clientIp; }
    public String getUserAgent(){ return userAgent; }
}
