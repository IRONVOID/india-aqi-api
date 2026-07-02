import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * StatsService — owns the business logic for the /stats endpoint.
 * Like StationService, this class knows nothing about HTTP — it just
 * takes the station list and returns a plain Map ready for JSON.
 */
public class StatsService {

    public static Map<String, Object> getStats(List<Object> stationsCache) {

        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("totalStations", stationsCache.size());

        Map<String, Integer> pollutantCounts = new LinkedHashMap<>();

        for (Object obj : stationsCache) {

            Map<String, Object> station = OpenAQClient.asMap(obj);

            List<Object> pollutants =
                    OpenAQClient.asList(station.get("pollutants"));

            // Count each pollutant only once per station
            Set<String> uniquePollutants = new HashSet<>();

            for (Object pollutant : pollutants) {

                if (pollutant != null) {
                    uniquePollutants.add(
                            pollutant.toString().toLowerCase()
                    );
                }
            }

            for (String pollutant : uniquePollutants) {

                pollutantCounts.put(
                        pollutant,
                        pollutantCounts.getOrDefault(pollutant, 0) + 1
                );
            }
        }

        for (Map.Entry<String, Integer> entry : pollutantCounts.entrySet()) {

            stats.put(
                    entry.getKey() + "Stations",
                    entry.getValue()
            );
        }

        stats.put("totalPollutantTypes", pollutantCounts.size());

        return stats;
    }
}
