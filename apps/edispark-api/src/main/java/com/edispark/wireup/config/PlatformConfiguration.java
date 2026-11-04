package com.edispark.wireup.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PlatformConfiguration {
    @Bean
    Clock platformClock() {
        return Clock.systemUTC();
    }
}
