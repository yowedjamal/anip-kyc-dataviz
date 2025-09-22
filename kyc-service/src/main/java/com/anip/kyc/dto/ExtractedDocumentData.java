package com.anip.kyc.dto;

import java.util.HashMap;
import java.util.Map;

public class ExtractedDocumentData {
    private Map<String,String> fields = new HashMap<>();

    private String rawText;
    private com.anip.kyc.models.Document.DocumentType documentType;
    private double confidenceScore = 0.0;

    public Map<String,String> getExtractedFields(){ return fields; }
    public void setExtractedFields(Map<String, Object> map) {
        // Convert object values to string to keep DTO simple
        this.fields.clear();
        if (map != null) {
            for (Map.Entry<String,Object> e : map.entrySet()) {
                this.fields.put(e.getKey(), e.getValue() == null ? null : e.getValue().toString());
            }
        }
    }

    public void put(String k, String v){ fields.put(k,v); }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public com.anip.kyc.models.Document.DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(com.anip.kyc.models.Document.DocumentType documentType) { this.documentType = documentType; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String toJson() {
        // Lightweight JSON conversion to avoid adding dependencies; used for storage only
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"rawText\":").append(escapeJson(rawText)).append(',');
        sb.append("\"confidenceScore\":").append(confidenceScore).append(',');
        sb.append("\"fields\":{");
        boolean first = true;
        for (Map.Entry<String,String> e : fields.entrySet()) {
            if (!first) sb.append(',');
            sb.append(escapeJson(e.getKey())).append(':').append(escapeJson(e.getValue()));
            first = false;
        }
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "null";
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }
}
