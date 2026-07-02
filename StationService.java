import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * StationService — owns all the business logic for the /stations endpoint:
 * filtering, sorting, and pagination.
 *
 * This class knows nothing about HTTP. It just takes a list of stations and
 * a raw query string, and returns a plain Map ready to be turned into JSON.
 * That separation is the whole point of this refactor: Server.java should
 * only need to know "call StationService, serialize the result" — it
 * shouldn't need to know HOW filtering or sorting works.
 */
public class StationService {

    /**
     * Main entry point used by Server.java.
     * Takes the full cached station list plus the raw query string from the
     * request, and returns a response body already shaped as:
     *   { "data": [...], "pagination": {...} }
     */
    public static Map<String, Object> getStations(List<Object> stationsCache, String query) {

        List<Object> result = new ArrayList<>(stationsCache);

        result = applyFilters(result, query);
        result = applySorting(result, query);

        return applyPagination(result, query);
    }

    // ===== FILTERING =====
    // Supports: pollutant=, name=, locality=
    // Filters are composable — each one narrows whatever the previous
    // filter already narrowed, so order in the URL doesn't matter.
    private static List<Object> applyFilters(List<Object> stations, String query) {

        List<Object> result = stations;

        if (query == null) {
            return result;
        }

        String[] params = query.split("&");

        for (String param : params) {

            if (param.startsWith("pollutant=")) {

                String pollutant =
                        param.substring("pollutant=".length()).toLowerCase();

                result = filterByPollutant(result, pollutant);
            }

            else if (param.startsWith("name=")) {

                String keyword =
                        param.substring("name=".length()).toLowerCase();

                result = filterByName(result, keyword);
            }

            // Filter stations by locality (e.g. ?locality=Delhi)
            // Uses the same "contains, case-insensitive" matching style
            // as the name filter, so partial/loose matches work
            // (e.g. "delhi" matches "New Delhi").
            //
            // IMPORTANT: OpenAQ does not reliably fill in "locality" for
            // Indian stations — it's often null. Instead of silently
            // returning zero results in that case, we fall back to
            // matching against the station's "name" field, since names
            // usually contain the city anyway
            // (e.g. "Delhi Technological University, Delhi - CPCB").
            else if (param.startsWith("locality=")) {

                String keyword =
                        param.substring("locality=".length()).toLowerCase();

                result = filterByLocality(result, keyword);
            }
        }

        return result;
    }

    private static List<Object> filterByPollutant(List<Object> stations, String pollutant) {

        List<Object> filtered = new ArrayList<>();

        for (Object obj : stations) {

            Map<String, Object> station = OpenAQClient.asMap(obj);

            List<Object> pollutants =
                    OpenAQClient.asList(station.get("pollutants"));

            for (Object p : pollutants) {

                if (p.toString().equalsIgnoreCase(pollutant)) {
                    filtered.add(station);
                    break;
                }
            }
        }

        return filtered;
    }

    private static List<Object> filterByName(List<Object> stations, String keyword) {

        List<Object> filtered = new ArrayList<>();

        for (Object obj : stations) {

            Map<String, Object> station = OpenAQClient.asMap(obj);

            String name =
                    String.valueOf(station.get("name")).toLowerCase();

            if (name.contains(keyword)) {
                filtered.add(station);
            }
        }

        return filtered;
    }

    private static List<Object> filterByLocality(List<Object> stations, String keyword) {

        List<Object> filtered = new ArrayList<>();

        for (Object obj : stations) {

            Map<String, Object> station = OpenAQClient.asMap(obj);

            Object localityValue = station.get("locality");

            boolean matches = false;

            if (localityValue != null) {

                String locality = localityValue.toString().toLowerCase();

                if (locality.contains(keyword)) {
                    matches = true;
                }
            } else {

                // Fallback: locality missing, try the name instead.
                String name =
                        String.valueOf(station.get("name")).toLowerCase();

                if (name.contains(keyword)) {
                    matches = true;
                }
            }

            if (matches) {
                filtered.add(station);
            }
        }

        return filtered;
    }

    // ===== SORTING =====
    // Applied AFTER filtering (we only sort what actually matched) and
    // BEFORE pagination (so "page 2" is the next slice of the sorted list,
    // not a page-then-sort mismatch).
    //
    // Supported values:
    //   sort=name            -> alphabetical by station name
    //   sort=locality        -> alphabetical by locality
    //                           (falls back to name when locality is null,
    //                            same as the locality filter)
    //   sort=pollutantCount  -> most pollutants monitored first
    private static List<Object> applySorting(List<Object> stations, String query) {

        if (query == null) {
            return stations;
        }

        String[] params = query.split("&");

        for (String param : params) {

            if (param.startsWith("sort=")) {

                String sortBy = param.substring("sort=".length());

                if (sortBy.equalsIgnoreCase("name")) {
                    sortByName(stations);
                }

                else if (sortBy.equalsIgnoreCase("locality")) {
                    sortByLocality(stations);
                }

                else if (sortBy.equalsIgnoreCase("pollutantCount")) {
                    sortByPollutantCount(stations);
                }
            }
        }

        return stations;
    }

    private static void sortByName(List<Object> stations) {

        stations.sort(Comparator.comparing(obj ->
                String.valueOf(
                        OpenAQClient.asMap(obj).get("name")
                ).toLowerCase()
        ));
    }

    private static void sortByLocality(List<Object> stations) {

        stations.sort(Comparator.comparing(obj -> {

            Map<String, Object> station = OpenAQClient.asMap(obj);

            Object localityValue = station.get("locality");

            // Same fallback as the locality filter: if locality is
            // missing, sort by name instead so null-locality stations
            // don't all clump together at one end of the list.
            String sortKey = (localityValue != null)
                    ? localityValue.toString()
                    : String.valueOf(station.get("name"));

            return sortKey.toLowerCase();
        }));
    }

    private static void sortByPollutantCount(List<Object> stations) {

        // Descending: stations monitoring more pollutants are generally
        // more useful/complete, so surface them first.
        stations.sort(Comparator.comparingInt((Object obj) ->
                OpenAQClient.asList(
                        OpenAQClient.asMap(obj).get("pollutants")
                ).size()
        ).reversed());
    }

    // ===== PAGINATION =====
    // Applied AFTER filtering and sorting, so page/limit always operate on
    // the already-filtered, already-sorted result set. This keeps
    // filtering + sorting + pagination fully composable, e.g.
    // ?locality=Delhi&sort=pollutantCount&page=1&limit=10
    private static Map<String, Object> applyPagination(List<Object> stations, String query) {

        int totalResults = stations.size();

        // Defaults: page 1, 10 stations per page.
        int page = 1;
        int limit = 10;

        if (query != null) {

            String[] params = query.split("&");

            for (String param : params) {

                if (param.startsWith("page=")) {

                    try {
                        page = Integer.parseInt(
                                param.substring("page=".length()));
                    } catch (NumberFormatException e) {
                        // Ignore invalid values, keep default.
                    }
                }

                else if (param.startsWith("limit=")) {

                    try {
                        limit = Integer.parseInt(
                                param.substring("limit=".length()));
                    } catch (NumberFormatException e) {
                        // Ignore invalid values, keep default.
                    }
                }
            }
        }

        // Guard against nonsense input (page=0, limit=-5, limit=99999...)
        // rather than letting it throw or return everything.
        if (page < 1) {
            page = 1;
        }
        if (limit < 1) {
            limit = 10;
        }
        if (limit > 100) {
            limit = 100; // sane upper bound so no one can request the whole dataset in one page
        }

        int totalPages = (int) Math.ceil((double) totalResults / limit);
        if (totalPages < 1) {
            totalPages = 1;
        }

        int fromIndex = (page - 1) * limit;
        int toIndex = Math.min(fromIndex + limit, totalResults);

        List<Object> pagedResult;

        if (fromIndex >= totalResults || fromIndex < 0) {
            // Requested page is past the last page of results.
            pagedResult = new ArrayList<>();
        } else {
            pagedResult = new ArrayList<>(stations.subList(fromIndex, toIndex));
        }

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("totalResults", totalResults);
        pagination.put("totalPages", totalPages);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("data", pagedResult);
        responseBody.put("pagination", pagination);

        return responseBody;
    }
}

