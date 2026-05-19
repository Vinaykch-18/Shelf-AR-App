package com.salesdairy.shelfarapp.models;

public class RouteCheckpoint {
    private long id;
    private int outletId;
    private int storeReferenceId;
    private int sequence;
    private String routeLabel;
    private String kind;
    private float anchorX;
    private float anchorY;
    private float anchorZ;
    private float yawDegrees;
    private float captureConfidence;
    private int sceneQualityScore;
    private String createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getOutletId() { return outletId; }
    public void setOutletId(int outletId) { this.outletId = outletId; }
    public int getStoreReferenceId() { return storeReferenceId; }
    public void setStoreReferenceId(int storeReferenceId) { this.storeReferenceId = storeReferenceId; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public String getRouteLabel() { return routeLabel; }
    public void setRouteLabel(String routeLabel) { this.routeLabel = routeLabel; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public float getAnchorX() { return anchorX; }
    public void setAnchorX(float anchorX) { this.anchorX = anchorX; }
    public float getAnchorY() { return anchorY; }
    public void setAnchorY(float anchorY) { this.anchorY = anchorY; }
    public float getAnchorZ() { return anchorZ; }
    public void setAnchorZ(float anchorZ) { this.anchorZ = anchorZ; }
    public float getYawDegrees() { return yawDegrees; }
    public void setYawDegrees(float yawDegrees) { this.yawDegrees = yawDegrees; }
    public float getCaptureConfidence() { return captureConfidence; }
    public void setCaptureConfidence(float captureConfidence) { this.captureConfidence = captureConfidence; }
    public int getSceneQualityScore() { return sceneQualityScore; }
    public void setSceneQualityScore(int sceneQualityScore) { this.sceneQualityScore = sceneQualityScore; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
