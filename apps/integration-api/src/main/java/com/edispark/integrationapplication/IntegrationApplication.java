package com.edispark.integrationapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.edispark.partnerships", "com.edispark.transformations",
        "com.edispark.exchange", "com.edispark.integrationapplication"})
public class IntegrationApplication {
    public static void main(String[] args) { SpringApplication.run(IntegrationApplication.class, args); }
}
