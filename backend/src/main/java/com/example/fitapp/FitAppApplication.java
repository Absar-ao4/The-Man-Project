package com.example.fitapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FitAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitAppApplication.class, args);
    }
}
