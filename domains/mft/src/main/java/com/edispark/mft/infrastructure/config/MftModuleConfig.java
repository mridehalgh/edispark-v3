package com.edispark.mft.infrastructure.config;

import com.edispark.mft.application.CertificateService;
import com.edispark.mft.application.ConnectionDetailsService;
import com.edispark.mft.application.As2ExchangePort;
import com.edispark.mft.application.As2EncryptionCertificateProvider;
import com.edispark.mft.application.MftDeliveryService;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.domain.repository.PayloadStore;
import com.edispark.mft.domain.repository.SecretStore;
import com.edispark.mft.infrastructure.persistence.InMemoryMftRepository;
import com.edispark.mft.infrastructure.storage.FileSystemPayloadStore;
import com.edispark.mft.infrastructure.storage.LocalEncryptedSecretStore;
import com.edispark.mft.infrastructure.as2.MftDeliveryWorker;
import com.edispark.mft.infrastructure.as2.Phase2As2ExchangeAdapter;
import java.io.IOException;
import java.nio.file.Path;
import java.net.URI;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"local", "openapi-export"})
public class MftModuleConfig {
    @Bean
    @ConditionalOnMissingBean
    MftRepository mftRepository() {
        return new InMemoryMftRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    PayloadStore mftPayloadStore(@Value("${mft.payload.directory:./.local/mft/payloads}") String directory,
            @Value("${mft.secrets.master-key-file:./.local/mft/master.key}") String masterKeyFile) throws IOException {
        return new FileSystemPayloadStore(Path.of(directory), Path.of(masterKeyFile));
    }

    @Bean
    @ConditionalOnMissingBean
    Clock mftClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    SecretStore mftSecretStore(
            @Value("${mft.secrets.directory:./.local/mft/secrets}") String directory,
            @Value("${mft.secrets.master-key-file:./.local/mft/master.key}") String masterKeyFile) throws IOException {
        return new LocalEncryptedSecretStore(Path.of(directory), Path.of(masterKeyFile));
    }

    @Bean
    CertificateService mftCertificateService(MftRepository repository, SecretStore secretStore, Clock clock) {
        return new CertificateService(repository, secretStore, clock);
    }

    @Bean
    ConnectionDetailsService mftConnectionDetailsService(
            MftRepository repository,
            @Value("${mft.as2.public-inbound-url:http://localhost:8081/as2}") URI inboundUrl,
            @Value("${mft.as2.public-async-mdn-url:http://localhost:8081/as2/mdn}") URI asynchronousMdnUrl,
            @Value("${mft.as2.supported-encryption-algorithms:AES128-GCM}") String encryptionAlgorithms,
            @Value("${mft.as2.supported-signature-algorithms:SHA-256}") String signatureAlgorithms,
            @Value("${mft.as2.key-encryption-algorithm:RSA-OAEP}") String keyEncryptionAlgorithm) {
        return new ConnectionDetailsService(repository, inboundUrl, asynchronousMdnUrl,
                commaSeparated(encryptionAlgorithms), commaSeparated(signatureAlgorithms), keyEncryptionAlgorithm,
                certificate -> new As2EncryptionCertificateProvider.PublicCertificate(certificate.name(),
                        certificate.fingerprintSha256(), certificate.notAfter(), certificate.publicCertificatePem()));
    }

    @Bean
    As2ExchangePort as2ExchangePort(
            @Value("${mft.as2.allow-private-networks:false}") boolean allowPrivateNetworks,
            @Value("${mft.as2.async-mdn-url:http://localhost:8081/as2/mdn}") String asyncMdnUrl,
            CertificateService certificateService) {
        return new Phase2As2ExchangeAdapter(allowPrivateNetworks, asyncMdnUrl, certificateService);
    }

    @Bean
    MftDeliveryService mftDeliveryService(MftRepository repository, PayloadStore payloadStore,
                                          As2ExchangePort exchange,
                                          Clock clock) {
        return new MftDeliveryService(repository, payloadStore, exchange, clock);
    }

    @Bean
    MftDeliveryWorker mftDeliveryWorker(MftRepository repository, MftDeliveryService deliveryService, Clock clock) {
        return new MftDeliveryWorker(repository, deliveryService, clock);
    }

    @Bean
    MftService mftService(MftRepository repository, PayloadStore payloadStore, Clock clock,
            @Value("${mft.as2.allow-legacy-crypto:false}") boolean allowLegacyCrypto,
            @Value("${mft.as2.max-outbound-payload-bytes:104857600}") long maximumPayloadBytes,
            @Value("${mft.as2.certificate-rollover-grace-days:7}") long rolloverGraceDays) {
        return new MftService(repository, payloadStore, clock, allowLegacyCrypto, false, maximumPayloadBytes,
                java.time.Duration.ofDays(rolloverGraceDays));
    }

    private List<String> commaSeparated(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
