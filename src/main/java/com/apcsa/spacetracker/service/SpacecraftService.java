package com.apcsa.spacetracker.service;

import com.apcsa.spacetracker.model.SpacecraftRelative;
import com.apcsa.spacetracker.model.VisiblePass;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class SpacecraftService {
    private static final double EARTH_A_KM = 6378.137;
    private static final double EARTH_F = 1.0 / 298.257223563;
    private static final double EARTH_E2 = EARTH_F * (2 - EARTH_F);
    private static final int SEARCH_RADIUS_DEG = 70;
    private static final int CATEGORY_BRIGHTEST = 1;
    private static final int MAX_CANDIDATES = 6;
    private static final int DAYS = 1;
    private static final int MIN_VISIBILITY_SECONDS = 20;
    private static final int WINDOW_SECONDS = 300;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private static final Duration WHERE_THE_ISS_LIST_TTL = Duration.ofHours(6);
    private static final Duration WHERE_THE_ISS_DETAIL_TTL = Duration.ofSeconds(45);
    private static final Duration N2YO_ABOVE_TTL = Duration.ofMinutes(5);
    private static final Duration N2YO_VISUALPASSES_TTL = Duration.ofSeconds(45);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String ny2oApiKey;
    private final PostgresCacheService cacheService;

    public SpacecraftService(
            @Value("${ny2o.api.key:}") String ny2oApiKey,
            PostgresCacheService cacheService
    ) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.ny2oApiKey = ny2oApiKey;
        this.cacheService = cacheService;
    }

    public List<SpacecraftRelative> getRelativeSpacecraft(double observerLat, double observerLon) {
        List<SpacecraftRelative> results = new ArrayList<>();

        // Turn live satellite positions into something the user can read from their location.
        List<Integer> ids = fetchAvailableSatelliteIds();
        for (Integer id : ids) {
            addIfAvailable(results, observerLat, observerLon, id, "SAT-" + id);
        }

        return results;
    }

    public List<VisiblePass> getVisiblePasses(double observerLat, double observerLon) {
        if (ny2oApiKey == null || ny2oApiKey.isBlank()) {
            return List.of();
        }

        // Start with bright nearby satellites, then narrow to passes happening right now.
        List<Integer> candidateIds = fetchPassCandidateIds(observerLat, observerLon);
        long now = Instant.now().getEpochSecond();
        long windowEnd = now + WINDOW_SECONDS;

        List<VisiblePass> results = new ArrayList<>();
        for (int satId : candidateIds) {
            results.addAll(fetchVisualPassesForSatellite(satId, observerLat, observerLon, now, windowEnd));
        }

        results.sort(
                Comparator.comparing((VisiblePass pass) -> !"Visible now".equals(pass.getStatus()))
                        .thenComparingLong(VisiblePass::getStartEpoch)
        );

        return results;
    }

    private List<Integer> fetchAvailableSatelliteIds() {
        List<Integer> ids = new ArrayList<>();
        String cacheKey = "wheretheiss:satellites";

        try {
            String json = readThroughCache(
                    "wheretheiss",
                    cacheKey,
                    "https://api.wheretheiss.at/v1/satellites",
                    WHERE_THE_ISS_LIST_TTL
            );
            if (json == null || json.isBlank()) {
                return List.of(25544);
            }

            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of(25544);
            }

            for (JsonNode sat : root) {
                int id = sat.path("id").asInt(-1);
                String name = sat.path("name").asText("");
                if (id > 0) {
                    ids.add(id);
                    cacheService.upsertCatalogEntry(id, name.isBlank() ? "SAT-" + id : name, "wheretheiss");
                }
            }

            if (ids.isEmpty()) {
                return List.of(25544);
            }
            return ids;
        } catch (Exception e) {
            return List.of(25544);
        }
    }

    private List<Integer> fetchPassCandidateIds(double lat, double lon) {
        String url = String.format(
                Locale.US,
                "https://api.n2yo.com/rest/v1/satellite/above/%.6f/%.6f/0/%d/%d/&apiKey=%s",
                lat, lon, SEARCH_RADIUS_DEG, CATEGORY_BRIGHTEST, ny2oApiKey
        );
        String cacheKey = String.format(Locale.US, "n2yo:above:%.4f:%.4f:%d:%d", lat, lon, SEARCH_RADIUS_DEG, CATEGORY_BRIGHTEST);

        List<Integer> ids = new ArrayList<>();
        try {
            String json = readThroughCache("n2yo", cacheKey, url, N2YO_ABOVE_TTL);
            if (json == null || json.isBlank()) {
                return ids;
            }

            JsonNode above = objectMapper.readTree(json).path("above");
            if (!above.isArray()) {
                return ids;
            }

            for (JsonNode sat : above) {
                int id = sat.path("satid").asInt(-1);
                String name = sat.path("satname").asText("");
                if (id > 0) {
                    ids.add(id);
                    cacheService.upsertCatalogEntry(id, name.isBlank() ? "SAT-" + id : name, "n2yo");
                    if (ids.size() >= MAX_CANDIDATES) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return ids;
    }

    private void addIfAvailable(List<SpacecraftRelative> results, double observerLat, double observerLon, int noradId, String fallbackName) {
        String url = "https://api.wheretheiss.at/v1/satellites/" + noradId;
        String cacheKey = "wheretheiss:satellite:" + noradId;

        try {
            String json = readThroughCache("wheretheiss", cacheKey, url, WHERE_THE_ISS_DETAIL_TTL);
            if (json == null || json.isBlank()) {
                return;
            }

            JsonNode root = objectMapper.readTree(json);
            if (root.has("error")) {
                return;
            }

            String name = root.path("name").asText(fallbackName).toUpperCase();
            double satLat = root.path("latitude").asDouble(Double.NaN);
            double satLon = root.path("longitude").asDouble(Double.NaN);
            double satAlt = root.path("altitude").asDouble(Double.NaN);
            String visibility = root.path("visibility").asText("unknown");

            if (Double.isNaN(satLat) || Double.isNaN(satLon) || Double.isNaN(satAlt)) {
                return;
            }

            cacheService.upsertCatalogEntry(noradId, name, "wheretheiss");

            Topocentric topo = computeTopocentric(observerLat, observerLon, 0.0, satLat, satLon, satAlt);
            results.add(new SpacecraftRelative(
                    name,
                    noradId,
                    round1(topo.elevationDegrees),
                    round1(topo.azimuthDegrees),
                    azimuthToDirection(topo.azimuthDegrees),
                    round1(topo.rangeKm),
                    visibility
            ));
        } catch (Exception ignored) {
        }
    }

    private List<VisiblePass> fetchVisualPassesForSatellite(
            int satId,
            double lat,
            double lon,
            long now,
            long windowEnd
    ) {
        String url = String.format(
                Locale.US,
                "https://api.n2yo.com/rest/v1/satellite/visualpasses/%d/%.6f/%.6f/0/%d/%d/&apiKey=%s",
                satId, lat, lon, DAYS, MIN_VISIBILITY_SECONDS, ny2oApiKey
        );
        String cacheKey = String.format(Locale.US, "n2yo:visualpasses:%d:%.4f:%.4f:%d:%d", satId, lat, lon, DAYS, MIN_VISIBILITY_SECONDS);

        List<VisiblePass> results = new ArrayList<>();

        try {
            String json = readThroughCache("n2yo", cacheKey, url, N2YO_VISUALPASSES_TTL);
            if (json == null || json.isBlank()) {
                return results;
            }

            JsonNode root = objectMapper.readTree(json);
            String satName = root.path("info").path("satname").asText("SAT-" + satId);
            cacheService.upsertCatalogEntry(satId, satName, "n2yo");

            JsonNode passes = root.path("passes");
            if (!passes.isArray()) {
                return results;
            }

            for (JsonNode pass : passes) {
                long startUtc = pass.path("startUTC").asLong(0);
                long endUtc = pass.path("endUTC").asLong(0);
                if (startUtc == 0 || endUtc == 0) {
                    continue;
                }

                // Keep the list focused on passes the user could realistically look for now.
                if (startUtc > windowEnd || endUtc < now) {
                    continue;
                }

                long maxUtc = pass.path("maxUTC").asLong(startUtc);
                String status = startUtc <= now ? "Visible now" : "Within 5 min";
                double maxEl = pass.path("maxEl").asDouble(0.0);
                String maxDir = pass.path("maxAzCompass").asText("?");
                int duration = pass.path("duration").asInt(0);
                double magRaw = pass.path("mag").asDouble(100000.0);
                String magnitude = magRaw >= 99999 ? "unknown" : String.valueOf(round1(magRaw));

                results.add(new VisiblePass(
                        satName,
                        satId,
                        status,
                        startUtc,
                        toLocal(startUtc),
                        toLocal(maxUtc),
                        toLocal(endUtc),
                        round1(maxEl),
                        maxDir,
                        duration,
                        magnitude
                ));
            }
        } catch (Exception ignored) {
        }

        return results;
    }

    private String readThroughCache(String source, String cacheKey, String url, Duration ttl) {
        String cached = cacheService.getCachedResponse(source, cacheKey);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        String live = restTemplate.getForObject(url, String.class);
        if (live != null && !live.isBlank()) {
            cacheService.putCachedResponse(source, cacheKey, live, ttl);
        }
        return live;
    }

    private Topocentric computeTopocentric(double obsLatDeg, double obsLonDeg, double obsAltKm,
                                           double satLatDeg, double satLonDeg, double satAltKm) {
        Vector3 obs = geodeticToEcef(obsLatDeg, obsLonDeg, obsAltKm);
        Vector3 sat = geodeticToEcef(satLatDeg, satLonDeg, satAltKm);

        double dx = sat.x - obs.x;
        double dy = sat.y - obs.y;
        double dz = sat.z - obs.z;

        double lat = Math.toRadians(obsLatDeg);
        double lon = Math.toRadians(obsLonDeg);

        double east = -Math.sin(lon) * dx + Math.cos(lon) * dy;
        double north = -Math.sin(lat) * Math.cos(lon) * dx - Math.sin(lat) * Math.sin(lon) * dy + Math.cos(lat) * dz;
        double up = Math.cos(lat) * Math.cos(lon) * dx + Math.cos(lat) * Math.sin(lon) * dy + Math.sin(lat) * dz;

        double horizontal = Math.sqrt(east * east + north * north);
        double range = Math.sqrt(horizontal * horizontal + up * up);

        double az = Math.toDegrees(Math.atan2(east, north));
        if (az < 0) {
            az += 360.0;
        }

        double el = Math.toDegrees(Math.atan2(up, horizontal));
        return new Topocentric(az, el, range);
    }

    private Vector3 geodeticToEcef(double latDeg, double lonDeg, double altKm) {
        double lat = Math.toRadians(latDeg);
        double lon = Math.toRadians(lonDeg);

        double sinLat = Math.sin(lat);
        double cosLat = Math.cos(lat);
        double cosLon = Math.cos(lon);
        double sinLon = Math.sin(lon);

        double n = EARTH_A_KM / Math.sqrt(1.0 - EARTH_E2 * sinLat * sinLat);
        double x = (n + altKm) * cosLat * cosLon;
        double y = (n + altKm) * cosLat * sinLon;
        double z = (n * (1.0 - EARTH_E2) + altKm) * sinLat;

        return new Vector3(x, y, z);
    }

    private String azimuthToDirection(double azimuth) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.round(azimuth / 45.0) % 8;
        return dirs[index];
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String toLocal(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault())
                .format(TIME_FMT);
    }

    private static class Vector3 {
        private final double x;
        private final double y;
        private final double z;

        private Vector3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class Topocentric {
        private final double azimuthDegrees;
        private final double elevationDegrees;
        private final double rangeKm;

        private Topocentric(double azimuthDegrees, double elevationDegrees, double rangeKm) {
            this.azimuthDegrees = azimuthDegrees;
            this.elevationDegrees = elevationDegrees;
            this.rangeKm = rangeKm;
        }
    }
}
