package com.edispark.integrationapplication.mapping;

import com.edispark.integrationapplication.acl.EdiPayloadWriter;
import com.edispark.integrationapplication.inbound.InboundEdiTranslator;
import com.edispark.partnerships.domain.model.AcknowledgementPolicy;
import com.edispark.partnerships.domain.model.BatchingPolicy;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.transformations.application.MappingService;
import com.edispark.transformations.domain.MappingCompatibility;
import com.edispark.transformations.domain.MappingRevision;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mappings/{mappingId}/file-preview")
@RequiredArgsConstructor
public final class MappingFilePreviewController {
    private static final int MAXIMUM_CONTENT = 1_048_576;
    private static final UUID PREVIEW_CONNECTION = new UUID(0, 0);
    private final MappingService mappings;
    private final InboundEdiTranslator translator;
    private final EdiPayloadWriter writer;
    private final MappingExampleFiles examples;

    @GetMapping
    @Operation(operationId = "getMappingExampleFiles", summary = "Load example files for a mapping's exact formats")
    public MappingExampleFiles.Example getExample(@PathVariable UUID mappingId) {
        var mapping = mappings.getPersisted(mappingId);
        return examples.load(mapping.compatibility(), mappings.bundledSample(mapping));
    }

    @PostMapping
    @Operation(operationId = "previewMappingFile", summary = "Parse inbound EDI or render outbound EDI for a mapping test")
    public FilePreview preview(@PathVariable UUID mappingId, @Valid @RequestBody FilePreviewRequest request) {
        var mapping = mappings.getPersisted(mappingId);
        var compatibility = mapping.compatibility();
        if (compatibility == null) throw new IllegalArgumentException("EDI file preview is unavailable for these formats.");
        byte[] content = request.content().getBytes(StandardCharsets.UTF_8);
        if (content.length > MAXIMUM_CONTENT) throw new IllegalArgumentException("Mapping preview files must be 1 MiB or smaller.");
        if ("INBOUND".equals(compatibility.direction())) return new FilePreview(parse(content, compatibility), List.of());
        var preview = writer.previewMapping(capability(mapping, compatibility), content, EdiPayloadWriter.encoderRevision(mapping));
        return new FilePreview(new String(preview.output(), StandardCharsets.US_ASCII), preview.warnings());
    }

    private String parse(byte[] content, MappingCompatibility compatibility) {
        InboundEdiTranslator.Translation translation;
        try {
            translation = translator.translate(content);
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("The EDI file contains an invalid date or time.", invalid);
        }
        if (!translation.standard().name().equals(compatibility.standard())
                || !translation.release().equals(compatibility.release())
                || !translation.messageType().equals(compatibility.messageType())) {
            throw new IllegalArgumentException("Upload a single " + compatibility.standard() + " "
                    + compatibility.messageType() + " " + compatibility.release() + " document.");
        }
        return new String(translation.mappingInputJson(), StandardCharsets.UTF_8);
    }

    private ExchangeCapability capability(MappingRevision mapping, MappingCompatibility compatibility) {
        return new ExchangeCapability(mapping.id(), ExchangeDirection.OUTBOUND,
                BusinessDocumentType.valueOf(compatibility.documentType()), EdiStandard.valueOf(compatibility.standard()),
                compatibility.release(), compatibility.messageType(), mapping.sourceSchemaId(), mapping.targetSchemaId(),
                mapping.id(), PREVIEW_CONNECTION, AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL);
    }

    public record FilePreviewRequest(@NotBlank @Size(max = MAXIMUM_CONTENT) String content) { }
    public record FilePreview(String content, List<String> warnings) { }
}
