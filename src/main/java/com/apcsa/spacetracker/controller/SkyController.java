package com.apcsa.spacetracker.controller;

import com.apcsa.spacetracker.service.GeocodingService;
import com.apcsa.spacetracker.service.SkyService;
import com.apcsa.spacetracker.service.SpacecraftService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalTime;

@Controller
public class SkyController {
    private final SkyService skyService;
    private final GeocodingService geocodingService;
    private final SpacecraftService spacecraftService;

    public SkyController(
            SkyService skyService,
            GeocodingService geocodingService,
            SpacecraftService spacecraftService
    ) {
        this.skyService = skyService;
        this.geocodingService = geocodingService;
        this.spacecraftService = spacecraftService;
    }

    @GetMapping("/sky")
    public String sky(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalTime time,
            Model model
    ) {
        LocalDate resolvedDate = date == null ? LocalDate.now() : date;
        LocalTime resolvedTime = time == null ? LocalTime.now().withSecond(0).withNano(0) : time;

        Double resolvedLat = latitude;
        Double resolvedLon = longitude;

        if ((resolvedLat == null || resolvedLon == null) && address != null && !address.isBlank()) {
            GeocodingService.Coordinates coords = geocodingService.geocodeAddress(address);
            if (coords != null) {
                resolvedLat = coords.getLatitude();
                resolvedLon = coords.getLongitude();
            } else {
                model.addAttribute("error", "Could not find that address.");
            }
        }

        if (resolvedLat != null && resolvedLon != null) {
            try {
                model.addAttribute("objects", skyService.getSkyObjects(resolvedLat, resolvedLon, resolvedDate, resolvedTime));
            } catch (Exception ex) {
                model.addAttribute("error", "Could not load sky positions right now.");
            }

            try {
                model.addAttribute("spacecraft", spacecraftService.getRelativeSpacecraft(resolvedLat, resolvedLon));
            } catch (Exception ex) {
                model.addAttribute("spacecraftError", "Could not load spacecraft positions right now.");
            }

            try {
                model.addAttribute("visiblePasses", spacecraftService.getVisiblePasses(resolvedLat, resolvedLon));
            } catch (Exception ex) {
                model.addAttribute("passError", "Could not load visible pass predictions right now.");
            }
        }

        model.addAttribute("searched", resolvedLat != null && resolvedLon != null);
        model.addAttribute("latitude", resolvedLat == null ? "" : resolvedLat);
        model.addAttribute("longitude", resolvedLon == null ? "" : resolvedLon);
        model.addAttribute("address", address == null ? "" : address);
        model.addAttribute("date", resolvedDate);
        model.addAttribute("time", resolvedTime);

        return "sky";
    }
}
