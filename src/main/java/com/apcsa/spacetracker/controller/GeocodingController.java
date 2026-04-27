package com.apcsa.spacetracker.controller;

import com.apcsa.spacetracker.service.GeocodingService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
public class GeocodingController {
    private final GeocodingService geocodingService;

    public GeocodingController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @GetMapping("/api/geocode/suggest")
    public ResponseEntity<List<GeocodingService.AddressSuggestion>> suggest(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank() || q.trim().length() < 3) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                    .body(List.of());
        }

        List<GeocodingService.AddressSuggestion> suggestions = geocodingService.suggestAddresses(q.trim());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .body(suggestions);
    }
}
