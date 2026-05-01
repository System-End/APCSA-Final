package com.apcsa.spacetracker.controller;

import com.apcsa.spacetracker.service.EventService;
import com.apcsa.spacetracker.service.GeocodingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalTime;

@Controller
public class EventController {
    private static final double DEFAULT_LATITUDE = 33.4484;
    private static final double DEFAULT_LONGITUDE = -112.0740;

    private final EventService eventService;
    private final GeocodingService geocodingService;

    public EventController(EventService eventService, GeocodingService geocodingService) {
        this.eventService = eventService;
        this.geocodingService = geocodingService;
    }

    @GetMapping("/events")
    public String events(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) LocalTime time,
            Model model
    ) {
        Double resolvedLat = latitude;
        Double resolvedLon = longitude;
        LocalDate resolvedFrom = fromDate == null ? LocalDate.now() : fromDate;
        LocalDate resolvedTo = toDate == null ? resolvedFrom.plusMonths(12) : toDate;
        LocalTime resolvedTime = time == null ? LocalTime.of(21, 0) : time;

        // Match the sky page: users can search by coordinates or by address.
        if ((resolvedLat == null || resolvedLon == null) && address != null && !address.isBlank()) {
            GeocodingService.Coordinates coords = geocodingService.geocodeAddress(address);
            if (coords != null) {
                resolvedLat = coords.getLatitude();
                resolvedLon = coords.getLongitude();
            } else {
                model.addAttribute("error", "Could not find that address.");
            }
        }

        if (resolvedLat == null) {
            // Default to Phoenix so the page has useful sample data on first load.
            resolvedLat = DEFAULT_LATITUDE;
        }

        if (resolvedLon == null) {
            resolvedLon = DEFAULT_LONGITUDE;
        }

        if (resolvedTo.isBefore(resolvedFrom)) {
            model.addAttribute("error", "`To` date must be on or after `From` date.");
        } else {
            try {
                model.addAttribute(
                        "events",
                        eventService.getEvents(resolvedLat, resolvedLon, resolvedFrom, resolvedTo, resolvedTime)
                );
            } catch (Exception ex) {
                model.addAttribute("error", "Could not load astronomy events right now.");
            }
        }

        model.addAttribute("latitude", resolvedLat);
        model.addAttribute("longitude", resolvedLon);
        model.addAttribute("address", address == null ? "" : address);
        model.addAttribute("fromDate", resolvedFrom);
        model.addAttribute("toDate", resolvedTo);
        model.addAttribute("time", resolvedTime);

        return "events";
    }
}
