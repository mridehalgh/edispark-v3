package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.Candidate;

import com.edispark.crypto.LocalTenantCipher;
import com.edispark.crypto.HierarchicalTenantCipher;
import com.edispark.crypto.DynamoDbTenantBranchKeyResolver;
import software.amazon.awssdk.services.kms.KmsClient;
import com.edispark.crypto.TenantCipher;
import com.edispark.integrationapplication.acl.EdiPayloadWriter;
import com.edispark.integrationapplication.inbound.InboundEdiTranslator;
import com.edispark.integrationapplication.mapping.*;
import com.edispark.transformations.application.MappingService;
import com.edispark.transformations.domain.MappingRepository;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import com.edispark.partnerships.application.PartnershipService;
import com.edispark.integrationapplication.persistence.ReferenceTables;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.time.*;
import java.util.Map;
import java.util.List;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.textract.TextractClient;

abstract class MigConfiguration {
    @Bean
    MigStore migStore(@Qualifier("integrationDynamoDb") DynamoDbClient db,
            @Qualifier("migTenantCipher") TenantCipher cipher, tools.jackson.databind.ObjectMapper json,
            @Value("${integration.aws.metadata-table}") String table,
            @Value("${edispark.agent.usagetable:edispark-desk-usage-local}") String allowanceTable,
            @Value("${edispark.agent.monthlycredits:}") String credits,
            @Value("${edispark.agent.monthlycents:500}") long cents, Clock clock) {
        long allowance = credits.isBlank() ? Math.multiplyExact(cents, 10_000)
                : Math.multiplyExact(Long.parseLong(credits), MigStore.MICROS_PER_CREDIT);
        if (allowance < 0 || allowance > 1_000_000_000_000L) throw new IllegalArgumentException("Invalid organisation AI allowance");
        return new MigStore(db, cipher, json, table, allowanceTable, allowance, clock);
    }

    @Bean
    MigWorkflow migWorkflow(MigStore store, MappingService service, MappingExampleFiles examples,
            InboundEdiTranslator translator, EdiPayloadWriter writer, MappingUblValidator ubl, MappingEdiValidator edi,
            MigCandidateModel models, MigExtractor.Ocr ocr, MappingRepository mappings, PartnershipRepository partners, PartnershipService partnershipService,
            ReferenceTables tables, MeterRegistry meters, Clock clock,
            tools.jackson.databind.ObjectMapper json, com.fasterxml.jackson.databind.ObjectMapper mappingJson,
            @Qualifier("migDemoTenant") String demoTenant) {
        return new MigWorkflow(store, new MigExtractor(), ocr, new MigEvidence(service, examples, json, partnershipService, tables), new MigReview(),
                new MigPreview(translator, writer, ubl, edi, service, mappingJson), models, mappings, partners, meters, clock, demoTenant);
    }

    @Bean(destroyMethod = "close")
    MigWorker migWorker(MigStore store, MigWorkflow workflow, MeterRegistry meters) { return new MigWorker(store, workflow, meters); }

    @Configuration(proxyBeanMethods = false)
    @Profile("local & !production & !openapi-export")
    static class Local extends MigConfiguration {
        @Bean TenantCipher migTenantCipher(@Value("${edispark.mig.local-root:../../.local/mig/.tenant-root.key}") String path) {
            return new LocalTenantCipher(Path.of(path), "edispark-mig");
        }
        @Bean String migDemoTenant(@Value("${LOCAL_SEED_TENANT_ID:}") String tenant) { return tenant; }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("local & !production & !openapi-export & !mig-bedrock")
    static class LocalFixtures {
        @Bean MigCandidateModel migModels() { return new LocalMigModel(); }
        @Bean MigExtractor.Ocr migOcr() {
            return (image, page) -> List.of(new com.edispark.integrationapplication.mig.MigGeneration.Section(
                    page + ", OCR fixture row 1", "Synthetic scanned guide. The runtime needs an explicit field rule."));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("production & !openapi-export")
    @ConditionalOnProperty(name = "edispark.mig.london-pilot-verified", havingValue = "true")
    @Import(LondonServices.class)
    static class London extends MigConfiguration {
        @Bean
        TenantCipher migTenantCipher(@Qualifier("dynamoDbClient") DynamoDbClient dynamo,
                @Qualifier("documentsKms") KmsClient kms,
                @Value("${documents.encryption.registry-table}") String registry,
                @Value("${documents.encryption.key-store-table}") String keyStore,
                @Value("${documents.encryption.logical-name}") String logicalName,
                @Value("${documents.encryption.root-key-arn}") String rootKey) {
            return new HierarchicalTenantCipher(dynamo, kms, keyStore, logicalName, rootKey,
                    new DynamoDbTenantBranchKeyResolver(dynamo, registry), 600, "edispark-mig");
        }
        @Bean String migDemoTenant() { return ""; }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("local & !production & !openapi-export & mig-bedrock")
    @Import(LondonServices.class)
    static class LocalBedrock {
        @Bean(destroyMethod = "close")
        ProfileCredentialsProvider migAwsProfile(@Value("${edispark.mig.local-aws-profile}") String profile) {
            if (profile.isBlank()) throw new IllegalArgumentException("Set the AWS profile for local MIG inference.");
            return ProfileCredentialsProvider.create(profile);
        }
    }

    static class LondonServices {
        @Bean(destroyMethod = "close", defaultCandidate = false)
        BedrockRuntimeClient migBedrock(@Qualifier("migAwsProfile") ObjectProvider<ProfileCredentialsProvider> credentials) {
            var builder = BedrockRuntimeClient.builder().region(Region.EU_WEST_2).overrideConfiguration(settings(105))
                    .httpClientBuilder(ApacheHttpClient.builder().socketTimeout(Duration.ofSeconds(100)));
            credentials.ifAvailable(builder::credentialsProvider);
            return builder.build();
        }

        @Bean(destroyMethod = "close")
        TextractClient migTextract(@Qualifier("migAwsProfile") ObjectProvider<ProfileCredentialsProvider> credentials) {
            var builder = TextractClient.builder().region(Region.EU_WEST_2).overrideConfiguration(settings(12));
            credentials.ifAvailable(builder::credentialsProvider);
            return builder.build();
        }

        @Bean MigExtractor.Ocr migOcr(TextractClient migTextract) { return new LondonMigOcr(migTextract); }

        @Bean
        MigCandidateModel migModels(@Qualifier("migBedrock") BedrockRuntimeClient bedrock,
                @Qualifier("migAwsProfile") ObjectProvider<ProfileCredentialsProvider> localProfile,
                @Value("${edispark.mig.guardrail:${edispark.agent.guardrail:}}") String guardrail,
                @Value("${edispark.mig.guardrail-version:${edispark.agent.version:}}") String version,
                @Value("${edispark.mig.sonnet-input-micros}") long sonnetInput,
                @Value("${edispark.mig.sonnet-output-micros}") long sonnetOutput,
                @Value("${edispark.mig.nova-input-micros}") long novaInput,
                @Value("${edispark.mig.nova-output-micros}") long novaOutput) {
            if (sonnetInput <= 0 || sonnetOutput <= 0 || novaInput <= 0 || novaOutput <= 0)
                throw new IllegalArgumentException("Configure positive verified London usage rates.");
            return new LondonMigModel(bedrock, guardrail, version, Map.of(
                    Candidate.SONNET, new MigCandidateModel.Rate(sonnetInput, sonnetOutput),
                    Candidate.NOVA, new MigCandidateModel.Rate(novaInput, novaOutput)), localProfile.getIfAvailable() == null);
        }

        private static ClientOverrideConfiguration settings(int seconds) {
            return ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(seconds))
                    .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()).build();
        }
    }
}
