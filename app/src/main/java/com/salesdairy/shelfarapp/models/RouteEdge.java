package com.salesdairy.shelfarapp.models;

public class RouteEdge {
    private long id;
    private int storeReferenceId;
    private long fromCheckpointId;
    private long toCheckpointId;
    private float distanceMeters;
    private String edgeKind;
    private String createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getStoreReferenceId() { return storeReferenceId; }
    public void setStoreReferenceId(int storeReferenceId) { this.storeReferenceId = storeReferenceId; }
    public long getFromCheckpointId() { return fromCheckpointId; }
    public void setFromCheckpointId(long fromCheckpointId) { this.fromCheckpointId = fromCheckpointId; }
    public long getToCheckpointId() { return toCheckpointId; }
    public void setToCheckpointId(long toCheckpointId) { this.toCheckpointId = toCheckpointId; }
    public float getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(float distanceMeters) { this.distanceMeters = distanceMeters; }
    public String getEdgeKind() { return edgeKind; }
    public void setEdgeKind(String edgeKind) { this.edgeKind = edgeKind; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
