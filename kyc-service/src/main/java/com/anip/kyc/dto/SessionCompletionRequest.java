package com.anip.kyc.dto;

public class SessionCompletionRequest {
    private String completedBy;
    private String notes;

    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
