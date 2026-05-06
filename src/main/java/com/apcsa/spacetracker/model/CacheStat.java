package com.apcsa.spacetracker.model;

public class CacheStat {
    private final String source;
    private final long totalEntries;
    private final long freshEntries;
    private final long staleEntries;

    public CacheStat(String source, long totalEntries, long freshEntries, long staleEntries) {
        this.source = source;
        this.totalEntries = totalEntries;
        this.freshEntries = freshEntries;
        this.staleEntries = staleEntries;
    }

    public String getSource() {
        return source;
    }

    public long getTotalEntries() {
        return totalEntries;
    }

    public long getFreshEntries() {
        return freshEntries;
    }

    public long getStaleEntries() {
        return staleEntries;
    }
}
