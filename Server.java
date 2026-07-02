import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        System.out.println("http://localhost:8080/stations?name=delhi&pollutant=pm25");
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
                    }
                }

                String responseJson = MiniJson.toJson(result);

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

                    Map<String, Object> station = OpenAQClient.asMap(obj);

                    List<Object> pollutants =
                            OpenAQClient.asList(station.get("pollutants"));

                    for (Object pollutant : pollutants) {

                        String name = pollutant.toString().toLowerCase();

                        pollutantCounts.put(
                                name,
                                pollutantCounts.getOrDefault(name, 0) + 1
                        );
                    }
                }

                for (Map.Entry<String, Integer> entry : pollutantCounts.entrySet()) {

                    stats.put(entry.getKey() + "Stations", entry.getValue());

                }

                stats.put("totalPollutantTypes", pollutantCounts.size());

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