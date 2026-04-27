package com.apcsa.spacetracker.model;

public class VisiblePass {
    private final String satName;
    private final int satId;
    private final String status;
    private final long startEpoch;
    private final String startLocal;
    private final String maxLocal;
    private final String endLocal;
    private final double maxElevation;
    private final String maxDirection;
    private final int durationSeconds;
    private final String magnitudeLabel;

    public VisiblePass(
            String satName,
            int satId,
            String status,
            long startEpoch,
            String startLocal,
            String maxLocal,
            String endLocal,
            double maxElevation,
            String maxDirection,
            int durationSeconds,
            String magnitudeLabel
    ) {
        this.satName = satName;
        this.satId = satId;
        this.status = status;
        this.startEpoch = startEpoch;
        this.startLocal = startLocal;
        this.maxLocal = maxLocal;
        this.endLocal = endLocal;
        this.maxElevation = maxElevation;
        this.maxDirection = maxDirection;
        this.durationSeconds = durationSeconds;
        this.magnitudeLabel = magnitudeLabel;
    }

    public String getSatName() {
        return satName;
    }

    public int getSatId() {
        return satId;
    }

    public String getStatus() {
        return status;
    }

    public long getStartEpoch() {
        return startEpoch;
    }

    public String getStartLocal() {
        return startLocal;
    }

    public String getMaxLocal() {
        return maxLocal;
    }

    public String getEndLocal() {
        return endLocal;
    }

    public double getMaxElevation() {
        return maxElevation;
    }

    public String getMaxDirection() {
        return maxDirection;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public String getMagnitudeLabel() {
        return magnitudeLabel;
    }
}
