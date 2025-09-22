package com.anip.kyc.dto.document;

import java.util.List;

public class DocumentListResponse {
    private List<DocumentSummary> documents;

    public List<DocumentSummary> getDocuments(){ return documents; }
    public void setDocuments(List<DocumentSummary> d){ this.documents = d; }
}
