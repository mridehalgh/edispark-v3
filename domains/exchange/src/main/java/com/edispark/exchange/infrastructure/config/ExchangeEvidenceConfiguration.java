package com.edispark.exchange.infrastructure.config;

import com.edispark.exchange.application.ExchangeEvidenceQuery;
import com.edispark.exchange.domain.ExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ExchangeEvidenceConfiguration {
    @Bean
    ExchangeEvidenceQuery exchangeEvidenceQuery(ExchangeRepository exchanges) {
        return new ExchangeEvidenceQuery(exchanges);
    }
}
