package com.edispark.documents.infrastructure.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.Format;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Gs1LogisticsLabelTransformerTest {
    private final Gs1LogisticsLabelTransformer transformer = new Gs1LogisticsLabelTransformer(new ObjectMapper());

    @Test
    void rendersPdfAndZplForEachValidHandlingUnit() {
        Content source = Content.of(document("123456789012345675", "PALLET^XZ~JA"), Format.JSON);

        Content pdf = transformer.transform(source, Format.PDF);
        Content zpl = transformer.transform(source, Format.ZPL);

        assertThat(new String(pdf.data(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(new String(zpl.data(), StandardCharsets.UTF_8))
                .contains("^FD00123456789012345675^FS")
                .doesNotContain("PALLET^XZ", "~JA");
    }

    @Test
    void rejectsInvalidSsccBeforeRendering() {
        Content source = Content.of(document("123456789012345670", "PALLET-1"), Format.JSON);

        assertThatThrownBy(() -> transformer.transform(source, Format.ZPL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SSCC check digit is invalid");
    }

    @Test
    void explainsWhenNoPrintableHandlingUnitsExist() {
        Content source = Content.of("""
                {"DespatchAdvice":[{"Shipment":[{"TransportHandlingUnit":[
                  {"ID":[{"_":"PALLET-1"}]}
                ]}]}]}
                """.getBytes(StandardCharsets.UTF_8), Format.JSON);

        assertThatThrownBy(() -> transformer.transform(source, Format.PDF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Add a valid 18-digit SSCC to at least one handling unit before creating labels");
    }

    private byte[] document(String sscc, String handlingUnitId) {
        return ("{\"DespatchAdvice\":[{\"Shipment\":[{\"TransportHandlingUnit\":["
                + "{\"ID\":[{\"_\":\"" + handlingUnitId + "\"}],\"TraceID\":[{\"_\":\"" + sscc + "\"}]}]}]}]}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
