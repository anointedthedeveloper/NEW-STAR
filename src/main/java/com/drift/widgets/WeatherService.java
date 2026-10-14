package com.drift.widgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.drift.config.SettingsService;
import com.drift.core.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Weather from Open-Meteo (no API key). Opt-in: nothing is requested unless the user enabled weather and set a location,
 * and only the configured coordinates are sent. Any failure is reported as unavailable - values are never invented.
 */
@Service
public class WeatherService {
    public sealed interface State {
        record Unavailable(String reason) implements State {}
        record Data(double temperature, String unit, String description, double windSpeed, String windUnit) implements State {}
    }

    public interface Fetcher { String get(URI uri) throws Exception; }

    private final SettingsService settings;
    private final Fetcher fetcher;

    @org.springframework.beans.factory.annotation.Autowired
    public WeatherService(SettingsService settings) {
        this(settings, uri -> {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> r = c.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) throw new java.io.IOException("HTTP " + r.statusCode());
            return r.body();
        });
    }

    public WeatherService(SettingsService settings, Fetcher fetcher) {
        this.settings = settings;
        this.fetcher = fetcher;
    }

    public State fetch() {
        var w = settings.get().weather();
        if (!w.enabled()) return new State.Unavailable("Weather is off. Enable it and set a location in Settings > Widgets.");
        if (w.latitude() == 0 && w.longitude() == 0) return new State.Unavailable("No location set. Add latitude/longitude in Settings > Widgets.");
        boolean imperial = w.units().equals("imperial");
        URI uri = URI.create(String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,wind_speed_10m&temperature_unit=%s&wind_speed_unit=%s",
                w.latitude(), w.longitude(), imperial ? "fahrenheit" : "celsius", imperial ? "mph" : "kmh"));
        try {
            JsonNode cur = Json.MAPPER.readTree(fetcher.get(uri)).path("current");
            if (cur.isMissingNode() || !cur.has("temperature_2m")) return new State.Unavailable("The weather service returned no data.");
            return new State.Data(cur.get("temperature_2m").asDouble(), imperial ? "°F" : "°C", describe(cur.path("weather_code").asInt(-1)),
                    cur.path("wind_speed_10m").asDouble(), imperial ? "mph" : "km/h");
        } catch (Exception e) {
            return new State.Unavailable("Weather unavailable (offline or the service did not respond).");
        }
    }

    static String describe(int wmo) {
        return switch (wmo) {
            case 0 -> "Clear sky"; case 1, 2 -> "Mostly clear"; case 3 -> "Overcast"; case 45, 48 -> "Fog";
            case 51, 53, 55, 56, 57 -> "Drizzle"; case 61, 63, 65, 66, 67 -> "Rain"; case 71, 73, 75, 77 -> "Snow";
            case 80, 81, 82 -> "Rain showers"; case 85, 86 -> "Snow showers"; case 95, 96, 99 -> "Thunderstorm";
            default -> "Conditions unknown";
        };
    }
}
