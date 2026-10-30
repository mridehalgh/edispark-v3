package com.edispark.mftapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.edispark.mft", "com.edispark.mftapplication"})
@EnableScheduling
public class MftApplication {
    public static void main(String[] args) {
        SpringApplication.run(MftApplication.class, args);
    }
}
