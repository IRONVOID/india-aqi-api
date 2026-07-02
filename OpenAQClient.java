import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;

/*
 * OpenAQClient — reusable code for talking to the OpenAQ API.
 */
public class OpenAQClient {

    static final String INDIA_COUNTRY_ID = "9";

    static String getEnvValue(String key) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(".env"));

        for (String line : lines) {
            line = line.trim();

            if (line.isEmpty() || line.startsWith("#"))
                continue;

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

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Request failed with status "
                            + response.statusCode()
                            + ": "
                            + response.body());
        }

        return response.body();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object obj) {
        return obj == null ? null : (Map<String, Object>) obj;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object obj) {
        return obj == null ? Collections.emptyList() : (List<Object>) obj;
    }

    /**
     * Fetches India monitoring stations.
     */
    static List<Object> fetchIndiaStations() throws Exception {

        String apiKey = getEnvValue("OPENAQ_API_KEY");

        String json = fetch(
                "/locations?countries_id="
                        + INDIA_COUNTRY_ID
                        + "&limit=50",
                apiKey);

        Map<String, Object> data = asMap(MiniJson.parse(json));

        List<Object> rawResults = asList(data.get("results"));

        List<Object> cleaned = new ArrayList<>();

        for (Object item : rawResults) {

            Map<String, Object> station = asMap(item);

            Map<String, Object> coords =
                    asMap(station.get("coordinates"));

            Object lat = coords != null ? coords.get("latitude") : null;
            Object lon = coords != null ? coords.get("longitude") : null;

            List<Object> sensors =
                    asList(station.get("sensors"));

            // FIX: Remove duplicate pollutants
            Set<String> uniquePollutants = new LinkedHashSet<>();

            for (Object sensorObj : sensors) {

                Map<String, Object> sensor =
                        asMap(sensorObj);

                Map<String, Object> parameter =
                        asMap(sensor.get("parameter"));

                if (parameter != null && parameter.get("name") != null) {

                    uniquePollutants.add(
                            parameter.get("name").toString().toLowerCase()
                    );
                }
            }

            List<Object> pollutants = new ArrayList<>(uniquePollutants);

            Map<String, Object> cleanStation =
                    new LinkedHashMap<>();

            cleanStation.put("id", station.get("id"));
            cleanStation.put("name", station.get("name"));
            cleanStation.put("locality", station.get("locality"));
            cleanStation.put("latitude", lat);
            cleanStation.put("longitude", lon);
            cleanStation.put("pollutants", pollutants);

            cleaned.add(cleanStation);
        }

        return cleaned;
    }

    /**
     * Fetches the latest measurements for one location.
     */
    static List<Object> fetchLatestMeasurements(int locationId) throws Exception {

    String apiKey = getEnvValue("OPENAQ_API_KEY");

    String json = fetch("/locations/" + locationId + "/latest", apiKey);

    Map<String, Object> data = asMap(MiniJson.parse(json));

    return asList(data.get("results"));
}
}