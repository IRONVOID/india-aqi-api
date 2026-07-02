
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/*
 * Server — this is your actual REST API.
 *
 * What's different from everything before:
 *   - Explore.java and Stations.java ran ONCE and stopped
 *   - This program starts a server that keeps running, listening for
 *     web requests, and responds to them — that's what makes it an API
 *
 * How it works:
 *   1. On startup, we fetch India station data ONCE from OpenAQ and
 *      keep it in memory (a List). We don't want to hit OpenAQ's
 *      servers every single time someone visits our API — that would
 *      be slow and could hit rate limits.
 *   2. We start an HTTP server on port 8080 using Java's built-in
 *      com.sun.net.httpserver package — no external framework needed.
 *   3. We register a "handler" for the path /stations — whenever
 *      someone visits http://localhost:8080/stations, this handler
 *      runs and sends back our station data as JSON.
 *
 * Compile:
 *     javac MiniJson.java OpenAQClient.java Server.java
 * Run:
 *     java Server
 * Then visit in your browser:
 *     http://localhost:8080/stations
 */
public class Server {

    // Holds our cleaned station data in memory once fetched at startup.
    static List<Object> stationsCache;

    public static void main(String[] args) throws Exception {
        System.out.println("Fetching India station data from OpenAQ...");
        stationsCache = OpenAQClient.fetchIndiaStations();
        System.out.println("Loaded " + stationsCache.size() + " stations into memory.\n");

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/stations", new StationsHandler());

        server.setExecutor(null); // use the default single-threaded executor
        server.start();

        System.out.println("Server running at http://localhost:8080");
        System.out.println("Try visiting: http://localhost:8080/stations");
    }

    /**
     * Handles requests to /stations — sends back our cached station
     * data as a JSON array.
     */
    static class StationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {
            String responseJson = MiniJson.toJson(stationsCache);
            byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }
}