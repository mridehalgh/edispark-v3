package com.edispark.integrationapplication.acl;

import com.edispark.exchange.application.TransformationAcl;
import com.edispark.exchange.application.ReadinessFailure;
import com.edispark.exchange.application.ReadinessIssue;
import com.edispark.exchange.domain.ExchangeWarning;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.transformations.application.MappingService;
import com.edispark.transformations.application.MappingFindingLocation;
import com.edispark.transformations.domain.MappingStatus;
import com.edispark.transformations.domain.MappingRevision;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class LocalTransformationAcl implements TransformationAcl {
    private final MappingService mappings;
    private final EdiPayloadWriter edi;
    private final com.edispark.exchange.application.OutboundControlAllocator controls;
    public LocalTransformationAcl(MappingService mappings, EdiPayloadWriter edi) {
        this(mappings, edi, com.edispark.exchange.application.OutboundControlAllocator.unavailable());
    }
    @org.springframework.beans.factory.annotation.Autowired
    public LocalTransformationAcl(MappingService mappings, EdiPayloadWriter edi,
            com.edispark.exchange.application.OutboundControlAllocator controls) {
        this.mappings = mappings; this.edi = edi;
        this.controls = controls;
    }
    @Override public Qualification qualification(ExchangeCapability capability) {
        return qualification(mappings.get(capability.mappingRevisionId()));
    }

    @Override public Qualification previewQualification(ExchangeCapability capability) {
        return qualification(mappings.getPersisted(capability.mappingRevisionId()));
    }

    private Qualification qualification(MappingRevision mapping) {
        if (!MappingService.requiresTargetEvidence(mapping)) return new Qualification(true, "");
        var evidence = mappings.qualificationEvidence(mapping.id());
        return new Qualification(evidence.valid(), evidence.identity());
    }
    @Override public Result executePublished(ExchangeCapability capability, byte[] canonicalContent) {
        return executePublished(capability, canonicalContent, mappings.get(capability.mappingRevisionId()));
    }

    @Override public Result previewPublished(ExchangeCapability capability, byte[] canonicalContent) {
        return executePublished(capability, canonicalContent, mappings.getPersisted(capability.mappingRevisionId()));
    }

    private Result executePublished(ExchangeCapability capability, byte[] canonicalContent, MappingRevision mapping) {
        if (mapping.status() != MappingStatus.PUBLISHED) {
            throw new IllegalArgumentException("Published mapping was not found");
        }
        if (!mapping.sourceSchemaId().equals(capability.sourceSchemaId()) || !mapping.targetSchemaId().equals(capability.targetSchemaId())) {
            throw new IllegalArgumentException("The pinned mapping does not match the route's source and target schemas.");
        }
        var result = mappings.executeWithReferenceTables(mapping, canonicalContent, capability.referenceTableRevisions());
        var warnings = result.warnings().stream()
                .map(warning -> new ExchangeWarning(warning.code(), warning.sourcePath(), warning.disposition(),
                        warning.message()))
                .toList();
        if (!warnings.isEmpty() && capability.outboundLossPolicy() == com.edispark.partnerships.domain.model.OutboundLossPolicy.STRICT) {
            return new Result(new byte[0], result.report(), warnings);
        }
        if (MappingService.requiresTargetEvidence(mapping)) {
            var evidence = mappings.qualificationEvidence(mapping.id());
            if (!evidence.valid()) throw new ReadinessFailure(java.util.List.of(ReadinessIssue.configuration("TARGET_QUALIFICATION_STALE",
                    "The mapping has no current passing target validation evidence. Publish a tested revision and requalify this route.")));
            var validation = mappings.validateTarget(mapping, canonicalContent, result.output());
            if (!validation.valid()) throw new ReadinessFailure(validation.findings().stream().map(finding -> {
                var sourcePath = finding.sourceLocation() ? java.util.Optional.of(finding.path())
                        : MappingFindingLocation.sourcePath(mapping, finding.path());
                return sourcePath.isPresent() ? ReadinessIssue.document(finding.code(), sourcePath.orElseThrow(), finding.message())
                            : new ReadinessIssue(finding.code(), finding.path(), finding.message(), "MAPPING", "ERROR",
                                    "Ask an administrator to test a new mapping revision against this document.");
            }).toList());
        }
        return new Result(edi.preview(capability, result.output(), EdiPayloadWriter.encoderRevision(mapping)), result.report(), warnings);
    }

    @Override public Result prepareForDispatch(ExchangeCapability capability, byte[] canonical, UUID exchangeId, Result checked) {
        if (capability.documentType() != com.edispark.partnerships.domain.model.BusinessDocumentType.ORDER_RESPONSE
                || capability.standard() != com.edispark.partnerships.domain.model.EdiStandard.TRADACOMS) return checked;
        var verified = executePublished(capability, canonical);
        if (!verified.warnings().isEmpty()) throw new ReadinessFailure(java.util.List.of(ReadinessIssue.configuration(
                "BQ_ACK_SOURCE_COVERAGE", "Complete the B&Q mapping's source dispositions before allocating transmission controls.")));
        var mapped = mappings.executeWithReferenceTables(mappings.get(capability.mappingRevisionId()), canonical,
                capability.referenceTableRevisions());
        var reserved = controls.reserve(capability.as2ConnectionId(), exchangeId);
        var control = new com.edispark.tradacoms.ack.BqAcknowledgement.Control(reserved.transmission(), reserved.fileGeneration());
        return new Result(edi.write(capability, mapped.output(), control), verified.report(), verified.warnings());
    }
}
