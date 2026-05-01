package com.apcsa.spacetracker.service;

import com.apcsa.spacetracker.model.AstroEvent;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

@Service
public class EventService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Duration EVENTS_CACHE_TTL = Duration.ofHours(12);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String appId;
    private final String appSecret;
    private final PostgresCacheService cacheService;

    public EventService(
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

    public List<AstroEvent> getEvents(
            double latitude,
            double longitude,
            LocalDate fromDate,
            LocalDate toDate,
            LocalTime time
    ) {
        ensureCredentialsPresent();

        List<AstroEvent> events = new ArrayList<>();
        // AstronomyAPI splits sun and moon events, so the page merges them into one timeline.
        events.addAll(fetchEventsForBody("sun", latitude, longitude, fromDate, toDate, time));
        events.addAll(fetchEventsForBody("moon", latitude, longitude, fromDate, toDate, time));
        events.sort(Comparator.comparing(AstroEvent::getEventTime));
        return events;
    }

    private List<AstroEvent> fetchEventsForBody(
            String body,
            double latitude,
            double longitude,
            LocalDate fromDate,
            LocalDate toDate,
            LocalTime time
    ) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://api.astronomyapi.com/api/v2/bodies/events/" + body)
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("elevation", 0)
                .queryParam("from_date", fromDate)
                .queryParam("to_date", toDate)
                .queryParam("time", time.format(TIME_FORMAT))
                .queryParam("output", "rows")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodeBasicAuth(appId, appSecret));
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String cacheKey = String.format(
                "astronomy:events:v2:%s:%.4f:%.4f:%s:%s:%s",
                body, latitude, longitude, fromDate, toDate, time.format(TIME_FORMAT)
        );

        String bodyJson = readThroughAuthorizedCache("astronomy", cacheKey, url, entity, EVENTS_CACHE_TTL);
        if (bodyJson == null || bodyJson.isBlank()) {
            return List.of();
        }

        return parseEvents(bodyJson, body);
    }

    private List<AstroEvent> parseEvents(String json, String fallbackBody) {
        List<AstroEvent> events = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode rows = root.path("data").path("rows");
            if (!rows.isArray()) {
                return events;
            }

            for (JsonNode row : rows) {
                String body = row.path("body").path("name").asText(capitalize(fallbackBody));
                JsonNode eventNode = row.path("events");
                if (!eventNode.isArray()) {
                    continue;
                }

                for (JsonNode event : eventNode) {
                    String eventType = event.path("type").asText("Unknown");
                    JsonNode highlights = event.path("eventHighlights");
                    // Peak gives the clearest one-line summary, even when an event has many phases.
                    String eventTime = formatDateTime(highlights.path("peak").path("date").asText(""));
                    String peakAltitude = formatAltitude(highlights.path("peak").path("altitude"));
                    String riseTime = formatDateTime(event.path("rise").asText(""));
                    String setTime = formatDateTime(event.path("set").asText(""));
                    String note = buildNote(event);

                    events.add(new AstroEvent(body, eventType, eventTime, peakAltitude, riseTime, setTime, note));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Astronomy API events response", e);
        }

        return events;
    }

    private String buildNote(JsonNode event) {
        List<String> parts = new ArrayList<>();

        JsonNode extraInfo = event.path("extraInfo");

        if (extraInfo.has("obscuration") && !extraInfo.path("obscuration").isNull()) {
            parts.add("Obscuration: " + round1(extraInfo.path("obscuration").asDouble()) + "%");
        }

        if (extraInfo.has("closestApproach") && !extraInfo.path("closestApproach").isNull()) {
            parts.add("Closest approach: " + round1(extraInfo.path("closestApproach").asDouble()) + " km");
        }

        JsonNode highlights = event.path("eventHighlights");
        String partialStart = formatDateTime(highlights.path("partialStart").path("date").asText(""));
        if (!"-".equals(partialStart)) {
            parts.add("Partial start: " + partialStart);
        }

        String totalStart = formatDateTime(highlights.path("totalStart").path("date").asText(""));
        if (!"-".equals(totalStart)) {
            parts.add("Total start: " + totalStart);
        }

        if (parts.isEmpty()) {
            return "No extra highlights";
        }
        return String.join(" | ", parts);
    }

    private String formatAltitude(JsonNode altitudeNode) {
        if (altitudeNode == null || altitudeNode.isMissingNode()) {
            return "-";
        }
        double degrees = altitudeNode.asDouble(Double.NaN);
        if (Double.isNaN(degrees)) {
            return "-";
        }
        return round1(degrees) + " deg";
    }

    private String formatDateTime(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        try {
            return OffsetDateTime.parse(value).toLocalDateTime().format(DISPLAY_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalTime.parse(value).format(DISPLAY_TIME);
        } catch (DateTimeParseException ignored) {
            return value;
        }
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

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
