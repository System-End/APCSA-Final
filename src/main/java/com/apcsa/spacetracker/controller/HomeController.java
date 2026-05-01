package com.apcsa.spacetracker.controller;

import com.apcsa.spacetracker.model.ApodResponse;
import com.apcsa.spacetracker.service.NasaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final NasaService nasaService;

    public HomeController(NasaService nasaService) {
        this.nasaService = nasaService;
    }

    @GetMapping("/")
    public String home(Model model) {
        try {
            // The landing page centers on NASA's photo of the day.
            ApodResponse apod = nasaService.getApod();
            model.addAttribute("apod", apod);
        } catch (Exception ex) {
            model.addAttribute("error", "Could not load NASA APOD right now.");
        }

        return "index";
    }
}
