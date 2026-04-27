package com.apcsa.spacetracker.model;

public class AstroEvent {
    private final String body;
    private final String eventType;
    private final String eventTime;
    private final String peakAltitude;
    private final String riseTime;
    private final String setTime;
    private final String note;

    public AstroEvent(
            String body,
            String eventType,
            String eventTime,
            String peakAltitude,
            String riseTime,
            String setTime,
            String note
    ) {
        this.body = body;
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.peakAltitude = peakAltitude;
        this.riseTime = riseTime;
        this.setTime = setTime;
        this.note = note;
    }

    public String getBody() {
        return body;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventTime() {
        return eventTime;
    }

    public String getPeakAltitude() {
        return peakAltitude;
    }

    public String getRiseTime() {
        return riseTime;
    }

    public String getSetTime() {
        return setTime;
    }

    public String getNote() {
        return note;
    }
}
