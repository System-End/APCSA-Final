# SpaceTracker

A Java/Spring Boot site for launches, sky position, astronomy events, and cached spacecraft lookups.

## Features
- NASA APOD on the home page
- Upcoming launches from Launch Library 2 with agency/status/year filters
- Sky object positions for a specific location and time
- Spacecraft position and visible pass checks
- Astronomy events page for sun and moon event data
- Postgres-backed API caching and local spacecraft lookup/autocomplete

## APIs
- NASA APOD
- The Space Devs Launch Library 2
- AstronomyAPI
- N2YO
- WhereTheISS
- Nominatim (OpenStreetMap geocoding)


## Environment
Set these in `.env` locally or in your deploy env file:

```env
NASA_API_KEY=
ASTRONOMY_API_APP_ID=
ASTRONOMY_API_APP_SECRET=
NY2O_API_KEY=
POSTGRES_JDBC_URL=jdbc:postgresql://localhost:5432/spacetracker
POSTGRES_USERNAME=
POSTGRES_PASSWORD=
PORT=8080
```

## Run locally
1. Make sure Postgres is running.
2. Create the `spacetracker` database.
3. Fill in the env vars above.
4. Start the app:

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`.

## Cache Notes
- API responses are cached in Postgres table `api_response_cache`
- Spacecraft names/IDs are stored in `spacecraft_catalog`
- Cache tables are auto-created on startup when Postgres env vars are present
