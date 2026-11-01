package com.edispark.integrationapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.edispark.partnerships", "com.edispark.transformations",
        "com.edispark.exchange", "com.edispark.integrationapplication"})
@EnableScheduling
public class IntegrationApplication {
    public static void main(String[] args) { SpringApplication.run(IntegrationApplication.class, args); }
}
