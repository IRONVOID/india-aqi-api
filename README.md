# 🌍 India AQI API

A Java REST API that fetches, cleans, and serves air quality monitoring station data from the OpenAQ API.

This project focuses on backend development, REST API design, JSON processing, and data filtering without using external frameworks like Spring Boot.

---

## ✨ Features

- 🌐 Fetches live monitoring station data from the OpenAQ API
- 📦 Custom JSON parser and serializer (MiniJson)
- 🚀 Lightweight Java HTTP Server using `com.sun.net.httpserver`
- 🔍 Search monitoring stations by name
- 🌫️ Filter stations by pollutant
- 📊 Statistics endpoint for pollutant coverage
- 🧹 Automatic duplicate pollutant removal
- ⚡ In-memory caching for faster responses
- 🔑 Secure API key management using `.env`

---

## 📌 Current API Endpoints

### Get all stations

```http
GET /stations
```

Example:

```
http://localhost:8080/stations
```

---

### Search by station name

```http
GET /stations?name=airport
```

Example:

```
http://localhost:8080/stations?name=airport
```

---

### Filter by pollutant

```http
GET /stations?pollutant=pm25
```

Example:

```
http://localhost:8080/stations?pollutant=pm25
```

---

### Combine filters

```http
GET /stations?name=delhi&pollutant=pm25
```

---

### Statistics

```http
GET /stats
```

Returns information such as:

- Total stations
- Stations monitoring PM2.5
- Stations monitoring CO
- Stations monitoring NO₂
- Total pollutant types

---

## 🏗️ Project Structure

```
india-aqi-api/
│
├── Server.java          # REST API server
├── OpenAQClient.java    # OpenAQ API communication
├── MiniJson.java        # Custom JSON parser & serializer
├── Stations.java
├── Explore.java
├── .env
└── README.md
```

---

## 🛠️ Technologies Used

- Java 24
- Java HTTP Server
- Java HttpClient
- OpenAQ API
- REST API Design
- Git
- GitHub

---

## 🚀 Getting Started

### Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/india-aqi-api.git
```

### Compile

```bash
javac *.java
```

### Run

```bash
java Server
```

The server starts at

```
http://localhost:8080
```

---

## 📈 Current Progress

- ✅ OpenAQ API integration
- ✅ REST API server
- ✅ Custom JSON parser
- ✅ Station search
- ✅ Pollutant filtering
- ✅ Statistics endpoint
- ✅ Duplicate pollutant cleanup
- 🚧 Locality search
- 🚧 Pagination
- 🚧 Sorting
- 🚧 Live AQI measurements
- 🚧 AQI health analysis
- 🚧 Frontend dashboard

---

## 🎯 Future Improvements

- Search by locality
- Pagination support
- Sorting support
- Live pollutant measurements
- AQI calculation
- Health recommendations
- Interactive frontend dashboard
- Deployment to the cloud

---

## 👨‍💻 Author

**Akshit Tanwar**

B.Tech Computer Science & Engineering

Building backend projects to strengthen Java and REST API development skills.