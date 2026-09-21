package com.micropay.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.micropay.backend")
@EnableScheduling
public class MicropayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicropayApplication.class, args);
    }

}
