package com.apcsa.spacetracker.model;

public class SpacecraftCatalogEntry {
    private final int noradId;
    private final String displayName;
    private final String source;
    private final String lastSeenAt;

    public SpacecraftCatalogEntry(int noradId, String displayName, String source, String lastSeenAt) {
        this.noradId = noradId;
        this.displayName = displayName;
        this.source = source;
        this.lastSeenAt = lastSeenAt;
    }

    public int getNoradId() {
        return noradId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSource() {
        return source;
    }

    public String getLastSeenAt() {
        return lastSeenAt;
    }
}
