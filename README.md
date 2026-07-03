# India AQI API

A backend REST API and live dashboard for monitoring air quality across India, built from scratch in Java — no Spring Boot, no Maven/Gradle, no external libraries. Just Java's built-in `HttpServer`, a hand-written JSON parser, and the [OpenAQ](https://openaq.org) API.

This project was built deliberately without frameworks in order to learn backend fundamentals — HTTP handling, routing, JSON serialization, and clean architecture — before reaching for tools that hide those details.

```
http://localhost:8080/
```

![status](https://img.shields.io/badge/status-active--development-9e1b32) ![java](https://img.shields.io/badge/java-17%2B-informational)

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Frontend Dashboard](#frontend-dashboard)
- [Known Issues & Limitations](#known-issues--limitations)
- [Roadmap](#roadmap)

---

## Features

- **Live station data** for ~50 air quality monitoring stations across India, sourced from OpenAQ
- **Search & filtering** by station name, locality, and pollutant — fully composable (combine all three in one request)
- **Sorting** by name, locality, or pollutant coverage
- **Pagination** with configurable page size
- **Live pollutant readings** per station, correctly labeled (OpenAQ's raw API only returns anonymous sensor IDs — this project maps them back to real pollutant names)
- **Pollution analysis** (the flagship feature): compares live readings against India's NAAQS safe limits and returns a health status + recommendation per pollutant
- **A full dashboard frontend** — vanilla HTML/CSS/JS, no frameworks, served from the same Java backend

---

## Architecture

The backend follows a simple layered structure: HTTP handling is fully separated from business logic.

| Layer | File | Responsibility |
|---|---|---|
| HTTP / Routing | `Server.java` | Starts the server, routes requests, reads query strings, writes JSON responses. Contains **no business logic**. |
| Data access | `OpenAQClient.java` | Talks to the OpenAQ API. Fetches and cleans station data, caches a `sensorId → pollutant name` lookup table. |
| JSON | `MiniJson.java` | Hand-written JSON parser and serializer. No Gson, no Jackson. |
| Business logic | `StationService.java` | Filtering, sorting, and pagination for `/stations`. |
| Business logic | `StatsService.java` | Aggregate statistics for `/stats`. |
| Business logic | `LiveMeasurementService.java` | Fetches and labels live sensor readings for a station. |
| Business logic | `PollutionAnalysisService.java` | Compares readings against safe limits and produces health recommendations. |
| Frontend | `public/` | Static dashboard (HTML/CSS/JS), served directly by `Server.java`. |

**Why this split matters:** each service class takes plain data in and returns a plain `Map` out — none of them know anything about HTTP. `Server.java`'s job is only to call the right service and serialize the result. This means adding a new filter, sort option, or analysis rule never requires touching routing code, and vice versa.

### Request flow for `/stations`

```
Server.java (routing)
      │
      ▼
StationService.getStations(stationsCache, query)
      │
      ├─ applyFilters()      → pollutant=, name=, locality=
      ├─ applySorting()      → sort=name|locality|pollutantCount
      └─ applyPagination()   → page=, limit=
      │
      ▼
{ "data": [...], "pagination": {...} }
```

### Request flow for `/analysis`

```
Server.java
      │
      ▼
PollutionAnalysisService.getAnalysis(stationsCache, stationId)
      │
      ▼
LiveMeasurementService.getLiveMeasurements(...)
      │
      ├─ OpenAQClient.fetchLatestMeasurements(stationId)   → raw OpenAQ readings
      └─ OpenAQClient.lookupPollutantName(stationId, sensorId)  → labels each reading
      │
      ▼
keep only the most recent reading per pollutant
      │
      ▼
compare against NAAQS safe limits → status + recommendation
```

---

## Project Structure

```
india-api-aqi/
├── Server.java                      # HTTP routing only
├── OpenAQClient.java                 # OpenAQ API integration + sensor lookup cache
├── MiniJson.java                     # Custom JSON parser/serializer
├── StationService.java               # Filtering, sorting, pagination
├── StatsService.java                 # Aggregate stats
├── LiveMeasurementService.java       # Live sensor readings
├── PollutionAnalysisService.java     # Flagship: safe-limit comparison + recommendations
├── public/
│   ├── index.html                    # Dashboard markup
│   ├── style.css                     # Matte black / cherry red theme
│   └── app.js                        # Dashboard logic (fetch, render, chart)
├── .env                               # OPENAQ_API_KEY (not committed)
└── README.md
```

---

## Getting Started

### Prerequisites

- Java 17 or later (uses `java.net.http.HttpClient` and `com.sun.net.httpserver.HttpServer`, both standard library)
- A free [OpenAQ API key](https://explore.openaq.org/register)

### Setup

1. Clone the repository
2. Create a `.env` file in the project root:
   ```
   OPENAQ_API_KEY=your_key_here
   ```
3. Compile and run:
   ```bash
   javac *.java
   java Server
   ```
4. Open the dashboard:
   ```
   http://localhost:8080/
   ```

The server fetches and caches ~50 Indian stations from OpenAQ on startup, so the first request may take a couple of seconds.

---

## API Reference

All responses are JSON. All endpoints are `GET`.

### `GET /stations`

Returns a paginated, filterable list of stations.

| Query param | Type | Description |
|---|---|---|
| `name` | string | Case-insensitive substring match on station name |
| `pollutant` | string | Exact match (case-insensitive) on a pollutant, e.g. `pm25` |
| `locality` | string | Case-insensitive substring match on locality. **Falls back to matching station name** when locality is `null` (see [Known Issues](#known-issues--limitations)) |
| `sort` | string | `name`, `locality`, or `pollutantCount` (descending) |
| `page` | integer | Default `1` |
| `limit` | integer | Default `10`, capped at `100` |

All filters are composable and apply in this order: **filter → sort → paginate**.

**Example:**
```
GET /stations?locality=Delhi&pollutant=pm25&sort=pollutantCount&page=1&limit=10
```

```json
{
  "data": [
    {
      "id": 17,
      "name": "R K Puram, Delhi - DPCC",
      "locality": null,
      "latitude": 28.563262,
      "longitude": 77.186937,
      "pollutants": ["co", "no", "no2", "nox", "o3", "pm10", "pm25", "so2", "..."]
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 10,
    "totalResults": 10,
    "totalPages": 1
  }
}
```

### `GET /stats`

Aggregate statistics across all cached stations.

```json
{
  "totalStations": 50,
  "pm25Stations": 48,
  "coStations": 44,
  "no2Stations": 42,
  "totalPollutantTypes": 12
}
```

### `GET /live?id=<stationId>`

Latest raw sensor readings for one station, labeled with pollutant names.

| Response | Meaning |
|---|---|
| `200` | Returns `{ stationId, stationName, measurements: [...] }` |
| `400` | Missing or non-numeric `id` |
| `404` | No station exists with that ID |

**Example:**
```
GET /live?id=17
```
```json
{
  "stationId": 17,
  "stationName": "R K Puram, Delhi - DPCC",
  "measurements": [
    { "pollutant": "pm25", "value": 125.0, "datetime": "2026-07-02T17:30:00Z" },
    { "pollutant": "co", "value": 0.76, "datetime": "2026-07-02T18:15:00Z" }
  ]
}
```

### `GET /analysis?id=<stationId>` — flagship endpoint

Compares the most recent reading for each analyzable pollutant against India's NAAQS safe limits.

**Analyzed pollutants:** PM2.5, PM10, NO2, SO2, O3 (see [Known Issues](#known-issues--limitations) for why CO and meteorological readings are excluded).

| Response | Meaning |
|---|---|
| `200` | Returns `{ stationId, stationName, analysis: [...] }` |
| `400` | Missing or non-numeric `id` |
| `404` | No station exists with that ID |

**Example:**
```
GET /analysis?id=17
```
```json
{
  "stationId": 17,
  "stationName": "R K Puram, Delhi - DPCC",
  "analysis": [
    {
      "pollutant": "pm25",
      "currentValue": 125.0,
      "safeLimit": 60.0,
      "exceededBy": 65.0,
      "status": "Very Unhealthy",
      "recommendation": "Health warning: avoid outdoor activity. Consider wearing a mask if you must go outside.",
      "datetime": "2026-07-02T17:30:00Z"
    }
  ]
}
```

**Status scale** (based on ratio of current value to safe limit — a simplified classification inspired by standard AQI categories, not an official AQI breakpoint calculation):

| Status | Ratio to safe limit |
|---|---|
| Good | ≤ 1.0 |
| Moderate | ≤ 1.25 |
| Unhealthy for Sensitive Groups | ≤ 1.5 |
| Unhealthy | ≤ 2.0 |
| Very Unhealthy | > 2.0 |

---

## Frontend Dashboard

Served at `/` directly by the Java backend — no separate frontend server, no CORS configuration needed, since both frontend and API share the same origin (`localhost:8080`).

- **Vanilla JS**, no build step, no frameworks
- Debounced live search, pollutant filter, sort, and pagination
- A canvas-drawn bar chart (no charting library) showing sensor coverage per pollutant, pulled from `/stats`
- Each station card can expand into a live "readout panel" — fetches `/analysis` on demand and displays a color-coded status pill plus per-pollutant values
- Visual design: matte black background with a cherry red accent, IBM Plex Mono for data readouts (instrument-panel style), IBM Plex Sans for UI text

---

## Known Issues & Limitations

This section documents real problems found and fixed during development, and limitations that are known and intentional rather than accidental.

### Fixed during development

| Issue | Cause | Fix |
|---|---|---|
| **Duplicate pollutants per station** | A station can have multiple sensors measuring the same pollutant, producing duplicate entries in the `pollutants` list | De-duplicated using a `LinkedHashSet` in `OpenAQClient.fetchIndiaStations()` |
| **`locality` filter returned zero results** | OpenAQ does not reliably populate the `locality` field for Indian stations — it's `null` for every station in the dataset | `locality` filter and sort both fall back to matching the station **name** field when `locality` is `null`, since names usually contain the city (e.g. `"IGI Airport"`, `"Civil Lines"`) |
| **Contradictory pollution analysis for R K Puram** | Some stations have two sensors reporting the same pollutant — one current, one years-stale (e.g. a PM2.5 reading from 2018 sitting alongside one from today) | `PollutionAnalysisService` keeps only the **most recent** reading per pollutant, comparing ISO 8601 UTC timestamps as strings (which sort correctly without needing a date library) |
| **Dashboard couldn't fetch from the API** | Opening the dashboard as a standalone HTML file (`file://`) triggers CORS restrictions when calling `http://localhost:8080` | Dashboard is served **by the same Java server** via `StaticFileHandler`, making all requests same-origin |
| **Duplicated response-writing code across handlers** | Every handler independently wrote the same "set headers, write bytes, close stream" block | Extracted into a shared `sendJson()` helper in `Server.java` |

### Known limitations (by design, not bugs)

- **CO is excluded from pollution analysis.** OpenAQ providers report CO inconsistently — sometimes in ppm, sometimes in µg/m³ — and comparing it directly against a µg/m³ safe limit without knowing the actual unit would produce misleading results. Rather than guess, CO is left out of `/analysis` until per-sensor unit data is incorporated.
- **Meteorological readings** (`temperature`, `relativehumidity`, `wind_speed`, `wind_direction`) are not pollutants and are excluded from analysis, though they're still visible in `/live` and station pollutant lists.
- **"Latest" data can be stale.** OpenAQ's `/latest` endpoint is not guaranteed to be recent — some sensors haven't reported in years. The most-recent-reading-per-pollutant logic mitigates duplicate/contradictory readings, but a station showing very old data will still return that data (there is currently no explicit "this reading is X days old, treat with caution" warning).
- **Units are assumed, not verified per-reading.** Safe limits assume PM2.5, PM10, NO2, SO2, and O3 are reported in µg/m³, consistent with typical Indian CPCB reporting. This is not verified against each sensor's actual `units` field.
- **The AQI-style status scale is a simplified approximation.** It classifies severity based on the ratio of current value to safe limit, not the official multi-pollutant AQI breakpoint tables used by CPCB/EPA.

---

## Roadmap

- [x] OpenAQ integration
- [x] REST API (Java `HttpServer`, no frameworks)
- [x] Search & filtering (name, pollutant, locality)
- [x] Statistics endpoint
- [x] Locality search (with name fallback)
- [x] Pagination
- [x] Sorting
- [x] Refactor into service layer
- [x] Live measurements endpoint
- [x] Pollution analysis (flagship feature)
- [x] Frontend dashboard
- [ ] Deployment — public backend + frontend URL

---

## Tech Stack

- **Language:** Java 17+
- **HTTP server:** `com.sun.net.httpserver.HttpServer` (JDK standard library)
- **HTTP client:** `java.net.http.HttpClient` (JDK standard library)
- **JSON:** hand-written parser/serializer (`MiniJson.java`) — no Gson, no Jackson
- **Build tool:** none — plain `javac`
- **Frontend:** HTML, CSS, vanilla JavaScript — no React, no build step
- **Data source:** [OpenAQ v3 API](https://docs.openaq.org)

---

## License

MIT — see [LICENSE](./LICENSE) for details.

Copyright (c) 2026 Akshit Tanwar

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files, to deal in the software without restriction, including the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies, subject to including the above copyright notice in all copies. The software is provided "as is", without warranty of any kind.