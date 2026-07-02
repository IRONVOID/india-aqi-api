import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/*
 * Server — responsible ONLY for HTTP concerns:
 *   - starting the server and loading the initial station cache
 *   - routing requests to the right handler
 *   - reading the request (query string) and writing the response (JSON)
 *
 * All business logic (filtering, sorting, pagination, stats calculation)
 * now lives in StationService and StatsService. This handler code should
 * never need to change just because a filter or sort option changes.
 */
public class Server {

    static List<Object> stationsCache;

    public static void main(String[] args) throws Exception {

        System.out.println("Fetching India station data from OpenAQ...");

        stationsCache = OpenAQClient.fetchIndiaStations();

        System.out.println("Loaded " + stationsCache.size() + " stations.\n");

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/stations", new StationsHandler());
        server.createContext("/stats", new StatsHandler());
        server.createContext("/live", new LiveHandler());
        server.createContext("/analysis", new AnalysisHandler());

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
        System.out.println("http://localhost:8080/live?id=236");
        System.out.println("http://localhost:8080/analysis?id=236");
        System.out.println("http://localhost:8080/stats");
    }

    static class StationsHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {

            try {

                String query = exchange.getRequestURI().getQuery();

                Map<String, Object> responseBody =
                        StationService.getStations(stationsCache, query);

                sendJson(exchange, 200, MiniJson.toJson(responseBody));

            } catch (Exception e) {

                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class StatsHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {

            try {

                Map<String, Object> stats = StatsService.getStats(stationsCache);

                sendJson(exchange, 200, MiniJson.toJson(stats));

            } catch (Exception e) {

                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class LiveHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {

            try {

                String query = exchange.getRequestURI().getQuery();

                Integer stationId = extractId(query);

                if (stationId == null) {
                    sendJson(exchange, 400,
                            "{\"error\":\"Missing or invalid required parameter: id. Example: /live?id=236\"}");
                    return;
                }

                Map<String, Object> result =
                        LiveMeasurementService.getLiveMeasurements(stationsCache, stationId);

                if (result == null) {
                    sendJson(exchange, 404,
                            "{\"error\":\"No station found with id " + stationId + "\"}");
                    return;
                }

                sendJson(exchange, 200, MiniJson.toJson(result));

            } catch (Exception e) {

                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class AnalysisHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {

            try {

                String query = exchange.getRequestURI().getQuery();

                Integer stationId = extractId(query);

                if (stationId == null) {
                    sendJson(exchange, 400,
                            "{\"error\":\"Missing or invalid required parameter: id. Example: /analysis?id=236\"}");
                    return;
                }

                Map<String, Object> result =
                        PollutionAnalysisService.getAnalysis(stationsCache, stationId);

                if (result == null) {
                    sendJson(exchange, 404,
                            "{\"error\":\"No station found with id " + stationId + "\"}");
                    return;
                }

                sendJson(exchange, 200, MiniJson.toJson(result));

            } catch (Exception e) {

                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * Pulls "id" out of a query string and parses it as an int. Shared by
     * LiveHandler and AnalysisHandler, since both endpoints take the same
     * ?id= parameter. Returns null if it's missing or not a valid number,
     * so callers can respond with a clear 400 error instead of crashing.
     */
    private static Integer extractId(String query) {

        if (query == null) {
            return null;
        }

        String[] params = query.split("&");

        for (String param : params) {

            if (param.startsWith("id=")) {

                try {
                    return Integer.parseInt(param.substring("id=".length()));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Shared helper for writing a JSON response with the correct headers.
     * Both handlers were duplicating this exact block before, so pulling
     * it out here removes that duplication too.
     */
    private static void sendJson(HttpExchange exchange, int statusCode, String json)
            throws java.io.IOException {

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");

        exchange.sendResponseHeaders(statusCode, bytes.length);

        OutputStream os = exchange.getResponseBody();

        os.write(bytes);

        os.close();
    }
}