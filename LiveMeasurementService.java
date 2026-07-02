import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * LiveMeasurementService — owns the business logic for the /live endpoint.
 *
 * Given a station ID, this fetches that station's latest raw measurements
 * from OpenAQ and turns them into clean, labeled readings using the
 * sensorId -> pollutant name lookup built in OpenAQClient.
 *
 * Like StationService and StatsService, this class knows nothing about
 * HTTP — it just returns a plain Map (or null if the station doesn't
 * exist), and Server.java decides how to turn that into a response.
 */
public class LiveMeasurementService {

    /**
     * Returns live measurement data for the given station ID, or null if
     * no station with that ID exists in the cache (Server.java turns a
     * null result into a 404).
     */
    public static Map<String, Object> getLiveMeasurements(
            List<Object> stationsCache, int stationId) throws Exception {

        Map<String, Object> station = findStationById(stationsCache, stationId);

        if (station == null) {
            return null;
        }

        List<Object> rawResults =
                OpenAQClient.fetchLatestMeasurements(stationId);

        List<Object> measurements = new ArrayList<>();

        for (Object obj : rawResults) {

            Map<String, Object> reading = OpenAQClient.asMap(obj);

            Object sensorIdValue = reading.get("sensorsId");

            if (sensorIdValue == null) {
                continue; // Skip readings we can't identify a sensor for.
            }

            int sensorId = OpenAQClient.toInt(sensorIdValue);

            String pollutantName =
                    OpenAQClient.lookupPollutantName(stationId, sensorId);

            Object value = reading.get("value");

            // datetime is a nested object: { "utc": "...", "local": "..." }
            Map<String, Object> datetime =
                    OpenAQClient.asMap(reading.get("datetime"));

            Object utcTime = (datetime != null) ? datetime.get("utc") : null;

            Map<String, Object> cleanReading = new LinkedHashMap<>();
            cleanReading.put("pollutant", pollutantName);
            cleanReading.put("value", value);
            cleanReading.put("datetime", utcTime);

            measurements.add(cleanReading);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("stationId", stationId);
        response.put("stationName", station.get("name"));
        response.put("measurements", measurements);

        return response;
    }

    private static Map<String, Object> findStationById(
            List<Object> stationsCache, int stationId) {

        for (Object obj : stationsCache) {

            Map<String, Object> station = OpenAQClient.asMap(obj);

            Object idValue = station.get("id");

            if (idValue != null && OpenAQClient.toInt(idValue) == stationId) {
                return station;
            }
        }

        return null;
    }
}
