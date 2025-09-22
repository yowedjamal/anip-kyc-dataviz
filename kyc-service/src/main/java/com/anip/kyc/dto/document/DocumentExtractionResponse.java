package com.anip.kyc.dto.document;

import java.util.Map;
import java.util.List;

public class DocumentExtractionResponse {
    private Map<String,String> extractedFields;

    public Map<String,String> getExtractedFields(){ return extractedFields; }
    public void setExtractedFields(Map<String,String> f){ this.extractedFields = f; }
}
