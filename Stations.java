import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;

/*
 * Day 2 — Fetch and parse real India monitoring station data
 *
 * What's new compared to Day 1:
 *   - Day 1 just printed raw JSON text
 *   - Today we actually PARSE that JSON into real Java objects (using
 *     MiniJson.java) and pull out the specific fields we care about:
 *     station name, city, coordinates, and which pollutants it measures
 *
 * Compile:
 *     javac MiniJson.java Stations.java
 * Run:
 *     java Stations
 */
public class Stations {

    static final String INDIA_COUNTRY_ID = "9"; // from OpenAQ docs (country code "IN")

    static String getEnvValue(String key) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(".env"));
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("=", 2);
            if (parts.length == 2 && parts[0].trim().equals(key)) {
                return parts[1].trim();
            }
        }
        throw new IOException("Key '" + key + "' not found in .env file");
    }

    static String fetch(String path, String apiKey) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openaq.org/v3" + path))
                .header("X-API-Key", apiKey)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Request failed with status " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    // Small helper: safely pull a nested Map from a parsed JSON object, or null if missing.
    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object obj) {
        return obj == null ? null : (Map<String, Object>) obj;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object obj) {
        return obj == null ? Collections.emptyList() : (List<Object>) obj;
    }

    public static void main(String[] args) {
        try {
            String apiKey = getEnvValue("OPENAQ_API_KEY");

            System.out.println("Fetching monitoring stations in India...\n");
            String json = fetch("/locations?countries_id=" + INDIA_COUNTRY_ID + "&limit=15", apiKey);

            Map<String, Object> data = asMap(MiniJson.parse(json));
            List<Object> results = asList(data.get("results"));

            System.out.println("Found " + results.size() + " stations (showing this page)\n");
            System.out.println("----------------------------------------");

            for (Object item : results) {
                Map<String, Object> station = asMap(item);

                String name = (String) station.get("name");
                String locality = (String) station.get("locality");

                Map<String, Object> coords = asMap(station.get("coordinates"));
                Object lat = coords != null ? coords.get("latitude") : null;
                Object lon = coords != null ? coords.get("longitude") : null;

                List<Object> sensors = asList(station.get("sensors"));
                List<String> pollutants = new ArrayList<>();
                for (Object sensorObj : sensors) {
                    Map<String, Object> sensor = asMap(sensorObj);
                    Map<String, Object> parameter = asMap(sensor.get("parameter"));
                    if (parameter != null) {
                        pollutants.add((String) parameter.get("name"));
                    }
                }

                System.out.println("Station: " + name);
                System.out.println("  Locality: " + (locality != null ? locality : "unknown"));
                System.out.println("  Coordinates: " + lat + ", " + lon);
                System.out.println("  Measures: " + String.join(", ", pollutants));
                System.out.println("----------------------------------------");
            }

        } catch (Exception e) {
            System.out.println("Something went wrong:");
            e.printStackTrace();
        }
    }
}