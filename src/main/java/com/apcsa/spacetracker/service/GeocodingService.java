package com.apcsa.spacetracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeocodingService {
    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String USER_AGENT = "SpaceTracker/1.0 (contact@endoftimee.tech)";
    private static final Duration GEOCODE_CACHE_TTL = Duration.ofDays(14);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PostgresCacheService cacheService;

    public GeocodingService(PostgresCacheService cacheService) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.objectMapper = new ObjectMapper();
        this.cacheService = cacheService;
    }

    public Coordinates geocodeAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }

        JsonNode root = runSearch(address, 1);
        if (root == null || !root.isArray() || root.isEmpty()) {
            return null;
        }

        JsonNode first = root.get(0);
        double lat = first.path("lat").asDouble(Double.NaN);
        double lon = first.path("lon").asDouble(Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return null;
        }

        return new Coordinates(lat, lon);
    }

    public List<AddressSuggestion> suggestAddresses(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        JsonNode root = runSearch(query, 5);
        if (root == null || !root.isArray()) {
            return List.of();
        }

        List<AddressSuggestion> suggestions = new ArrayList<>();
        for (JsonNode item : root) {
            String displayName = item.path("display_name").asText("");
            double lat = item.path("lat").asDouble(Double.NaN);
            double lon = item.path("lon").asDouble(Double.NaN);

            if (displayName.isBlank() || Double.isNaN(lat) || Double.isNaN(lon)) {
                continue;
            }

            suggestions.add(new AddressSuggestion(displayName, lat, lon));
        }

        return suggestions;
    }

    private JsonNode runSearch(String query, int limit) {
        String url = UriComponentsBuilder
                .fromHttpUrl(NOMINATIM_URL)
                .queryParam("q", query)
                .queryParam("format", "jsonv2")
                .queryParam("limit", limit)
                .queryParam("addressdetails", 1)
                .queryParam("accept-language", "en")
                .queryParam("email", "contact@endoftimee.tech")
                .toUriString();
        String cacheKey = "nominatim:" + normalizeQuery(query) + ":limit:" + limit;

        try {
            String cached = cacheService.getCachedResponse("nominatim", cacheKey);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readTree(cached);
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Geocoding request returned status {} for query {}", response.statusCode(), query);
                return null;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                return null;
            }

            cacheService.putCachedResponse("nominatim", cacheKey, body, GEOCODE_CACHE_TTL);
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("Geocoding lookup failed for query: {}", query, e);
            return null;
        }
    }

    private String normalizeQuery(String query) {
        return query.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    public static class AddressSuggestion {
        private final String displayName;
        private final double latitude;
        private final double longitude;

        public AddressSuggestion(String displayName, double latitude, double longitude) {
            this.displayName = displayName;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }

    public static class Coordinates {
        private final double latitude;
        private final double longitude;

        public Coordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }
}
