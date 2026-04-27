package com.apcsa.spacetracker.model;

public class SpacecraftRelative {
    private final String name;
    private final int noradId;
    private final double elevationDegrees;
    private final double azimuthDegrees;
    private final String direction;
    private final double rangeKm;
    private final String visibility;

    public SpacecraftRelative(
            String name,
            int noradId,
            double elevationDegrees,
            double azimuthDegrees,
            String direction,
            double rangeKm,
            String visibility
    ) {
        this.name = name;
        this.noradId = noradId;
        this.elevationDegrees = elevationDegrees;
        this.azimuthDegrees = azimuthDegrees;
        this.direction = direction;
        this.rangeKm = rangeKm;
        this.visibility = visibility;
    }

    public String getName() {
        return name;
    }

    public int getNoradId() {
        return noradId;
    }

    public double getElevationDegrees() {
        return elevationDegrees;
    }

    public double getAzimuthDegrees() {
        return azimuthDegrees;
    }

    public String getDirection() {
        return direction;
    }

    public double getRangeKm() {
        return rangeKm;
    }

    public String getVisibility() {
        return visibility;
    }
}
