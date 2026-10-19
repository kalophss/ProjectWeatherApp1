package com.WORLDCLOCK.controller;

import com.WORLDCLOCK.model.CityInfo;
import com.WORLDCLOCK.service.WorldClockService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class WorldClockController {

    private final WorldClockService service;

    public WorldClockController(WorldClockService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("cities", service.getAllCitiesWithData());
        model.addAttribute("timezones", service.getAvailableTimezones());
        return "index";
    }

    // POST, γιατί αλλάζει δεδομένα στη βάση
    @PostMapping("/api/add-city")
    @ResponseBody
    public CityInfo addCity(@RequestParam String name,
                            @RequestParam String country,
                            @RequestParam String timezone,
                            @RequestParam double latitude,
                            @RequestParam double longitude) {
        CityInfo newCity = new CityInfo(name, country, timezone, latitude, longitude);
        return service.addCity(newCity);
    }

    @DeleteMapping("/api/delete-city/{id}")
    @ResponseBody
    public void deleteCity(@PathVariable Long id) {
        service.deleteCity(id);
    }

    @GetMapping("/api/cities")
    @ResponseBody
    public List<CityInfo> getAllCities() {
        return service.getAllCitiesWithData();
    }
}
