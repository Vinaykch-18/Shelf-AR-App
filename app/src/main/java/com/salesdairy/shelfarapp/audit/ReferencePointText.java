package com.salesdairy.shelfarapp.audit;

import android.text.TextUtils;

import com.salesdairy.shelfarapp.models.StoreReference;

public final class ReferencePointText {

    private static final String SINGLE = "store reference";
    private static final String SINGLE_TITLE = "Store reference";
    private static final String PLURAL_TITLE = "Store references";

    private ReferencePointText() {
    }

    public static String single() {
        return SINGLE;
    }

    public static String singleTitle() {
        return SINGLE_TITLE;
    }

    public static String pluralTitle() {
        return PLURAL_TITLE;
    }

    public static String displayName(StoreReference reference) {
        if (reference == null) {
            return SINGLE_TITLE;
        }
        String areaName = safe(reference.getReferenceScopeName(), "Area");
        String pointName = safe(reference.getReferenceName(), SINGLE_TITLE);
        return areaName + " • " + pointName;
    }

    public static String shortName(StoreReference reference) {
        return reference == null ? SINGLE_TITLE : safe(reference.getReferenceName(), SINGLE_TITLE);
    }

    public static String areaName(StoreReference reference) {
        return reference == null ? "Area" : safe(reference.getReferenceScopeName(), "Area");
    }

    public static String activeCardTitle(StoreReference reference) {
        return reference == null ? "No " + SINGLE + " saved yet" : displayName(reference);
    }

    public static String activeCardHint(StoreReference reference) {
        return reference == null
                ? "Save one store reference, then add shelves under it."
                : "Guided audit will start by locking this saved store reference.";
    }

    public static String lockStep(StoreReference reference) {
        return "Lock " + shortName(reference) + " first";
    }

    public static String lockHelp(StoreReference reference) {
        return "Match the saved store reference photo and wait for lock.";
    }

    public static String lockedChip() {
        return "Reference locked";
    }

    public static String savedPhotoLabel(boolean resolved) {
        return resolved ? "View saved shelf photo" : "View saved store reference photo";
    }

    public static String savedPhotoTitle(boolean resolved) {
        return resolved ? "Saved shelf photo" : "Saved store reference photo";
    }

    private static String safe(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value.trim();
    }
}
