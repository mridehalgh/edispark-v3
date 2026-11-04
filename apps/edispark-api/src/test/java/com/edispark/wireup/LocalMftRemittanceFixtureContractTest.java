package com.edispark.wireup;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.integrationapplication.inbound.InboundEdiTranslator;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.transformations.application.MappingRuntime;
import com.edispark.transformations.infrastructure.ClasspathGlobalMappingCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class LocalMftRemittanceFixtureContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void localEdifactRemittanceFixtureProducesACompleteUblRemittance() throws Exception {
        byte[] edi = Objects.requireNonNull(getClass().getResourceAsStream("/seed/mft/edifact-remadv-d96a.edi"))
                .readAllBytes();
        var translation = new InboundEdiTranslator(mapper).translate(edi);
        var mapping = new ClasspathGlobalMappingCatalog().list().stream()
                .filter(item -> item.compatibility() != null)
                .filter(item -> item.compatibility().direction().equals("INBOUND"))
                .filter(item -> item.compatibility().standard().equals("EDIFACT"))
                .filter(item -> item.compatibility().documentType().equals(BusinessDocumentType.REMITTANCE_ADVICE.name()))
                .findFirst().orElseThrow();

        var input = mapper.readTree(translation.mappingInputJson());
        var ubl = mapper.readTree(new MappingRuntime(mapper).execute(mapping, translation.mappingInputJson()));

        assertThat(input.at("/parties/sender").asText()).isEqualTo("[partner_identifier]");
        assertThat(input.at("/parties/receiver").asText()).isEqualTo("[tenant_identifier]");
        assertThat(input.at("/document/totalPaymentAmount").decimalValue()).isEqualByComparingTo("662.90");
        assertThat(input.at("/lines/0/id").asText()).isEqualTo("INV-77891");
        assertThat(ubl.at("/RemittanceAdvice/0/RemittanceAdviceLine/0/ID/0/_").asText())
                .isEqualTo("INV-77891");
        assertThat(ubl.at("/RemittanceAdvice/0/TotalPaymentAmount/0/_").decimalValue())
                .isEqualByComparingTo("662.90");
        assertThat(ubl.at("/RemittanceAdvice/0/TotalPaymentAmount/0/currencyID").asText()).isEqualTo("GBP");
    }

    @Test
    void localTradacomsRemittanceFixtureProducesACompleteUblRemittance() throws Exception {
        byte[] edi = Objects.requireNonNull(getClass().getResourceAsStream("/seed/mft/tradacoms-remadv-9.edi"))
                .readAllBytes();
        var translation = new InboundEdiTranslator(mapper).translate(edi);
        var mapping = new ClasspathGlobalMappingCatalog().list().stream()
                .filter(item -> item.compatibility() != null)
                .filter(item -> item.compatibility().direction().equals("INBOUND"))
                .filter(item -> item.compatibility().standard().equals("TRADACOMS"))
                .filter(item -> item.compatibility().documentType().equals(BusinessDocumentType.REMITTANCE_ADVICE.name()))
                .findFirst().orElseThrow();

        var input = mapper.readTree(translation.mappingInputJson());
        var ubl = mapper.readTree(new MappingRuntime(mapper).execute(mapping, translation.mappingInputJson()));

        assertThat(input.at("/parties/sender").asText()).isEqualTo("[tradacoms_buyer_gln]");
        assertThat(input.at("/parties/receiver").asText()).isEqualTo("[tenant_identifier]");
        assertThat(input.at("/document/totalPaymentAmount").decimalValue()).isEqualByComparingTo("43.20");
        assertThat(input.at("/lines/0/id").asText()).isEqualTo("1");
        assertThat(ubl.at("/RemittanceAdvice/0/RemittanceAdviceLine/0/ID/0/_").asText()).isEqualTo("1");
        assertThat(ubl.at("/RemittanceAdvice/0/TotalPaymentAmount/0/_").decimalValue())
                .isEqualByComparingTo("43.20");
    }
}
