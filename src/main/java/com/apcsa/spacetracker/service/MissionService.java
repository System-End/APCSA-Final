package com.apcsa.spacetracker.service;

import com.apcsa.spacetracker.model.Mission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class MissionService {
    private static final String UPCOMING_LAUNCHES_URL = "https://ll.thespacedevs.com/2.3.0/launches/upcoming/?limit=50";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public MissionService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public List<Mission> getUpcomingMissions(String agencyFilter, String statusFilter, Integer yearFilter) {
        List<Mission> missions = fetchUpcomingMissions();
        List<Mission> filtered = new ArrayList<>();

        for (Mission mission : missions) {
            if (!matchesAgency(mission, agencyFilter)) {
                continue;
            }
            if (!matchesStatus(mission, statusFilter)) {
                continue;
            }
            if (!matchesYear(mission, yearFilter)) {
                continue;
            }
            filtered.add(mission);
        }

        return filtered;
    }

    private List<Mission> fetchUpcomingMissions() {
        List<Mission> missions = new ArrayList<>();
        String json = restTemplate.getForObject(UPCOMING_LAUNCHES_URL, String.class);

        if (json == null || json.isBlank()) {
            return missions;
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                return missions;
            }

            for (JsonNode launch : results) {
                String name = launch.path("name").asText("Unknown");
                String agency = launch.path("launch_service_provider").path("name").asText("Unknown");
                String status = launch.path("status").path("name").asText("Unknown");
                String launchDate = launch.path("net").asText("Unknown");
                String location = launch.path("pad").path("location").path("name").asText("Unknown");
                String missionType = launch.path("mission").path("type").asText("Unknown");
                int year = parseYear(launchDate);

                missions.add(new Mission(name, agency, status, launchDate, location, missionType, year));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Launch Library response", e);
        }

        return missions;
    }

    private int parseYear(String launchDate) {
        if (launchDate == null || launchDate.length() < 4) {
            return -1;
        }
        try {
            return Integer.parseInt(launchDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private boolean matchesAgency(Mission mission, String agencyFilter) {
        if (agencyFilter == null || agencyFilter.isBlank()) {
            return true;
        }
        return mission.getAgency().toLowerCase().contains(agencyFilter.toLowerCase());
    }

    private boolean matchesStatus(Mission mission, String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return true;
        }
        return mission.getStatus().toLowerCase().contains(statusFilter.toLowerCase());
    }

    private boolean matchesYear(Mission mission, Integer yearFilter) {
        if (yearFilter == null) {
            return true;
        }
        return mission.getYear() == yearFilter;
    }
}
