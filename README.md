# India AQI API
 
A REST API exposing clean, structured air quality data for India — built by connecting to [OpenAQ](https://openaq.org)'s real-time global air quality monitoring network.
 
## Why I built this
 
Most beginner projects are CRUD apps or clones of existing tools. I wanted something that actually deals with real-world data from a live external API — parsing it, understanding its structure, and turning it into something clean and usable, then actually serving it as a real API rather than just printing results to a console.
 
## What I learned building this
 
- How to authenticate with a real external API using an API key
- Why secrets should never be committed to git — I accidentally committed my `.env` file due to a `.gitignore` misconfiguration, caught it via a GitGuardian security alert, rotated the leaked key, and cleaned it from git history
- How to parse AND generate JSON in Java without external libraries, by writing a small custom parser/serializer (`MiniJson.java`)
- OpenAQ's data model: locations (stations) → sensors → parameters
- The difference between a script that runs once and an actual server: how to use Java's built-in `HttpServer` to keep a program running and respond to live HTTP requests
- Real data is messy even after "cleaning" it — for example, the `locality` field for Indian stations consistently comes back `null` from OpenAQ. Rather than hide that, I'm treating it as a known limitation to fix (likely by deriving city names from station names) in a later pass
## Status: 🚧 In progress
 
- [x] Connected to OpenAQ API and authenticated with an API key
- [x] Parsed real India monitoring station data (name, coordinates, pollutants measured)
- [x] Built a REST API server with a live `/stations` endpoint using Java's built-in `HttpServer`
- [ ] Add more endpoints (e.g. filter stations by city)
- [ ] Clean up known data issues (missing `locality` values)
- [ ] Deploy live (currently only runs on localhost)
- [ ] Build a minimal frontend
- [ ] Final polish + documentation
## Tech stack
 
- **Java** — core language, built-in `HttpClient` and `HttpServer` (no external frameworks or libraries)
- **OpenAQ API v3** — real-time air quality data source
- Custom-built JSON parser/serializer (`MiniJson.java`) — since Java has no built-in JSON support
## How to run this
 
1. Clone the repo and `cd` into it:
```bash
   git clone https://github.com/IRONVOID/india-aqi-api.git
   cd india-aqi-api
```
 
2. Get a free API key from [OpenAQ](https://explore.openaq.org/register)
3. Create a `.env` file in the project root:
```
   OPENAQ_API_KEY=your_key_here
```
 
4. Compile the server and its dependencies:
```bash
   javac MiniJson.java OpenAQClient.java Server.java
```
 
5. Run the server:
```bash
   java Server
```
 
6. While the server is running, visit in your browser:
```
   http://localhost:8080/stations
```
   This returns a live JSON list of Indian air quality monitoring stations, including their name, coordinates, and which pollutants they measure.
 
   Note: the server needs to keep running in its terminal for this URL to work — closing the terminal stops the server.
 
## Data source
 
This project uses the [OpenAQ API](https://docs.openaq.org/), a nonprofit, open-access platform aggregating ground-level air quality data from government and research sources worldwide, including India.