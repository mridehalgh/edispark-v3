package com.edispark.mft.infrastructure.config;

import com.edispark.mft.application.MftEvidenceQuery;
import com.edispark.mft.domain.repository.MftRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MftEvidenceConfiguration {
    @Bean
    MftEvidenceQuery mftEvidenceQuery(MftRepository repository) {
        return new MftEvidenceQuery(repository);
    }
}
