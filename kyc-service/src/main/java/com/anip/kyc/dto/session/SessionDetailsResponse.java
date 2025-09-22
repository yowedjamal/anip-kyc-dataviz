package com.anip.kyc.dto.session;

import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;

public class SessionDetailsResponse {
    private UUID sessionId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> events;

    public SessionDetailsResponse() {}

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<String> getEvents() { return events; }
    public void setEvents(List<String> events) { this.events = events; }
}
