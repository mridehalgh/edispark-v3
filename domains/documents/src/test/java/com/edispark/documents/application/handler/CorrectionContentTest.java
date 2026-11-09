package com.edispark.documents.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.Format;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CorrectionContentTest {
    @Test void keepsUnexposedLineFieldsWithTheirOwnIdentityWhenLinesAreRemoved() throws Exception {
        String source = "{\"lines\":[{\"ID\":[{\"_\":\"1\"}],\"extra\":\"first\"},{\"ID\":[{\"_\":\"2\"}],\"extra\":\"second\"}]}";
        var edited = Content.of("{\"lines\":[{\"ID\":[{\"_\":\"2\"}]}]}".getBytes(StandardCharsets.UTF_8), Format.JSON);
        var result = new ObjectMapper().readTree(CorrectionContent.merge(source.getBytes(StandardCharsets.UTF_8), edited).data());
        assertThat(result.at("/lines/0/extra").asText()).isEqualTo("second");
        assertThat(result.path("lines")).hasSize(1);
    }
    @Test void retainsUnexposedFieldsAndRepairsTheLegacyAuthoringFieldInTheNewCopyOnly() throws Exception {
        String original = "{\"OrderResponse\":[{\"ResponseCode\":[{\"_\":\"AB\"}],\"ID\":[{\"_\":\"original\"}],\"Note\":[{\"_\":\"retained\"}],\"OrderLine\":[{\"ID\":[{\"_\":\"1\"}],\"CustomField\":42}]}]}";
        var edited = Content.of("{\"OrderResponse\":[{\"OrderResponseCode\":[{\"_\":\"AP\"}],\"ID\":[{\"_\":\"correction\"}],\"OrderLine\":[{\"ID\":[{\"_\":\"1\"}]}]}]}".getBytes(StandardCharsets.UTF_8), Format.JSON);
        var result = new ObjectMapper().readTree(CorrectionContent.merge(original.getBytes(StandardCharsets.UTF_8), edited).data());
        assertThat(result.at("/OrderResponse/0/ID/0/_").asText()).isEqualTo("correction");
        assertThat(result.at("/OrderResponse/0/Note/0/_").asText()).isEqualTo("retained");
        assertThat(result.at("/OrderResponse/0/OrderLine/0/CustomField").asInt()).isEqualTo(42);
        assertThat(result.at("/OrderResponse/0/ResponseCode").isMissingNode()).isTrue();
        assertThat(original).contains("ResponseCode");
    }
}
