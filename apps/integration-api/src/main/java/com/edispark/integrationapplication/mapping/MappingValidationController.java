package com.edispark.integrationapplication.mapping;

import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.transformations.application.MappingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mappings/{mappingId}/validation")
@RequiredArgsConstructor
public final class MappingValidationController {
    private static final int MAX_BYTES = 1_048_576;
    private final MappingService mappings;
    private final MappingUblValidator ubl;
    private final MappingEdiValidator edi;

    @PostMapping
    @Operation(operationId = "validateMappingDocument", summary = "Validate a UBL or EDI file against the selected mapping side")
    public MappingValidationReport validate(@PathVariable UUID mappingId, @Valid @RequestBody MappingValidationRequest request) {
        var compatibility = mappings.getPersisted(mappingId).compatibility();
        if (compatibility == null) throw new IllegalArgumentException("Validation is unavailable for this mapping's format pair.");
        byte[] bytes = request.content().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Validation supports documents up to 1 MiB.");
        boolean ediSide = (request.side() == Side.SOURCE) == "INBOUND".equals(compatibility.direction());
        return ediSide ? edi.validate(compatibility, request.content())
                : ubl.validate(BusinessDocumentType.valueOf(compatibility.documentType()), bytes);
    }

    public enum Side { SOURCE, TARGET }
    public record MappingValidationRequest(@NotNull Side side, @NotBlank @Size(max = MAX_BYTES) String content) { }
}
