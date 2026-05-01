package com.apcsa.spacetracker.controller;

import com.apcsa.spacetracker.service.MissionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MissionController {
    private final MissionService missionService;

    public MissionController(MissionService missionService) {
        this.missionService = missionService;
    }

    @GetMapping("/missions")
    public String missions(
            @RequestParam(required = false) String agency,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer year,
            Model model
    ) {
        try {
            // Missions are fetched once, then filtered by whatever fields the user filled in.
            model.addAttribute("missions", missionService.getUpcomingMissions(agency, status, year));
        } catch (Exception ex) {
            model.addAttribute("error", "Could not load mission data right now.");
        }

        model.addAttribute("agency", agency == null ? "" : agency);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("year", year == null ? "" : year);

        return "missions";
    }
}
