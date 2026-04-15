package com.apcsa.spacetracker.model;

public class SkyObject {
    private final String name;
    private final double altitudeDegrees;
    private final double azimuthDegrees;
    private final String direction;
    private final boolean visible;

    public SkyObject(String name, double altitudeDegrees, double azimuthDegrees, String direction, boolean visible) {
        this.name = name;
        this.altitudeDegrees = altitudeDegrees;
        this.azimuthDegrees = azimuthDegrees;
        this.direction = direction;
        this.visible = visible;
    }

    public String getName() {
        return name;
    }

    public double getAltitudeDegrees() {
        return altitudeDegrees;
    }

    public double getAzimuthDegrees() {
        return azimuthDegrees;
    }

    public String getDirection() {
        return direction;
    }

    public boolean isVisible() {
        return visible;
    }
}
