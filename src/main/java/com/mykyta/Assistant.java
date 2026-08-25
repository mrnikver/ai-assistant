package com.mykyta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Assistant {

    public static void main(String[] args) {
        SpringApplication.run(Assistant.class, args);
    }
}