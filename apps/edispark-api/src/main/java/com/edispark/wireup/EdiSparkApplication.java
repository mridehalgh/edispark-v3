package com.edispark.wireup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
    "com.edispark.wireup",
    "com.edispark.application",
    "com.edispark.documents",
    "com.edispark.mftapplication",
    "com.edispark.mft",
    "com.edispark.integrationapplication",
    "com.edispark.partnerships",
    "com.edispark.transformations",
    "com.edispark.exchange"
})
public class EdiSparkApplication {
    public static void main(String[] args) {
        SpringApplication.run(EdiSparkApplication.class, args);
    }
}
