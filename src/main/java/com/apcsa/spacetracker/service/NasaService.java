package com.apcsa.spacetracker.service;

import com.apcsa.spacetracker.model.ApodResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class NasaService {
    private final RestTemplate restTemplate;
    private final String nasaApiKey;

    public NasaService(@Value("${nasa.api.key}") String nasaApiKey) {
        this.restTemplate = new RestTemplate();
        this.nasaApiKey = nasaApiKey;
    }

    public ApodResponse getApod() {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://api.nasa.gov/planetary/apod")
                .queryParam("api_key", nasaApiKey)
                .toUriString();

        return restTemplate.getForObject(url, ApodResponse.class);
    }
}
