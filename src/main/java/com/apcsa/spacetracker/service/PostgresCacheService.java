package com.apcsa.spacetracker.service;

import com.apcsa.spacetracker.model.SpacecraftCatalogEntry;
import com.apcsa.spacetracker.model.CacheStat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PostgresCacheService {
    private static final Logger log = LoggerFactory.getLogger(PostgresCacheService.class);
    private static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final boolean enabled;

    public PostgresCacheService(
            @Value("${postgres.jdbc-url:}") String jdbcUrl,
            @Value("${postgres.username:}") String username,
            @Value("${postgres.password:}") String password
    ) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.enabled = jdbcUrl != null && !jdbcUrl.isBlank()
                && username != null && !username.isBlank();

        if (enabled) {
            initializeSchema();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCachedResponse(String source, String cacheKey) {
        if (!enabled) {
            return null;
        }

        // Only return fresh rows so each service can treat cache misses the same way.
        String sql = """
                SELECT response_json
                FROM api_response_cache
                WHERE source = ? AND cache_key = ? AND expires_at > CURRENT_TIMESTAMP
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source);
            statement.setString(2, cacheKey);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("response_json");
                }
            }
        } catch (Exception e) {
            log.warn("Cache lookup failed for source={} key={}", source, cacheKey, e);
        }

        return null;
    }

    public void putCachedResponse(String source, String cacheKey, String responseJson, Duration ttl) {
        if (!enabled || responseJson == null || responseJson.isBlank() || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }

        String sql = """
                INSERT INTO api_response_cache (source, cache_key, response_json, fetched_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (source, cache_key)
                DO UPDATE SET response_json = EXCLUDED.response_json,
                              fetched_at = EXCLUDED.fetched_at,
                              expires_at = EXCLUDED.expires_at
                """;

        Instant now = Instant.now();
        Instant expires = now.plus(ttl);

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source);
            statement.setString(2, cacheKey);
            statement.setString(3, responseJson);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setTimestamp(5, Timestamp.from(expires));
            statement.executeUpdate();
        } catch (Exception e) {
            log.warn("Cache write failed for source={} key={}", source, cacheKey, e);
        }
    }

    public void upsertCatalogEntry(int noradId, String name, String source) {
        if (!enabled || noradId <= 0 || name == null || name.isBlank()) {
            return;
        }

        // The catalog lets the UI search spacecraft names without calling the APIs again.
        String sql = """
                INSERT INTO spacecraft_catalog (norad_id, display_name, normalized_name, source, last_seen_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (norad_id)
                DO UPDATE SET display_name = EXCLUDED.display_name,
                              normalized_name = EXCLUDED.normalized_name,
                              source = EXCLUDED.source,
                              last_seen_at = EXCLUDED.last_seen_at
                """;

        Instant now = Instant.now();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, noradId);
            statement.setString(2, name);
            statement.setString(3, normalizeName(name));
            statement.setString(4, source);
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        } catch (Exception e) {
            log.warn("Spacecraft catalog update failed for NORAD {}", noradId, e);
        }
    }

    public List<Integer> searchCatalogIds(String query, int limit) {
        if (!enabled || query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        String normalized = "%" + normalizeName(query) + "%";
        String sql = """
                SELECT norad_id
                FROM spacecraft_catalog
                WHERE normalized_name LIKE ?
                ORDER BY last_seen_at DESC
                LIMIT ?
                """;

        List<Integer> ids = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getInt("norad_id"));
                }
            }
        } catch (Exception e) {
            log.warn("Spacecraft catalog ID search failed for query {}", query, e);
        }

        return ids;
    }

    public List<SpacecraftCatalogEntry> searchCatalog(String query, int limit) {
        if (!enabled || query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        String normalized = "%" + normalizeName(query) + "%";
        String sql = """
                SELECT norad_id, display_name, source, last_seen_at
                FROM spacecraft_catalog
                WHERE normalized_name LIKE ?
                ORDER BY last_seen_at DESC
                LIMIT ?
                """;

        List<SpacecraftCatalogEntry> results = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp lastSeen = resultSet.getTimestamp("last_seen_at");
                    results.add(new SpacecraftCatalogEntry(
                            resultSet.getInt("norad_id"),
                            resultSet.getString("display_name"),
                            resultSet.getString("source"),
                            lastSeen == null
                                    ? "-"
                                    : lastSeen.toInstant().atZone(ZoneId.systemDefault()).format(DISPLAY_TIME_FORMAT)
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Spacecraft catalog search failed for query {}", query, e);
        }

        return results;
    }

    public List<SpacecraftCatalogEntry> suggestCatalog(String query, int limit) {
        if (!enabled || query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        String normalized = normalizeName(query);
        String prefix = normalized + "%";
        String contains = "%" + normalized + "%";
        // Autocomplete prefers prefix matches, but partial names should still work.
        String sql = """
                SELECT norad_id, display_name, source, last_seen_at
                FROM spacecraft_catalog
                WHERE normalized_name LIKE ? OR normalized_name LIKE ?
                ORDER BY
                    CASE WHEN normalized_name LIKE ? THEN 0 ELSE 1 END,
                    last_seen_at DESC
                LIMIT ?
                """;

        List<SpacecraftCatalogEntry> results = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, prefix);
            statement.setString(2, contains);
            statement.setString(3, prefix);
            statement.setInt(4, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp lastSeen = resultSet.getTimestamp("last_seen_at");
                    results.add(new SpacecraftCatalogEntry(
                            resultSet.getInt("norad_id"),
                            resultSet.getString("display_name"),
                            resultSet.getString("source"),
                            lastSeen == null
                                    ? "-"
                                    : lastSeen.toInstant().atZone(ZoneId.systemDefault()).format(DISPLAY_TIME_FORMAT)
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Spacecraft catalog suggestion search failed for query {}", query, e);
        }

        return results;
    }

    public List<CacheStat> getCacheStats() {
        if (!enabled) {
            return List.of();
        }

        String responseStatsSql = """
                SELECT source,
                       COUNT(*) AS total_entries,
                       COUNT(*) FILTER (WHERE expires_at > CURRENT_TIMESTAMP) AS fresh_entries
                FROM api_response_cache
                GROUP BY source
                ORDER BY source
                """;
        String catalogStatsSql = "SELECT COUNT(*) AS total_entries FROM spacecraft_catalog";

        List<CacheStat> stats = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement responseStats = connection.prepareStatement(responseStatsSql);
             PreparedStatement catalogStats = connection.prepareStatement(catalogStatsSql);
             ResultSet responseRows = responseStats.executeQuery()) {
            while (responseRows.next()) {
                long total = responseRows.getLong("total_entries");
                long fresh = responseRows.getLong("fresh_entries");
                stats.add(new CacheStat(responseRows.getString("source"), total, fresh, total - fresh));
            }

            try (ResultSet catalogRows = catalogStats.executeQuery()) {
                if (catalogRows.next()) {
                    long catalogEntries = catalogRows.getLong("total_entries");
                    stats.add(new CacheStat("spacecraft_catalog", catalogEntries, catalogEntries, 0));
                }
            }
        } catch (Exception e) {
            log.warn("Cache stats lookup failed", e);
        }

        return stats;
    }

    private void initializeSchema() {
        String cacheTableSql = """
                CREATE TABLE IF NOT EXISTS api_response_cache (
                    source TEXT NOT NULL,
                    cache_key TEXT NOT NULL,
                    response_json TEXT NOT NULL,
                    fetched_at TIMESTAMP NOT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (source, cache_key)
                )
                """;

        String catalogTableSql = """
                CREATE TABLE IF NOT EXISTS spacecraft_catalog (
                    norad_id INTEGER PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    normalized_name TEXT NOT NULL,
                    source TEXT NOT NULL,
                    last_seen_at TIMESTAMP NOT NULL
                )
                """;

        String catalogIndexSql = """
                CREATE INDEX IF NOT EXISTS idx_spacecraft_catalog_normalized_name
                ON spacecraft_catalog (normalized_name)
                """;

        try (Connection connection = openConnection();
             PreparedStatement cacheTable = connection.prepareStatement(cacheTableSql);
             PreparedStatement catalogTable = connection.prepareStatement(catalogTableSql);
             PreparedStatement catalogIndex = connection.prepareStatement(catalogIndexSql)) {
            cacheTable.execute();
            catalogTable.execute();
            catalogIndex.execute();
        } catch (Exception e) {
            log.warn("Cache schema initialization failed", e);
        }
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(jdbcUrl, username, password == null ? "" : password);
    }

    private String normalizeName(String input) {
        String lower = input.toLowerCase();
        return lower.replaceAll("[^a-z0-9]+", " ").trim();
    }
}
