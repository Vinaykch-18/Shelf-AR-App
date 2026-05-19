package com.salesdairy.shelfarapp.audit;

import com.salesdairy.shelfarapp.models.Shelf;

import java.util.List;

public final class AuditSessionProgress {
    private long sessionId;
    private int totalShelves;
    private int doneShelves;
    private int currentIndex;

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public int getTotalShelves() {
        return totalShelves;
    }

    public void setTotalShelves(int totalShelves) {
        this.totalShelves = totalShelves;
    }

    public int getDoneShelves() {
        return doneShelves;
    }

    public void setDoneShelves(int doneShelves) {
        this.doneShelves = doneShelves;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    public int getPendingShelves() {
        return Math.max(0, totalShelves - doneShelves);
    }

    public String getHeaderLine() {
        if (totalShelves <= 0) {
            return "No shelves ready";
        }
        return doneShelves + " done • " + getPendingShelves() + " left";
    }

    public String getShelfLine() {
        if (totalShelves <= 0 || currentIndex <= 0) {
            return "";
        }
        return "Shelf " + currentIndex + " of " + totalShelves;
    }

    public static AuditSessionProgress from(long sessionId, List<Shelf> shelves, int currentShelfId) {
        AuditSessionProgress progress = new AuditSessionProgress();
        progress.setSessionId(sessionId);
        progress.setTotalShelves(shelves == null ? 0 : shelves.size());
        int done = 0;
        int index = 0;
        if (shelves != null) {
            for (int i = 0; i < shelves.size(); i++) {
                Shelf shelf = shelves.get(i);
                if (shelf != null && "AUDITED".equalsIgnoreCase(shelf.getAuditStatus())) {
                    done++;
                }
                if (shelf != null && shelf.getId() == currentShelfId) {
                    index = i + 1;
                }
            }
        }
        progress.setDoneShelves(done);
        progress.setCurrentIndex(index);
        return progress;
    }
}
