package com.salesdairy.shelfarapp.models;

public class StoreReference {
    private int id;
    private int outletId;
    private String referenceScopeName;
    private String referenceName;
    private String imagePath;
    private String hint;
    private String cloudAnchorId;
    private String cloudAnchorStatus;
    private String cloudAnchorError;
    private long cloudAnchorHostedAt;
    private int cloudAnchorTtlDays;
    private String createdAt;
    private boolean active;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getOutletId() { return outletId; }
    public void setOutletId(int outletId) { this.outletId = outletId; }
    public String getReferenceScopeName() { return referenceScopeName; }
    public void setReferenceScopeName(String referenceScopeName) { this.referenceScopeName = referenceScopeName; }
    public String getReferenceName() { return referenceName; }
    public void setReferenceName(String referenceName) { this.referenceName = referenceName; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
    public String getCloudAnchorId() { return cloudAnchorId; }
    public void setCloudAnchorId(String cloudAnchorId) { this.cloudAnchorId = cloudAnchorId; }
    public String getCloudAnchorStatus() { return cloudAnchorStatus; }
    public void setCloudAnchorStatus(String cloudAnchorStatus) { this.cloudAnchorStatus = cloudAnchorStatus; }
    public String getCloudAnchorError() { return cloudAnchorError; }
    public void setCloudAnchorError(String cloudAnchorError) { this.cloudAnchorError = cloudAnchorError; }
    public long getCloudAnchorHostedAt() { return cloudAnchorHostedAt; }
    public void setCloudAnchorHostedAt(long cloudAnchorHostedAt) { this.cloudAnchorHostedAt = cloudAnchorHostedAt; }
    public int getCloudAnchorTtlDays() { return cloudAnchorTtlDays; }
    public void setCloudAnchorTtlDays(int cloudAnchorTtlDays) { this.cloudAnchorTtlDays = cloudAnchorTtlDays; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
