package com.WORLDCLOCK.service;

import com.WORLDCLOCK.model.CityInfo;
import com.WORLDCLOCK.Repository.CityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class WorldClockService {

    private static final long TEN_MINUTES = 10 * 60 * 1000;

    private final CityRepository cityRepository;
    private final String apiKey;
    private final WebClient webClient = WebClient.create("https://api.openweathermap.org");
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Το κλειδί διαβάζεται από τη μεταβλητή περιβάλλοντος OWM_API_KEY (βλ. application.properties)
    public WorldClockService(CityRepository cityRepository,
                             @Value("${openweathermap.api.key}") String apiKey) {
        this.cityRepository = cityRepository;
        this.apiKey = apiKey;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (cityRepository.count() == 0) {
            // Σειρά: (Όνομα, Χώρα, Ζώνη Ώρας, Latitude/Πλάτος, Longitude/Μήκος)
            cityRepository.save(new CityInfo("Θεσσαλονίκη", "Ελλάδα", "Europe/Athens", 40.6401, 22.9444));
            cityRepository.save(new CityInfo("Λονδίνο", "Ηνωμένο Βασίλειο", "Europe/London", 51.5074, -0.1278));
            cityRepository.save(new CityInfo("Παρίσι", "Γαλλία", "Europe/Paris", 48.8566, 2.3522));
            cityRepository.save(new CityInfo("Νέα Υόρκη", "ΗΠΑ", "America/New_York", 40.7128, -74.0060));
            cityRepository.save(new CityInfo("Λος Άντζελες", "ΗΠΑ", "America/Los_Angeles", 34.0522, -118.2437));
            cityRepository.save(new CityInfo("Τόκιο", "Ιαπωνία", "Asia/Tokyo", 35.6895, 139.6917));
            cityRepository.save(new CityInfo("Σίδνεϊ", "Αυστραλία", "Australia/Sydney", -33.8688, 151.2093));
            cityRepository.save(new CityInfo("Ντουμπάι", "ΗΑΕ", "Asia/Dubai", 25.2048, 55.2708));
            cityRepository.save(new CityInfo("Μόσχα", "Ρωσία", "Europe/Moscow", 55.7558, 37.6173));
            cityRepository.save(new CityInfo("Σαγκάη", "Κίνα", "Asia/Shanghai", 31.2304, 121.4737));
            cityRepository.save(new CityInfo("Σάο Πάολο", "Βραζιλία", "America/Sao_Paulo", -23.5505, -46.6333));
        }
        updateAllWeather();
    }

    public List<CityInfo> getAllCitiesWithData() {
        List<CityInfo> cities = cityRepository.findAll();
        for (CityInfo city : cities) {
            enrichWithTime(city);
        }
        return cities;
    }

    public CityInfo addCity(CityInfo city) {
        enrichWithWeather(city);
        enrichWithTime(city);
        return cityRepository.save(city);
    }

    public void deleteCity(Long id) {
        cityRepository.deleteById(id);
    }

    // Ανανέωση καιρού κάθε 10 λεπτά, ώστε το refresh του frontend να φέρνει φρέσκα δεδομένα
    @Scheduled(fixedRate = TEN_MINUTES, initialDelay = TEN_MINUTES)
    public void updateAllWeather() {
        List<CityInfo> cities = cityRepository.findAll();
        for (CityInfo city : cities) {
            enrichWithWeather(city);
            enrichWithTime(city);
            cityRepository.save(city);
        }
    }

    public CityInfo getCityData(String name, String country, String timezone, double lat, double lon) {
        CityInfo city = new CityInfo(name, country, timezone, lat, lon);
        enrichWithTime(city);
        enrichWithWeather(city);
        return city;
    }

    private void enrichWithTime(CityInfo city) {
        try {
            ZoneId zoneId = ZoneId.of(city.getTimezone());
            ZonedDateTime zdt = ZonedDateTime.now(zoneId);
            city.setLocaltime(zdt.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            city.setDate(zdt.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
            city.setDayofweek(zdt.format(DateTimeFormatter.ofPattern("EEEE")));
        } catch (Exception e) {
            city.setLocaltime("--:--:--");
            city.setDate("--");
            city.setDayofweek("--");
        }
    }

    private void enrichWithWeather(CityInfo city) {
        try {
            // Locale.US για τελεία ως υποδιαστολή στο URL
            String url = String.format(Locale.US, "/data/2.5/weather?lat=%.4f&lon=%.4f&units=metric&appid=%s",
                    city.getLatitude(), city.getLongitude(), this.apiKey);

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode main = root.path("main");
                JsonNode wind = root.path("wind");
                JsonNode weatherArray = root.path("weather");

                city.setTemperature(main.path("temp").asDouble());
                city.setFeelslike(main.path("feels_like").asDouble());
                city.setHumidity(main.path("humidity").asInt());
                city.setWindspeed(wind.path("speed").asDouble());

                if (weatherArray.isArray() && !weatherArray.isEmpty()) {
                    int weatherCode = weatherArray.get(0).path("id").asInt();
                    city.setWeathermain(getWeathermain(weatherCode));
                    city.setWeatherdescription(getWeatherdescription(weatherCode));
                    city.setWeathericon(getWeatherEmoji(weatherCode));
                }
            }
        } catch (Exception e) {
            city.setTemperature(Double.NaN);
            city.setWeatherdescription("Σφάλμα");
            city.setWeathericon("❌");
            city.setWeathermain("Error");
        }
    }

    private String getWeathermain(int code) {
        if (code >= 200 && code < 300) return "Thunderstorm";
        if (code >= 300 && code < 400) return "Drizzle";
        if (code >= 500 && code < 600) return "Rain";
        if (code >= 600 && code < 700) return "Snow";
        if (code >= 700 && code < 800) return "Atmosphere"; // Ομίχλη, σκόνη κλπ.
        if (code == 800) return "Clear";
        if (code > 800) return "Clouds";
        return "Unknown";
    }

    private String getWeatherdescription(int code) {
        if (code >= 200 && code < 300) return "Καταιγίδα";
        if (code >= 300 && code < 400) return "Ψιχάλα";
        if (code >= 500 && code < 600) return "Βροχή";
        if (code >= 600 && code < 700) return "Χιόνι";
        if (code >= 700 && code < 800) return "Ομίχλη";
        if (code == 800) return "Αίθριος Ουρανός";
        if (code == 801) return "Λίγα Σύννεφα";
        if (code == 802) return "Αραιή Συννεφιά";
        if (code == 803 || code == 804) return "Συννεφιά";
        return "Άγνωστες συνθήκες";
    }

    private String getWeatherEmoji(int code) {
        if (code >= 200 && code < 300) return "⛈️";
        if (code >= 300 && code < 400) return "🌦️";
        if (code >= 500 && code < 600) return "🌧️";
        if (code >= 600 && code < 700) return "❄️";
        if (code >= 700 && code < 800) return "🌫️";
        if (code == 800) return "☀️";
        if (code == 801 || code == 802) return "🌤️";
        if (code == 803 || code == 804) return "☁️";
        return "❓";
    }

    public List<String> getAvailableTimezones() {
        return ZoneId.getAvailableZoneIds().stream().sorted().toList();
    }
}
