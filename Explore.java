import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;

public class Explore {

    static String getEnvValue(String key) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(".env"));
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
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

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("HTTP status code: " + response.statusCode());
        return response.body();
    }

    public static void main(String[] args) {
        try {
            String apiKey = getEnvValue("OPENAQ_API_KEY");

            if (apiKey.equals("your_api_key_here")) {
                System.out.println("You still have the placeholder key in .env — replace it with your real OpenAQ key.");
                return;
            }

            System.out.println("=== Fetching list of countries (to find India's ID) ===\n");
            String countriesJson = fetch("/countries?limit=300", apiKey);
            System.out.println(countriesJson.substring(0, Math.min(1000, countriesJson.length())));
            System.out.println("\n... (truncated, full response is longer)\n");

        } catch (Exception e) {
            System.out.println("Something went wrong:");
            e.printStackTrace();
        }
    }
}