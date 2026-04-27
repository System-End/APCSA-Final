package com.apcsa.spacetracker.service;

import com.apcsa.spacetracker.model.SkyObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

@Service
public class SkyService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Duration SKY_CACHE_TTL = Duration.ofMinutes(2);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String appId;
    private final String appSecret;
    private final PostgresCacheService cacheService;

    public SkyService(
            @Value("${astronomy.api.app-id:}") String appId,
            @Value("${astronomy.api.app-secret:}") String appSecret,
            PostgresCacheService cacheService
    ) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.appId = appId;
        this.appSecret = appSecret;
        this.cacheService = cacheService;
    }

    public List<SkyObject> getSkyObjects(double latitude, double longitude, LocalDate date, LocalTime time) {
        ensureCredentialsPresent();
        String formattedTime = time.format(TIME_FORMAT);

        String url = UriComponentsBuilder
                .fromHttpUrl("https://api.astronomyapi.com/api/v2/bodies/positions")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("elevation", 0)
                .queryParam("from_date", date)
                .queryParam("to_date", date)
                .queryParam("time", formattedTime)
                .queryParam("output", "table")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodeBasicAuth(appId, appSecret));
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String cacheKey = String.format(
                "astronomy:positions:%.4f:%.4f:%s:%s",
                latitude, longitude, date, formattedTime
        );

        String body = readThroughAuthorizedCache("astronomy", cacheKey, url, entity, SKY_CACHE_TTL);
        if (body == null || body.isBlank()) {
            return List.of();
        }

        return parseSkyObjects(body);
    }

    private List<SkyObject> parseSkyObjects(String json) {
        List<SkyObject> objects = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode rows = root.path("data").path("table").path("rows");
            if (!rows.isArray()) {
                return objects;
            }

            for (JsonNode row : rows) {
                String name = row.path("entry").path("name").asText("Unknown");
                if (shouldSkipObject(name)) {
                    continue;
                }

                JsonNode firstCell = row.path("cells").isArray() && !row.path("cells").isEmpty()
                        ? row.path("cells").get(0)
                        : null;

                if (firstCell == null) {
                    continue;
                }

                double altitude = firstCell.path("position").path("horizontal").path("altitude").path("degrees").asDouble(Double.NaN);
                double azimuth = firstCell.path("position").path("horizontal").path("azimuth").path("degrees").asDouble(Double.NaN);

                if (Double.isNaN(altitude) || Double.isNaN(azimuth)) {
                    continue;
                }

                boolean visible = altitude > 0.0;
                String direction = azimuthToDirection(azimuth);

                objects.add(new SkyObject(name, round1(altitude), round1(azimuth), direction, visible));
            }

            objects.sort(
                    Comparator.comparing(SkyObject::isVisible).reversed()
                            .thenComparing(SkyObject::getAltitudeDegrees, Comparator.reverseOrder())
                            .thenComparing(SkyObject::getName)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Astronomy API response", e);
        }

        return objects;
    }

    private void ensureCredentialsPresent() {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException("Astronomy API credentials are not configured.");
        }
    }

    private String encodeBasicAuth(String id, String secret) {
        String raw = id + ":" + secret;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String readThroughAuthorizedCache(
            String source,
            String cacheKey,
            String url,
            HttpEntity<Void> entity,
            Duration ttl
    ) {
        String cached = cacheService.getCachedResponse(source, cacheKey);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String live = response.getBody();
        if (live != null && !live.isBlank()) {
            cacheService.putCachedResponse(source, cacheKey, live, ttl);
        }
        return live;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private boolean shouldSkipObject(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }

        String normalized = name.trim().toLowerCase();
        return "earth".equals(normalized);
    }

    private String azimuthToDirection(double azimuth) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.round(azimuth / 45.0) % 8;
        return dirs[index];
    }
}
