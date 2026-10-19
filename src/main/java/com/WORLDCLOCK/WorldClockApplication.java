package com.WORLDCLOCK;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WorldClockApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorldClockApplication.class, args);
    }
}
