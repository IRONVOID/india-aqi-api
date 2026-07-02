import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * PollutionAnalysisService — the flagship feature of this project.
 *
 * Given a station's live measurements, this determines which pollutants
 * exceed safe limits, and returns a health status + recommendation for
 * each one that we're able to confidently analyze.
 *
 * IMPORTANT LIMITATION (documented on purpose, not hidden):
 * OpenAQ does not consistently report measurement units per reading.
 * PM2.5, PM10, NO2, SO2, and O3 are commonly reported by Indian CPCB
 * stations in µg/m³, so we can compare them directly against NAAQS
 * safe limits. CO is frequently reported in ppm by some providers and
 * µg/m³ by others -- comparing it directly against a µg/m³ limit would
 * be misleading, so CO is intentionally excluded from analysis for now.
 * Meteorological readings (temperature, humidity, wind) are not
 * pollutants and are excluded too. A production version of this
 * feature would need to read each sensor's "units" field and convert
 * accordingly.
 */
public class PollutionAnalysisService {

    // India NAAQS-based safe limits, in µg/m³.
    static final Map<String, Double> SAFE_LIMITS = new LinkedHashMap<>();
    static {
        SAFE_LIMITS.put("pm25", 60.0);
        SAFE_LIMITS.put("pm10", 100.0);
        SAFE_LIMITS.put("no2", 80.0);
        SAFE_LIMITS.put("so2", 80.0);
        SAFE_LIMITS.put("o3", 100.0);
    }

    /**
     * Returns pollution analysis for the given station ID, or null if no
     * station with that ID exists (Server.java turns that into a 404).
     */
    public static Map<String, Object> getAnalysis(
            List<Object> stationsCache, int stationId) throws Exception {

        Map<String, Object> live =
                LiveMeasurementService.getLiveMeasurements(stationsCache, stationId);

        if (live == null) {
            return null;
        }

        List<Object> measurements = OpenAQClient.asList(live.get("measurements"));

        Map<String, Map<String, Object>> latestByPollutant =
                keepOnlyMostRecentReadingPerPollutant(measurements);

        List<Object> analysis = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> entry : latestByPollutant.entrySet()) {

            String pollutant = entry.getKey();
            Map<String, Object> reading = entry.getValue();

            Object valueObj = reading.get("value");

            if (!(valueObj instanceof Number)) {
                continue; // Skip readings with a missing/invalid value.
            }

            double currentValue = ((Number) valueObj).doubleValue();
            double safeLimit = SAFE_LIMITS.get(pollutant);
            double exceededBy = round(currentValue - safeLimit);

            String status = getStatus(currentValue, safeLimit);
            String recommendation = getRecommendation(status);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pollutant", pollutant);
            result.put("currentValue", round(currentValue));
            result.put("safeLimit", safeLimit);
            result.put("exceededBy", exceededBy);
            result.put("status", status);
            result.put("recommendation", recommendation);
            result.put("datetime", reading.get("datetime"));

            analysis.add(result);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("stationId", stationId);
        response.put("stationName", live.get("stationName"));
        response.put("analysis", analysis);

        return response;
    }

    /**
     * Some stations have multiple sensors reporting the same pollutant
     * (e.g. two separate PM2.5 sensors, one stale from 2018 and one
     * current). We only want the most recent reading per pollutant --
     * otherwise we'd show contradictory results for the same pollutant.
     *
     * Also filters out any pollutant we don't have a safe limit for
     * (see class-level comment on CO / meteorological readings).
     */
    private static Map<String, Map<String, Object>> keepOnlyMostRecentReadingPerPollutant(
            List<Object> measurements) {

        Map<String, Map<String, Object>> latestByPollutant = new LinkedHashMap<>();

        for (Object obj : measurements) {

            Map<String, Object> reading = OpenAQClient.asMap(obj);

            String pollutant = String.valueOf(reading.get("pollutant"));

            if (!SAFE_LIMITS.containsKey(pollutant)) {
                continue;
            }

            Object datetimeValue = reading.get("datetime");
            String datetime = (datetimeValue == null) ? "" : datetimeValue.toString();

            Map<String, Object> existing = latestByPollutant.get(pollutant);

            if (existing == null) {
                latestByPollutant.put(pollutant, reading);
            } else {

                // ISO 8601 UTC timestamps (e.g. "2026-07-02T18:15:00Z")
                // sort correctly as plain strings, so we can compare them
                // directly without needing a date/time parsing library.
                String existingDatetime = String.valueOf(existing.get("datetime"));

                if (datetime.compareTo(existingDatetime) > 0) {
                    latestByPollutant.put(pollutant, reading);
                }
            }
        }

        return latestByPollutant;
    }

    /**
     * Classifies air quality based on how far the current value is over
     * the safe limit. This is a simplified 5-tier scale inspired by
     * standard AQI categories (Good / Moderate / Unhealthy for Sensitive
     * Groups / Unhealthy / Very Unhealthy) -- not an official AQI
     * calculation, which uses pollutant-specific breakpoint tables.
     */
    private static String getStatus(double currentValue, double safeLimit) {

        double ratio = currentValue / safeLimit;

        if (ratio <= 1.0) {
            return "Good";
        }
        if (ratio <= 1.25) {
            return "Moderate";
        }
        if (ratio <= 1.5) {
            return "Unhealthy for Sensitive Groups";
        }
        if (ratio <= 2.0) {
            return "Unhealthy";
        }
        return "Very Unhealthy";
    }

    private static String getRecommendation(String status) {

        switch (status) {

            case "Good":
                return "Air quality is satisfactory. Enjoy outdoor activities as normal.";

            case "Moderate":
                return "Air quality is acceptable. Unusually sensitive individuals should consider reducing prolonged outdoor exertion.";

            case "Unhealthy for Sensitive Groups":
                return "Sensitive groups (children, elderly, people with respiratory or heart conditions) should limit prolonged outdoor activity.";

            case "Unhealthy":
                return "Everyone may begin to experience health effects. Limit prolonged outdoor exertion, especially sensitive groups.";

            case "Very Unhealthy":
                return "Health warning: avoid outdoor activity. Consider wearing a mask if you must go outside.";

            default:
                return "No recommendation available.";
        }
    }

    /**
     * Rounds to 2 decimal places for cleaner JSON output
     * (avoids values like 33.99999999999997).
     */
    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
