package com.farmweather.weather_irrigation.controller;

import com.farmweather.weather_irrigation.service.WeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/irrigation")
    public String getIrrigationAdvice(
            @RequestParam(defaultValue = "55") int nx,
            @RequestParam(defaultValue = "127") int ny) {

        return weatherService.getIrrigationAdvice(nx, ny);
    }
}
