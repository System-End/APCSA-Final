package com.apcsa.spacetracker.model;

public class Mission {
    private final String name;
    private final String agency;
    private final String status;
    private final String launchDate;
    private final String location;
    private final String missionType;
    private final int year;

    public Mission(
            String name,
            String agency,
            String status,
            String launchDate,
            String location,
            String missionType,
            int year
    ) {
        this.name = name;
        this.agency = agency;
        this.status = status;
        this.launchDate = launchDate;
        this.location = location;
        this.missionType = missionType;
        this.year = year;
    }

    public String getName() {
        return name;
    }

    public String getAgency() {
        return agency;
    }

    public String getStatus() {
        return status;
    }

    public String getLaunchDate() {
        return launchDate;
    }

    public String getLocation() {
        return location;
    }

    public String getMissionType() {
        return missionType;
    }

    public int getYear() {
        return year;
    }
}
