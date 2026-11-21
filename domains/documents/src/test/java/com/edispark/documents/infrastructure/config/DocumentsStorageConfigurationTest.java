package com.edispark.documents.infrastructure.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DocumentsStorageConfigurationTest {
    @Test void productionCannotFallBackToPlaintextFilesystemStorage() {
        assertThatThrownBy(() -> new DocumentsStorageConfiguration().documentsDeploymentContent(null, null, false, ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("requires encrypted S3");
    }

    @Test void anUnconfiguredCellKeyringFailsClosed() {
        assertThatThrownBy(() -> new DocumentsStorageConfiguration().documentsTenantCipher(null, null, "", "", "", ""))
                .isInstanceOf(IllegalStateException.class);
    }
}
