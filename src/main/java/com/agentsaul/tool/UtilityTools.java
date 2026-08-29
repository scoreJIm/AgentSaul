package com.agentsaul.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class UtilityTools {

    private static final Logger log = LoggerFactory.getLogger(UtilityTools.class);
    private final RestClient restClient = RestClient.create();

    @Tool(description = "Get the current date and time")
    public String currentDateTime() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.info("[Tool] currentDateTime -> {}", now);
        return now;
    }

    @Tool(description = "Get current weather for a city. Returns temperature, wind speed and weather condition.")
    public String getWeather(@ToolParam(description = "City name in English, e.g. Beijing, Shanghai") String city) {
        log.info("[Tool] getWeather city={}", city);
        try {
            // Open-Meteo: free, no API key, returns JSON
            // First geocode the city
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name={city}&count=1";
            @SuppressWarnings("unchecked")
            Map<String, Object> geoResp = restClient.get()
                    .uri(geoUrl, city)
                    .retrieve()
                    .body(Map.class);

            if (geoResp == null || !geoResp.containsKey("results")) {
                return "Could not find weather data for " + city;
            }

            var results = (List<Map<String, Object>>) geoResp.get("results");
            if (results.isEmpty()) return "City not found: " + city;
            var location = results.get(0);
            double lat = ((Number) location.get("latitude")).doubleValue();
            double lon = ((Number) location.get("longitude")).doubleValue();
            String name = (String) location.getOrDefault("name", city);

            // Get weather
            String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code";
            @SuppressWarnings("unchecked")
            Map<String, Object> weatherResp = restClient.get()
                    .uri(weatherUrl, lat, lon)
                    .retrieve()
                    .body(Map.class);

            if (weatherResp == null) return "Weather data unavailable for " + city;

            var current = (Map<String, Object>) weatherResp.get("current");
            double temp = ((Number) current.get("temperature_2m")).doubleValue();
            int humidity = ((Number) current.get("relative_humidity_2m")).intValue();
            double wind = ((Number) current.get("wind_speed_10m")).doubleValue();
            int code = ((Number) current.get("weather_code")).intValue();

            return String.format("Weather in %s: %s, %.1f°C, Humidity: %d%%, Wind: %.1f km/h",
                    name, weatherDesc(code), temp, humidity, wind);
        } catch (Exception e) {
            log.error("[Tool] getWeather failed: {}", e.getMessage());
            return "Weather service unavailable: " + e.getMessage();
        }
    }

    @Tool(description = "Get geolocation for an IP address, or use 'me' for current device's location")
    public String geoLocation(@ToolParam(description = "IP address or 'me'") String ip) {
        log.info("[Tool] geoLocation ip={}", ip);
        try {
            String url = "http://ip-api.com/json/{ip}?fields=city,regionName,country,lat,lon,timezone";
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restClient.get()
                    .uri(url, ip)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) return "Could not get location for " + ip;

            return String.format("Location: %s, %s, %s (lat: %s, lon: %s, tz: %s)",
                    resp.getOrDefault("city", "unknown"),
                    resp.getOrDefault("regionName", "unknown"),
                    resp.getOrDefault("country", "unknown"),
                    resp.getOrDefault("lat", "?"),
                    resp.getOrDefault("lon", "?"),
                    resp.getOrDefault("timezone", "?"));
        } catch (Exception e) {
            log.error("[Tool] geoLocation failed: {}", e.getMessage());
            return "Geolocation unavailable: " + e.getMessage();
        }
    }

    private String weatherDesc(int code) {
        return switch (code) {
            case 0 -> "Clear";
            case 1,2,3 -> "Partly cloudy";
            case 45,48 -> "Foggy";
            case 51,53,55 -> "Drizzle";
            case 56,57 -> "Freezing drizzle";
            case 61,63,65 -> "Rain";
            case 66,67 -> "Freezing rain";
            case 71,73,75 -> "Snow";
            case 77 -> "Snow grains";
            case 80,81,82 -> "Rain showers";
            case 85,86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96,99 -> "Thunderstorm with hail";
            default -> "Cloudy";
        };
    }
}
