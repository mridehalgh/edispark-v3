package com.edispark.integrationapplication.mapping;

import com.edispark.transformations.domain.MappingCompatibility;
import com.edispark.transformations.domain.MappingSample;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class MappingExampleFiles {
    private static final Map<String, String> UBL_EXAMPLES = Map.of(
            "ORDER_RESPONSE", "ubl-order-response-2026-0042.json", "DESPATCH_ADVICE", "ubl-despatch-advice-2026-0042.json",
            "REMITTANCE_ADVICE", "ubl-remittance-advice-2026-0042.json", "RECEIPT_ADVICE", "ubl-receipt-advice-2026-0042.json");

    public Example load(MappingCompatibility compatibility, MappingSample sample) {
        if (sample != null) return new Example(sample.sourceDocument(), sample.sourceRaw(), sample.targetDocument(), sample.targetRaw());
        if (compatibility == null || !UBL_EXAMPLES.containsKey(compatibility.documentType())) return new Example(null, null, null, null);
        String ediName = String.join("-", compatibility.standard(), compatibility.messageType(), compatibility.release())
                .toLowerCase(Locale.ROOT);
        String edi = resource("seed/mft/" + ediName + ".edi");
        String ubl = resource("seed/documents/" + UBL_EXAMPLES.get(compatibility.documentType()));
        return "INBOUND".equals(compatibility.direction()) ? new Example(null, edi, ubl, null) : new Example(ubl, null, null, edi);
    }

    private String resource(String path) {
        var resource = new ClassPathResource(path);
        if (!resource.exists()) return null;
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load the mapping example file.", exception);
        }
    }

    public record Example(String sourceDocument, String sourceRaw, String targetDocument, String targetRaw) { }
}
