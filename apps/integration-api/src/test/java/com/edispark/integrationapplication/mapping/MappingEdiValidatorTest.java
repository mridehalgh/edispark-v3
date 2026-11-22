package com.edispark.integrationapplication.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.transformations.domain.MappingCompatibility;
import org.junit.jupiter.api.Test;

class MappingEdiValidatorTest {
    private static final MappingCompatibility EDIFACT =
            new MappingCompatibility("INBOUND", "ORDER", "EDIFACT", "D96A", "ORDERS");
    private static final MappingCompatibility TRADACOMS =
            new MappingCompatibility("INBOUND", "ORDER", "TRADACOMS", "9", "ORDERS");

    private final MappingEdiValidator validator = new MappingEdiValidator();

    @Test
    void checksEdifactIdentityAndCountsWithoutSplittingAnEscapedTerminator() {
        String content = "UNB+UNOC:3+SENDER+RECEIVER+261122:1200+REF'"
                + "UNH+1+ORDERS:D:96A:UN'BGM+220+ORDER-1+9'FTX+AAI+++Items?' notes'UNT+4+1'UNZ+1+REF'";

        assertThat(validator.validate(EDIFACT, content).valid()).isTrue();
        var invalid = validator.validate(EDIFACT, content.replace("UNT+4+1", "UNT+3+2").replace(":96A:", ":01B:"));
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.findings()).extracting(MappingValidationReport.Finding::code)
                .containsExactly("EDI_MESSAGE_IDENTITY", "EDI_MESSAGE_REFERENCE", "EDI_SEGMENT_COUNT");
    }

    @Test
    void checksTradacomsEnvelopeCountsAndSelectedMessageRelease() {
        String content = "STX=ANA:1+SENDER+RECEIVER+261122:120000+REF'"
                + "MHD=1+ORDERS:9'ORD=ORDER-1'OLD=1+12345'MTR=4'END=1'";

        assertThat(validator.validate(TRADACOMS, content).valid()).isTrue();
        var invalid = validator.validate(TRADACOMS, content.replace("ORDERS:9", "ORDERS:7").replace("END=1", "END=2"));
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.findings()).extracting(MappingValidationReport.Finding::code)
                .contains("EDI_MESSAGE_IDENTITY");
    }
}
