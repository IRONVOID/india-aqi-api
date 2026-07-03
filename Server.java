import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        // Most hosting platforms (Render included) assign a port
        // dynamically via the PORT environment variable and expect the
        // app to listen on it. Locally, PORT usually isn't set, so we
        // fall back to 8080, keeping local development unchanged.
        int port = 8080;
        String portEnv = System.getenv("PORT");

        if (portEnv != null) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                // Ignore an invalid value and keep the default.
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/stations", new StationsHandler());
        server.createContext("/stats", new StatsHandler());
        server.createContext("/live", new LiveHandler());
        server.createContext("/analysis", new AnalysisHandler());

        // Registered last, but HttpServer routes by longest matching
        // path prefix (not registration order), so this only catches
        // requests that none of the API routes above matched — i.e.
        // everything under the "public" folder (index.html, style.css,
        // app.js) and the dashboard's root "/" itself.
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("Server running at http://localhost:" + port + "\n");

        System.out.println("Available Endpoints:");
        System.out.println("http://localhost:" + port + "/                (dashboard)");
        System.out.println("http://localhost:" + port + "/stations");
        System.out.println("http://localhost:" + port + "/stations?pollutant=pm25");
        System.out.println("http://localhost:" + port + "/stations?name=airport");
        System.out.println("http://localhost:" + port + "/stations?locality=Delhi");
        System.out.println("http://localhost:" + port + "/stations?name=delhi&pollutant=pm25");
        System.out.println("http://localhost:" + port + "/stations?locality=Delhi&pollutant=pm25");
        System.out.println("http://localhost:" + port + "/stations?page=1&limit=10");
        System.out.println("http://localhost:" + port + "/stations?locality=Delhi&page=2&limit=5");
        System.out.println("http://localhost:" + port + "/stations?sort=name");
        System.out.println("http://localhost:" + port + "/stations?sort=locality");
        System.out.println("http://localhost:" + port + "/stations?sort=pollutantCount");
        System.out.println("http://localhost:" + port + "/stations?locality=Delhi&sort=pollutantCount&limit=5");
        System.out.println("http://localhost:" + port + "/live?id=236");
        System.out.println("http://localhost:" + port + "/analysis?id=236");
        System.out.println("http://localhost:" + port + "/stats");
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
     * Serves the dashboard's static files (index.html, style.css, app.js)
     * from the "public" folder sitting next to this Server.java / .class
     * file. This is what lets the frontend and backend live on the same
     * origin (http://localhost:8080), which avoids CORS entirely --
     * the browser never sees the dashboard's JS talking to a "different"
     * server, because as far as it's concerned there's only one.
     */
    static class StaticFileHandler implements HttpHandler {

        private static final Path PUBLIC_DIR = Paths.get("public").toAbsolutePath().normalize();

        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {

            String requestPath = exchange.getRequestURI().getPath();

            // "/" itself should serve the dashboard's index page.
            if (requestPath.equals("/")) {
                requestPath = "/index.html";
            }

            // Resolve the requested path against the public folder, and
            // normalize it. This blocks a request like "/../Server.java"
            // from escaping the public folder and reading arbitrary files
            // off the server -- a basic but important safety check for
            // any static file server.
            Path resolved = PUBLIC_DIR.resolve("." + requestPath).normalize();

            if (!resolved.startsWith(PUBLIC_DIR) || !Files.exists(resolved) || Files.isDirectory(resolved)) {
                sendPlainText(exchange, 404, "404 Not Found: " + requestPath);
                return;
            }

            byte[] fileBytes = Files.readAllBytes(resolved);

            exchange.getResponseHeaders().set("Content-Type", contentTypeFor(resolved));
            exchange.sendResponseHeaders(200, fileBytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(fileBytes);
            os.close();
        }

        private String contentTypeFor(Path path) {

            String name = path.toString().toLowerCase();

            if (name.endsWith(".html")) return "text/html; charset=utf-8";
            if (name.endsWith(".css")) return "text/css; charset=utf-8";
            if (name.endsWith(".js")) return "application/javascript; charset=utf-8";

            return "application/octet-stream";
        }

        private void sendPlainText(HttpExchange exchange, int statusCode, String message)
                throws java.io.IOException {

            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
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