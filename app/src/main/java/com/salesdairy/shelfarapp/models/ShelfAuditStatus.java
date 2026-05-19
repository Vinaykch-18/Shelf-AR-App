package com.salesdairy.shelfarapp.models;

public class ShelfAuditStatus {
    private long sessionId;
    private long shelfAuditId;
    private String status;
    private String auditedAt;
    private int imageCount;

    public long getSessionId() { return sessionId; }
    public void setSessionId(long sessionId) { this.sessionId = sessionId; }
    public long getShelfAuditId() { return shelfAuditId; }
    public void setShelfAuditId(long shelfAuditId) { this.shelfAuditId = shelfAuditId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAuditedAt() { return auditedAt; }
    public void setAuditedAt(String auditedAt) { this.auditedAt = auditedAt; }
    public int getImageCount() { return imageCount; }
    public void setImageCount(int imageCount) { this.imageCount = imageCount; }

    public boolean isDone() {
        return isAudited();
    }

    public boolean isAudited() {
        return "AUDITED".equalsIgnoreCase(status);
    }
}
