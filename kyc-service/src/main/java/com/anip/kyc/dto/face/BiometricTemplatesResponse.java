package com.anip.kyc.dto.face;

import java.util.List;

public class BiometricTemplatesResponse {
    private List<EncryptedTemplate> templates;

    public List<EncryptedTemplate> getTemplates() { return templates; }
    public void setTemplates(List<EncryptedTemplate> templates) { this.templates = templates; }

    public static class EncryptedTemplate {
        private byte[] encryptedData;
        private String metadata;
        public byte[] getEncryptedData() { return encryptedData; }
        public String getMetadata() { return metadata; }
    }
}
