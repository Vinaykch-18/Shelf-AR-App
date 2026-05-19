package com.salesdairy.shelfarapp.models;

public class Shelf {
    private int id;
    private int outletId;
    private int storeReferenceId;
    private String routeLabel;
    private int routeOrder;
    private long nearestCheckpointId;
    private String shelfName;
    private String imagePath;

    private float anchorX;
    private float anchorY;
    private float anchorZ;
    private float rotX;
    private float rotY;
    private float rotZ;
    private float rotW;

    private float cameraX;
    private float cameraY;
    private float cameraZ;
    private float cameraRotX;
    private float cameraRotY;
    private float cameraRotZ;
    private float cameraRotW;

    private String createdAt;
    private String cloudAnchorId;
    private String cloudAnchorStatus;
    private String cloudAnchorError;
    private long cloudAnchorHostedAt;
    private int cloudAnchorTtlDays;
    private String guideAnchorBundle;

    private String auditStatus;
    private String auditDoneAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getOutletId() { return outletId; }
    public void setOutletId(int outletId) { this.outletId = outletId; }
    public int getStoreReferenceId() { return storeReferenceId; }
    public void setStoreReferenceId(int storeReferenceId) { this.storeReferenceId = storeReferenceId; }
    public String getRouteLabel() { return routeLabel; }
    public void setRouteLabel(String routeLabel) { this.routeLabel = routeLabel; }
    public int getRouteOrder() { return routeOrder; }
    public void setRouteOrder(int routeOrder) { this.routeOrder = routeOrder; }
    public long getNearestCheckpointId() { return nearestCheckpointId; }
    public void setNearestCheckpointId(long nearestCheckpointId) { this.nearestCheckpointId = nearestCheckpointId; }
    public String getShelfName() { return shelfName; }
    public void setShelfName(String shelfName) { this.shelfName = shelfName; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public float getAnchorX() { return anchorX; }
    public void setAnchorX(float anchorX) { this.anchorX = anchorX; }
    public float getAnchorY() { return anchorY; }
    public void setAnchorY(float anchorY) { this.anchorY = anchorY; }
    public float getAnchorZ() { return anchorZ; }
    public void setAnchorZ(float anchorZ) { this.anchorZ = anchorZ; }
    public float getRotX() { return rotX; }
    public void setRotX(float rotX) { this.rotX = rotX; }
    public float getRotY() { return rotY; }
    public void setRotY(float rotY) { this.rotY = rotY; }
    public float getRotZ() { return rotZ; }
    public void setRotZ(float rotZ) { this.rotZ = rotZ; }
    public float getRotW() { return rotW; }
    public void setRotW(float rotW) { this.rotW = rotW; }
    public float getCameraX() { return cameraX; }
    public void setCameraX(float cameraX) { this.cameraX = cameraX; }
    public float getCameraY() { return cameraY; }
    public void setCameraY(float cameraY) { this.cameraY = cameraY; }
    public float getCameraZ() { return cameraZ; }
    public void setCameraZ(float cameraZ) { this.cameraZ = cameraZ; }
    public float getCameraRotX() { return cameraRotX; }
    public void setCameraRotX(float cameraRotX) { this.cameraRotX = cameraRotX; }
    public float getCameraRotY() { return cameraRotY; }
    public void setCameraRotY(float cameraRotY) { this.cameraRotY = cameraRotY; }
    public float getCameraRotZ() { return cameraRotZ; }
    public void setCameraRotZ(float cameraRotZ) { this.cameraRotZ = cameraRotZ; }
    public float getCameraRotW() { return cameraRotW; }
    public void setCameraRotW(float cameraRotW) { this.cameraRotW = cameraRotW; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
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
    public String getGuideAnchorBundle() { return guideAnchorBundle; }
    public void setGuideAnchorBundle(String guideAnchorBundle) { this.guideAnchorBundle = guideAnchorBundle; }
    public String getAuditStatus() { return auditStatus; }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; }
    public String getAuditDoneAt() { return auditDoneAt; }
    public void setAuditDoneAt(String auditDoneAt) { this.auditDoneAt = auditDoneAt; }
}
