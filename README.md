# APCSA Final Project

Space Mission and Event Tracker (website, with possible mobile app version later).

## What it does
- Pulls live data from space APIs
- Lets users search/filter missions and events

## APIs
- The Space Devs Launch Library 2
- NASA Open APIs
- AstronomyAPI

## Tech
- Java 21
- Spring Boot + Thymeleaf
- Maven
- Mobile app version (possible future phase)

## Goal
Make space mission + astronomy event info easy to view in one place.

## Run locally
- Put your NASA key in `.env` or export `NASA_API_KEY`
- Start app: `mvn spring-boot:run`
- Open: `http://localhost:8080`
