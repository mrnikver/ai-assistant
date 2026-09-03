package com.mykyta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiAssistApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAssistApplication.class, args);
    }
}
