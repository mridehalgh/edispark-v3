package com.edispark.wireup.managed;

import com.edispark.integrationapplication.persistence.ManagedMetadata;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.partnerships.domain.model.AgreementStatus;
import com.edispark.partnerships.domain.model.BusinessIdentity;
import com.edispark.partnerships.domain.model.PartnerStatus;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.model.TradingPartner;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import io.cottn.core.tenancy.TenantAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@Profile("!openapi-export")
public final class ManagedSetup {
    private final ManagedCatalogue catalogue;
    private final ManagedMetadata metadata;
    private final PartnershipRepository partnerships;
    private final MftService mft;
    private final Clock clock;
    private final boolean local;
    private final URI localEndpoint;

    ManagedSetup(ManagedCatalogue catalogue, ManagedMetadata metadata, PartnershipRepository partnerships,
                 MftService mft, Clock clock, Environment environment,
                 @Value("${mft.local-demo.partner-endpoint:http://localhost:8082/as2}") URI localEndpoint) {
        this.catalogue = catalogue;
        this.metadata = metadata;
        this.partnerships = partnerships;
        this.mft = mft;
        this.clock = clock;
        this.local = environment.matchesProfiles("local");
        this.localEndpoint = localEndpoint;
    }

    public record Request(@NotNull UUID requestId, @NotNull @Valid BusinessIdentity businessIdentity,
                          @NotBlank @Size(max = 128) String localAs2Id, @NotBlank @Size(max = 128) String remoteAs2Id,
                          @NotNull URI endpoint, URI asynchronousMdnEndpoint, @NotNull UUID localCertificateId,
                          @NotNull UUID partnerSigningCertificateId, @NotNull UUID partnerEncryptionCertificateId) {}
    public record ManagedSetupResult(UUID templateId, int templateRevision, UUID partnerId, UUID connectionId, UUID agreementId) {}
    public record Acceptance(String reference, String actor, Instant recordedAt, String configuration) {}
    record Setup(ManagedSetupResult result, ManagedTemplate.Definition definition, String requestHash, Instant startedAt) {}

    ManagedSetupResult apply(UUID templateId, int revision, Request request) {
        var idempotencyKey = request.requestId().toString();
        var requestHash = fingerprint(templateId, revision, request);
        var existing = metadata.setup(idempotencyKey);
        if (existing.isPresent()) return complete(replay(existing.get(), requestHash), request);
        var identity = request.businessIdentity();
        String tenantId = TenantAccess.currentId();
        UUID agreement = stableId(tenantId + ":managed-agreement:" + catalogue.write(identity));
        var priorSetup = metadata.agreement(agreement.toString());
        if (priorSetup.isPresent()) return complete(replay(priorSetup.get(), requestHash), request);
        var template = catalogue.published(templateId, revision);
        var result = new ManagedSetupResult(templateId, revision, stableId(agreement + ":partner"),
                stableId(agreement + ":as2"), agreement);
        ensureIdentityAvailable(identity, result.partnerId());
        connectionDraft(result, template.definition(), request);
        var setup = new Setup(result, template.definition(), requestHash, clock.instant());
        if (!metadata.reserveSetup(idempotencyKey, catalogue.write(setup), agreement.toString())) {
            var reserved = metadata.setup(idempotencyKey).or(() -> metadata.agreement(agreement.toString()))
                    .orElseThrow(() -> new IllegalStateException("The setup is being submitted. Retry with the same values."));
            setup = replay(reserved, requestHash);
        }
        return complete(setup, request);
    }

    private Setup replay(String value, String requestHash) {
        var existing = catalogue.read(value, Setup.class);
        if (!existing.requestHash().equals(requestHash)) {
            throw new IllegalStateException("A managed setup already exists with different values. Review the existing partner.");
        }
        return existing;
    }

    private ManagedSetupResult complete(Setup setup, Request request) {
        var result = setup.result();
        var definition = setup.definition();
        if (partnerships.findPartner(result.partnerId()).isEmpty()) {
            ensureIdentityAvailable(request.businessIdentity(), result.partnerId());
        }
        try {
            mft.persistedConnection(result.connectionId());
        } catch (com.edispark.mft.application.MftNotFoundException absent) {
            mft.saveManagedDraft(connectionDraft(result, definition, request));
        }
        String tenantId = TenantAccess.currentId();
        var now = setup.startedAt();
        partnerships.insertPartnerDraft(new TradingPartner(result.partnerId(), tenantId, definition.name(),
                List.of(request.businessIdentity()), PartnerStatus.DRAFT, now, now, result.partnerId(), 1, null, null,
                1, definition.inboundFileFailurePolicy()));
        var capabilities = definition.capabilities().stream()
                .map(capability -> capability.bind(stableId(result.agreementId() + ":" + capability.mappingRevisionId()), result.connectionId())).toList();
        partnerships.insertAgreementDraft(new TradingAgreement(result.agreementId(), tenantId, result.partnerId(),
                1, definition.name(), AgreementStatus.DRAFT, null, null, capabilities, null, now, now));
        return result;
    }

    private PartnerProfile connectionDraft(ManagedSetupResult result, ManagedTemplate.Definition definition, Request request) {
        if (definition.verification() == ManagedTemplate.Verification.LOCAL_DEMO
                && (!local || !localEndpoint.equals(request.endpoint()))) {
            throw new IllegalStateException("This template is available only in the local simulator.");
        }
        var transport = definition.transport();
        try {
            return mft.managedDraft(result.connectionId(), new MftService.CreatePartner(definition.name(), request.localAs2Id(),
                    request.remoteAs2Id(), request.endpoint(), request.localCertificateId(), request.partnerSigningCertificateId(),
                    request.partnerEncryptionCertificateId(), request.asynchronousMdnEndpoint(), transport.mdnMode(),
                    transport.signingRequired(), transport.encryptionRequired(), transport.compressionEnabled(), false));
        } catch (com.edispark.mft.application.MftNotFoundException absent) {
            throw new IllegalArgumentException("Select AS2 certificates from this workspace.", absent);
        }
    }

    ManagedPartnerController.ManagedAgreement agreementSetup(UUID agreementId) {
        var agreement = partnerships.findAgreement(agreementId)
                .orElseThrow(() -> new IllegalArgumentException("Trading agreement was not found."));
        var setup = managed(agreement);
        if (setup == null) return new ManagedPartnerController.ManagedAgreement(null, false, false);
        boolean retailerAcceptanceRequired = !simulator(agreement);
        return new ManagedPartnerController.ManagedAgreement(setup.result(), retailerAcceptanceRequired,
                retailerAcceptanceRequired && currentAcceptance(agreement));
    }

    Acceptance accept(UUID agreementId, String reference, String actor) {
        var agreement = partnerships.findAgreement(agreementId)
                .orElseThrow(() -> new IllegalArgumentException("Trading agreement was not found."));
        var setup = managed(agreement);
        if (setup == null || setup.definition().verification() == ManagedTemplate.Verification.LOCAL_DEMO) {
            throw new IllegalArgumentException("Retailer acceptance applies to a managed live agreement.");
        }
        if (agreement.status() != AgreementStatus.DRAFT && agreement.status() != AgreementStatus.QUALIFYING) {
            throw new IllegalStateException("Record retailer acceptance on an editable agreement revision.");
        }
        var evidence = new Acceptance(reference.trim(), actor, clock.instant(), configuration(agreement));
        metadata.recordAcceptance(agreement.id().toString(), catalogue.write(evidence));
        return evidence;
    }

    public List<String> qualificationFailures(TradingAgreement agreement) {
        var setup = managed(agreement);
        if (setup == null) return List.of();
        if (partnerships.findPartner(agreement.partnerId()).filter(partner -> partner.status() == PartnerStatus.ACTIVE).isEmpty()) {
            return List.of("Activate the managed partner before qualifying the agreement.");
        }
        try {
            for (var capability : agreement.capabilities()) {
                var connection = mft.persistedConnection(capability.as2ConnectionId());
                if (!connection.familyId().equals(setup.result().connectionId())) {
                    return List.of("Managed capabilities must use the connection created by this setup or a revision of it.");
                }
                if (connection.status() != com.edispark.mft.domain.model.PartnerStatus.ACTIVE) {
                    return List.of("Activate the managed AS2 connection before qualifying the agreement.");
                }
            }
        } catch (com.edispark.mft.application.MftNotFoundException absent) {
            return List.of("A managed AS2 connection must exist before retailer acceptance can apply.");
        }
        if (simulator(agreement)) return List.of();
        if (setup.definition().verification() == ManagedTemplate.Verification.LOCAL_DEMO) {
            return List.of("Simulator templates are unavailable for live activation.");
        }
        return currentAcceptance(agreement) ? List.of()
                : List.of("Record current retailer acceptance for this agreement and AS2 connection.");
    }

    private boolean currentAcceptance(TradingAgreement agreement) {
        try {
            return metadata.acceptance(agreement.id().toString())
                    .map(value -> catalogue.read(value, Acceptance.class))
                    .filter(evidence -> evidence.configuration().equals(configuration(agreement))).isPresent();
        } catch (com.edispark.mft.application.MftNotFoundException absent) {
            return false;
        }
    }

    private boolean simulator(TradingAgreement agreement) {
        if (!local || agreement.capabilities().isEmpty()) return false;
        try {
            return agreement.capabilities().stream().allMatch(capability ->
                    localEndpoint.equals(mft.persistedConnection(capability.as2ConnectionId()).endpoint()));
        } catch (com.edispark.mft.application.MftNotFoundException absent) {
            return false;
        }
    }

    private Setup managed(TradingAgreement agreement) {
        return metadata.agreement(agreement.familyId().toString()).map(value -> catalogue.read(value, Setup.class)).orElse(null);
    }

    private String configuration(TradingAgreement agreement) {
        Map<UUID, PartnerProfile> connections = new LinkedHashMap<>();
        for (var capability : agreement.capabilities()) {
            connections.computeIfAbsent(capability.as2ConnectionId(), mft::persistedConnection);
        }
        var partner = partnerships.findPartner(agreement.partnerId()).orElseThrow();
        return hash(List.of(agreement.id(), agreement.capabilities(), connections, partner.identities()));
    }

    private void ensureIdentityAvailable(BusinessIdentity identity, UUID partnerId) {
        if (partnerships.listPartners().stream().filter(partner -> !partner.familyId().equals(partnerId))
                .anyMatch(partner -> partner.identities().contains(identity))) {
            throw new IllegalStateException("This business identity is already registered. Review the existing partner.");
        }
    }

    private String fingerprint(UUID templateId, int revision, Request request) {
        return hash(java.util.Arrays.asList(templateId, revision, request.businessIdentity(), request.localAs2Id(),
                request.remoteAs2Id(), request.endpoint(), request.asynchronousMdnEndpoint(), request.localCertificateId(),
                request.partnerSigningCertificateId(), request.partnerEncryptionCertificateId()));
    }

    private String hash(Object value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(catalogue.write(value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static UUID stableId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
