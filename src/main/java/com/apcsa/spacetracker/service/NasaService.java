package com.apcsa.spacetracker.service;

import com.apcsa.spacetracker.model.ApodResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDate;

@Service
public class NasaService {
    private static final Duration APOD_CACHE_TTL = Duration.ofHours(12);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String nasaApiKey;
    private final PostgresCacheService cacheService;

    public NasaService(
            @Value("${nasa.api.key}") String nasaApiKey,
            PostgresCacheService cacheService
    ) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.nasaApiKey = nasaApiKey;
        this.cacheService = cacheService;
    }

    public ApodResponse getApod() {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://api.nasa.gov/planetary/apod")
                .queryParam("api_key", nasaApiKey)
                .toUriString();
        String cacheKey = "apod:" + LocalDate.now();

        try {
            // APOD changes daily, so one cache entry per date is enough.
            String json = readThroughCache("nasa", cacheKey, url, APOD_CACHE_TTL);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, ApodResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load NASA APOD", e);
        }
    }

    private String readThroughCache(String source, String cacheKey, String url, Duration ttl) {
        String cached = cacheService.getCachedResponse(source, cacheKey);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        // Store the raw NASA response so later requests can rebuild the page from cache.
        String live = restTemplate.getForObject(url, String.class);
        if (live != null && !live.isBlank()) {
            cacheService.putCachedResponse(source, cacheKey, live, ttl);
        }
        return live;
    }
}
