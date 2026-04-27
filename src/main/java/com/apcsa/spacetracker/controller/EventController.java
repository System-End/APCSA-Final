package com.apcsa.spacetracker.controller;

import com.apcsa.spacetracker.service.EventService;
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

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/events")
    public String events(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) LocalTime time,
            Model model
    ) {
        Double resolvedLat = latitude == null ? DEFAULT_LATITUDE : latitude;
        Double resolvedLon = longitude == null ? DEFAULT_LONGITUDE : longitude;
        LocalDate resolvedFrom = fromDate == null ? LocalDate.now() : fromDate;
        LocalDate resolvedTo = toDate == null ? resolvedFrom.plusMonths(12) : toDate;
        LocalTime resolvedTime = time == null ? LocalTime.of(21, 0) : time;

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
        model.addAttribute("fromDate", resolvedFrom);
        model.addAttribute("toDate", resolvedTo);
        model.addAttribute("time", resolvedTime);

        return "events";
    }
}
