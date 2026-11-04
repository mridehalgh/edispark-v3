package com.edispark.wireup.modules;

import static org.assertj.core.api.Assertions.assertThat;
import com.edispark.documents.domain.model.Format;
import com.edispark.exchange.application.DocumentAcl.GeneratedArtifact;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InProcessDocumentAclTest {

    @ParameterizedTest
    @CsvSource({"EDI_PAYLOAD, EDI", "MAPPING_REPORT, JSON"})
    void storesGeneratedArtifactsUsingDocumentsFormats(GeneratedArtifact artifact, Format expectedFormat) {
        assertThat(InProcessDocumentAcl.storageFormat(artifact)).isEqualTo(expectedFormat);
    }
}
