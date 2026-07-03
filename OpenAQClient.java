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

    /**
     * Lookup table built while fetching stations: for each station ID,
     * maps sensorId -> pollutant name.
     *
     * Why this exists: OpenAQ's "/latest" endpoint (used for live
     * measurements) only returns a raw sensorsId with each reading —
     * it does NOT tell you which pollutant that sensor measures. The
     * only place that mapping is available is the sensor list returned
     * by "/locations", which we already read once in fetchIndiaStations()
     * to build the "pollutants" list. Instead of throwing that mapping
     * away, we cache it here so live measurements can be labeled correctly
     * later without a second API call.
     */
    static Map<Integer, Map<Integer, String>> sensorLookup = new LinkedHashMap<>();

    /**
     * Reads a config value, checking real environment variables first
     * (used in production — e.g. a key set in Render's dashboard),
     * then falling back to a local ".env" file (used in local
     * development). This means the exact same code works both on
     * your machine and once deployed, with no changes needed.
     */
    static String getEnvValue(String key) throws IOException {

        String fromEnvironment = System.getenv(key);

        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }

        Path envFile = Paths.get(".env");

        if (!Files.exists(envFile)) {
            throw new IOException(
                    "Could not find '" + key + "' as an environment variable, "
                            + "and no .env file exists. In production, set it as an "
                            + "environment variable on your hosting platform. For local "
                            + "development, create a .env file with " + key + "=your_value");
        }

        List<String> lines = Files.readAllLines(envFile);

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
     * Converts a JSON-parsed number (often a Double, e.g. 236.0) into a
     * plain int. Centralized here so every place that needs a station ID
     * or sensor ID as an int handles it the same way.
     */
    static int toInt(Object obj) {

        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }

        return Integer.parseInt(String.valueOf(obj));
    }

    /**
     * Looks up the pollutant name for a given station + sensor combination,
     * using the lookup table built during fetchIndiaStations().
     * Returns "unknown" if we don't have a mapping (e.g. cache not yet
     * loaded, or a sensor that wasn't part of the original station data).
     */
    static String lookupPollutantName(int stationId, int sensorId) {

        Map<Integer, String> stationSensors = sensorLookup.get(stationId);

        if (stationSensors == null) {
            return "unknown";
        }

        return stationSensors.getOrDefault(sensorId, "unknown");
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

            // NEW: sensorId -> pollutant name, for this station.
            // Filled in alongside uniquePollutants below, using the same
            // sensor loop, so there's no extra API calls needed.
            Map<Integer, String> sensorIdToPollutant = new LinkedHashMap<>();

            for (Object sensorObj : sensors) {

                Map<String, Object> sensor =
                        asMap(sensorObj);

                Map<String, Object> parameter =
                        asMap(sensor.get("parameter"));

                if (parameter != null && parameter.get("name") != null) {

                    String pollutantName =
                            parameter.get("name").toString().toLowerCase();

                    uniquePollutants.add(pollutantName);

                    Object sensorIdValue = sensor.get("id");

                    if (sensorIdValue != null) {
                        sensorIdToPollutant.put(
                                toInt(sensorIdValue),
                                pollutantName
                        );
                    }
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

            // Cache the sensor lookup table for this station so
            // lookupPollutantName() can use it later.
            if (station.get("id") != null) {
                sensorLookup.put(toInt(station.get("id")), sensorIdToPollutant);
            }
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