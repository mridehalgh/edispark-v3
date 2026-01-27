package com.example.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main Spring Boot application entry point.
 * <p>
 * This application module serves as a thin orchestration layer that wires up
 * domain modules and provides cross-cutting concerns. It contains no business
 * logic - all domain logic resides in the domain modules.
 * <p>
 * Component scanning is configured to discover:
 * <ul>
 *   <li>com.example.documents - Documents domain module (controllers, handlers, repositories)</li>
 *   <li>com.example.application - Application module (global configuration, exception handlers)</li>
 * </ul>
 *
 * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.structuring-your-code">Spring Boot Application Structure</a>
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.example.documents",
    "com.example.application"
})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
