package com.apcsa.spacetracker.controller;

import com.apcsa.spacetracker.model.CacheStat;
import com.apcsa.spacetracker.service.PostgresCacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CacheController {
    private final PostgresCacheService cacheService;

    public CacheController(PostgresCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @GetMapping("/api/cache/stats")
    public List<CacheStat> stats() {
        return cacheService.getCacheStats();
    }
}
