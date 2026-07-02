# India AQI API

A REST API exposing clean, structured air quality data for India — built by connecting to [OpenAQ](https://openaq.org)'s real-time global air quality monitoring network.

## Why I built this

Most beginner projects are CRUD apps or clones of existing tools. I wanted something that actually deals with real-world data from a live external API — parsing it, understanding its structure, and eventually turning it into something clean and usable.

## What I learned building this

- How to authenticate with a real external API using an API key
- Why secrets should never be committed to git — I accidentally committed my `.env` file due to a `.gitignore` misconfiguration, caught it via a GitGuardian security alert, rotated the leaked key, and cleaned it from git history
- How to parse JSON in Java without external libraries, by writing a small custom parser
- OpenAQ's data model: locations (stations) → sensors → parameters

## Status: 🚧 In progress (Day 2/7)

- [x] Connected to OpenAQ API and authenticated with an API key
- [x] Parsed real India monitoring station data (name, coordinates, pollutants measured)
- [ ] Store cleaned data in a database
- [ ] Build REST API endpoints
- [ ] Deploy live

## Tech stack

- **Java** — core language, built-in `HttpClient`, custom JSON parser
- **OpenAQ API v3** — real-time air quality data source

## How to run this

1. Clone the repo and `cd` into it
2. Get a free API key from [OpenAQ](https://explore.openaq.org/register)
3. Create a `.env` file with: `OPENAQ_API_KEY=your_key_here`
4. Compile: `javac MiniJson.java Stations.java`
5. Run: `java Stations`