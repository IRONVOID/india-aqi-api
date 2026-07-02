import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Server {

    static List<Object> stationsCache;

    public static void main(String[] args) throws Exception {

        System.out.println("Fetching India station data from OpenAQ...");

        stationsCache = OpenAQClient.fetchIndiaStations();

        System.out.println("Loaded " + stationsCache.size() + " stations.\n");

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/stations", new StationsHandler());
        server.createContext("/stats", new StatsHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("Server running at http://localhost:8080\n");

        System.out.println("Available Endpoints:");
        System.out.println("http://localhost:8080/stations");
        System.out.println("http://localhost:8080/stations?pollutant=pm25");
        System.out.println("http://localhost:8080/stations?name=airport");
        System.out.println("http://localhost:8080/stations?locality=Delhi");
        System.out.println("http://localhost:8080/stations?name=delhi&pollutant=pm25");
        System.out.println("http://localhost:8080/stations?locality=Delhi&pollutant=pm25");
        System.out.println("http://localhost:8080/stations?page=1&limit=10");
        System.out.println("http://localhost:8080/stations?locality=Delhi&page=2&limit=5");
        System.out.println("http://localhost:8080/stations?sort=name");
        System.out.println("http://localhost:8080/stations?sort=locality");
        System.out.println("http://localhost:8080/stations?sort=pollutantCount");
        System.out.println("http://localhost:8080/stations?locality=Delhi&sort=pollutantCount&limit=5");
        System.out.println("http://localhost:8080/stats");
    }

    static class StationsHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {

            try {

                List<Object> result = new ArrayList<>(stationsCache);

                String query = exchange.getRequestURI().getQuery();

                if (query != null) {

                    String[] params = query.split("&");

                    for (String param : params) {

                        if (param.startsWith("pollutant=")) {

                            String pollutant =
                                    param.substring("pollutant=".length()).toLowerCase();

                            List<Object> filtered = new ArrayList<>();

                            for (Object obj : result) {

                                Map<String, Object> station =
                                        OpenAQClient.asMap(obj);

                                List<Object> pollutants =
                                        OpenAQClient.asList(station.get("pollutants"));

                                for (Object p : pollutants) {

                                    if (p.toString().equalsIgnoreCase(pollutant)) {

                                        filtered.add(station);
                                        break;
                                    }
                                }
                            }

                            result = filtered;
                        }

                        else if (param.startsWith("name=")) {

                            String keyword =
                                    param.substring("name=".length()).toLowerCase();

                            List<Object> filtered = new ArrayList<>();

                            for (Object obj : result) {

                                Map<String, Object> station =
                                        OpenAQClient.asMap(obj);

                                String name =
                                        String.valueOf(station.get("name")).toLowerCase();

                                if (name.contains(keyword)) {
                                    filtered.add(station);
                                }
                            }

                            result = filtered;
                        }

                        // NEW: Filter stations by locality (e.g. ?locality=Delhi)
                        // Uses the same "contains, case-insensitive" matching style
                        // as the name filter above, so partial/loose matches work
                        // (e.g. "delhi" matches "New Delhi").
                        //
                        // IMPORTANT: OpenAQ does not reliably fill in "locality"
                        // for Indian stations — it's often null. Instead of
                        // silently returning zero results in that case, we fall
                        // back to matching against the station's "name" field,
                        // since names usually contain the city anyway
                        // (e.g. "Delhi Technological University, Delhi - CPCB").
                        else if (param.startsWith("locality=")) {

                            String keyword =
                                    param.substring("locality=".length()).toLowerCase();

                            List<Object> filtered = new ArrayList<>();

                            for (Object obj : result) {

                                Map<String, Object> station =
                                        OpenAQClient.asMap(obj);

                                Object localityValue = station.get("locality");

                                boolean matches = false;

                                if (localityValue != null) {

                                    String locality =
                                            localityValue.toString().toLowerCase();

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

                            result = filtered;
                        }
                    }
                }

                // ===== SORTING =====
                // Applied AFTER filtering (we only sort what actually matched)
                // and BEFORE pagination (so "page 2" is the next slice of the
                // sorted list, not a page-then-sort mismatch).
                //
                // Supported values:
                //   sort=name            -> alphabetical by station name
                //   sort=locality        -> alphabetical by locality
                //                           (falls back to name when locality
                //                            is null, same as the locality filter)
                //   sort=pollutantCount  -> most pollutants monitored first
                if (query != null) {

                    String[] params = query.split("&");

                    for (String param : params) {

                        if (param.startsWith("sort=")) {

                            String sortBy =
                                    param.substring("sort=".length());

                            if (sortBy.equalsIgnoreCase("name")) {

                                result.sort(Comparator.comparing(obj ->
                                        String.valueOf(
                                                OpenAQClient.asMap(obj).get("name")
                                        ).toLowerCase()
                                ));
                            }

                            else if (sortBy.equalsIgnoreCase("locality")) {

                                result.sort(Comparator.comparing(obj -> {

                                    Map<String, Object> station =
                                            OpenAQClient.asMap(obj);

                                    Object localityValue = station.get("locality");

                                    // Same fallback as the locality filter:
                                    // if locality is missing, sort by name instead
                                    // so null-locality stations don't all clump
                                    // together at one end of the list.
                                    String sortKey = (localityValue != null)
                                            ? localityValue.toString()
                                            : String.valueOf(station.get("name"));

                                    return sortKey.toLowerCase();
                                }));
                            }

                            else if (sortBy.equalsIgnoreCase("pollutantCount")) {

                                // Descending: stations monitoring more
                                // pollutants are generally more useful/complete,
                                // so surface them first.
                                result.sort(Comparator.comparingInt((Object obj) ->
                                        OpenAQClient.asList(
                                                OpenAQClient.asMap(obj).get("pollutants")
                                        ).size()
                                ).reversed());
                            }
                        }
                    }
                }

                // ===== PAGINATION =====
                // Applied AFTER all filters above, so page/limit always
                // operate on the already-filtered result set, not the
                // full station list. This keeps filtering + pagination
                // composable, e.g. ?locality=Delhi&pollutant=pm25&page=1&limit=10

                int totalResults = result.size();

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
                    pagedResult = new ArrayList<>(result.subList(fromIndex, toIndex));
                }

                Map<String, Object> pagination = new LinkedHashMap<>();
                pagination.put("page", page);
                pagination.put("limit", limit);
                pagination.put("totalResults", totalResults);
                pagination.put("totalPages", totalPages);

                Map<String, Object> responseBody = new LinkedHashMap<>();
                responseBody.put("data", pagedResult);
                responseBody.put("pagination", pagination);

                String responseJson = MiniJson.toJson(responseBody);

                byte[] bytes =
                        responseJson.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set(
                        "Content-Type",
                        "application/json"
                );

                exchange.sendResponseHeaders(200, bytes.length);

                OutputStream os = exchange.getResponseBody();

                os.write(bytes);

                os.close();

            } catch (Exception e) {

                String error =
                        "{\"error\":\"" + e.getMessage() + "\"}";

                byte[] bytes =
                        error.getBytes(StandardCharsets.UTF_8);

                exchange.sendResponseHeaders(500, bytes.length);

                exchange.getResponseBody().write(bytes);

                exchange.close();
            }
        }
    }
    static class StatsHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {

            try {

                Map<String, Object> stats = new LinkedHashMap<>();

                stats.put("totalStations", stationsCache.size());

                Map<String, Integer> pollutantCounts = new LinkedHashMap<>();

                for (Object obj : stationsCache) {

                    Map<String, Object> station =
                            OpenAQClient.asMap(obj);

                    List<Object> pollutants =
                            OpenAQClient.asList(station.get("pollutants"));

                    // FIX: Count each pollutant only once per station
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

                stats.put(
                        "totalPollutantTypes",
                        pollutantCounts.size()
                );

                String responseJson = MiniJson.toJson(stats);

                byte[] bytes =
                        responseJson.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set(
                        "Content-Type",
                        "application/json"
                );

                exchange.sendResponseHeaders(200, bytes.length);

                OutputStream os = exchange.getResponseBody();

                os.write(bytes);

                os.close();

            } catch (Exception e) {

                String error =
                        "{\"error\":\"" + e.getMessage() + "\"}";

                byte[] bytes =
                        error.getBytes(StandardCharsets.UTF_8);

                exchange.sendResponseHeaders(500, bytes.length);

                exchange.getResponseBody().write(bytes);

                exchange.close();
            }
        }
    }
}
