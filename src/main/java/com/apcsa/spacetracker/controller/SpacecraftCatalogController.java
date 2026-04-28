package com.apcsa.spacetracker.controller;

import com.apcsa.spacetracker.model.SpacecraftCatalogEntry;
import com.apcsa.spacetracker.model.SpacecraftRelative;
import com.apcsa.spacetracker.model.VisiblePass;
import com.apcsa.spacetracker.service.PostgresCacheService;
import com.apcsa.spacetracker.service.SpacecraftService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
public class SpacecraftCatalogController {
    private final PostgresCacheService cacheService;
    private final SpacecraftService spacecraftService;

    public SpacecraftCatalogController(
            PostgresCacheService cacheService,
            SpacecraftService spacecraftService
    ) {
        this.cacheService = cacheService;
        this.spacecraftService = spacecraftService;
    }

    @GetMapping("/api/spacecraft/search")
    public ResponseEntity<List<SpacecraftCatalogEntry>> search(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank() || q.trim().length() < 2) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                    .body(List.of());
        }

        List<SpacecraftCatalogEntry> results = cacheService.searchCatalog(q.trim(), 8);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .body(results);
    }

    @GetMapping("/api/spacecraft/suggest")
    public ResponseEntity<List<SpacecraftCatalogEntry>> suggest(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank() || q.trim().length() < 2) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                    .body(List.of());
        }

        List<SpacecraftCatalogEntry> results = cacheService.suggestCatalog(q.trim(), 6);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .body(results);
    }

    @GetMapping("/api/spacecraft/relative")
    public ResponseEntity<List<SpacecraftRelative>> relative(
            @RequestParam Double latitude,
            @RequestParam Double longitude
    ) {
        List<SpacecraftRelative> results = spacecraftService.getRelativeSpacecraft(latitude, longitude);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .body(results);
    }

    @GetMapping("/api/spacecraft/passes")
    public ResponseEntity<List<VisiblePass>> passes(
            @RequestParam Double latitude,
            @RequestParam Double longitude
    ) {
        List<VisiblePass> results = spacecraftService.getVisiblePasses(latitude, longitude);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .body(results);
    }
}
