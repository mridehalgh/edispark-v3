package com.edispark.wireup.managed;

import com.edispark.transformations.domain.GlobalMappingCatalog;
import com.edispark.wireup.managed.ManagedTemplate.Capability;
import com.edispark.wireup.managed.ManagedTemplate.Definition;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ManagedPartnerController {
    private static final String PLATFORM = "/api/platform/managed-partners";
    private static final String TENANT = "/api/managed-partners";
    private final ObjectProvider<ManagedCatalogue> catalogue;
    private final ObjectProvider<ManagedSetup> setup;
    private final GlobalMappingCatalog mappings;

    ManagedPartnerController(ObjectProvider<ManagedCatalogue> catalogue, ObjectProvider<ManagedSetup> setup,
                             GlobalMappingCatalog mappings) {
        this.catalogue = catalogue;
        this.setup = setup;
        this.mappings = mappings;
    }

    @GetMapping(PLATFORM)
    @Operation(operationId = "listManagedPartnerRevisions")
    public List<ManagedTemplate> revisions() {
        return catalogue.getObject().revisions();
    }

    @GetMapping(PLATFORM + "/mappings")
    @Operation(operationId = "listManagedPartnerMappings")
    public List<MappingOption> mappings() {
        return mappings.list().stream().filter(mapping -> mapping.compatibility() != null).map(mapping -> {
            var match = mapping.compatibility();
            return new MappingOption(mapping.name(), new Capability(ExchangeDirection.valueOf(match.direction()),
                    BusinessDocumentType.valueOf(match.documentType()), EdiStandard.valueOf(match.standard()),
                    match.release(), match.messageType(), mapping.sourceSchemaId(), mapping.targetSchemaId(), mapping.id()));
        }).toList();
    }

    @PostMapping(PLATFORM)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createManagedPartnerTemplate")
    public ManagedTemplate create(@Valid @RequestBody Definition body, @AuthenticationPrincipal Jwt actor) {
        return catalogue.getObject().create(body, actor.getSubject());
    }

    @PutMapping(PLATFORM + "/{id}")
    @Operation(operationId = "editManagedPartnerTemplate")
    public ManagedTemplate edit(@PathVariable UUID id, @Valid @RequestBody Edit body, @AuthenticationPrincipal Jwt actor) {
        return catalogue.getObject().edit(id, body.revision(), body.version(), body.definition(), actor.getSubject());
    }

    @PostMapping(PLATFORM + "/{id}/publish")
    @Operation(operationId = "publishManagedPartnerTemplate")
    public ManagedTemplate publish(@PathVariable UUID id, @Valid @RequestBody Revision body, @AuthenticationPrincipal Jwt actor) {
        return catalogue.getObject().publish(id, body.revision(), body.version(), actor.getSubject());
    }

    @PostMapping(PLATFORM + "/{id}/retire")
    @Operation(operationId = "retireManagedPartnerTemplate")
    public ManagedTemplate retire(@PathVariable UUID id, @Valid @RequestBody Revision body, @AuthenticationPrincipal Jwt actor) {
        return catalogue.getObject().retire(id, body.revision(), body.version(), actor.getSubject());
    }

    @GetMapping(TENANT)
    @Operation(operationId = "listPublishedManagedPartners")
    public List<Published> published() {
        return catalogue.getObject().published().stream()
                .map(template -> new Published(template.id(), template.revision(), template.definition())).toList();
    }

    @PostMapping(TENANT + "/{id}/revisions/{revision}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "applyManagedPartnerTemplate")
    public ManagedSetup.ManagedSetupResult apply(@PathVariable UUID id, @PathVariable int revision,
                                     @Valid @RequestBody ManagedSetup.Request request) {
        return setup.getObject().apply(id, revision, request);
    }

    @GetMapping(TENANT + "/agreements/{agreementId}")
    @Operation(operationId = "getManagedAgreementSetup")
    public ManagedAgreement managedAgreement(@PathVariable UUID agreementId) {
        return setup.getObject().agreementSetup(agreementId);
    }

    @PostMapping(TENANT + "/agreements/{agreementId}/acceptance")
    @Operation(operationId = "recordManagedRetailerAcceptance")
    public ManagedSetup.Acceptance accept(@PathVariable UUID agreementId, @Valid @RequestBody AcceptanceRequest request,
                                           @AuthenticationPrincipal Jwt actor) {
        return setup.getObject().accept(agreementId, request.reference(), actor.getSubject());
    }

    public record Published(UUID id, int revision, Definition definition) {}
    public record MappingOption(String name, Capability capability) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record ManagedAgreement(ManagedSetup.ManagedSetupResult setup, boolean requiresRetailerAcceptance,
                                   boolean retailerAcceptanceCurrent) {}
    public record Revision(@Positive int revision, @PositiveOrZero long version) {}
    public record Edit(@Positive int revision, @PositiveOrZero long version, @Valid @NotNull Definition definition) {}
    public record AcceptanceRequest(@NotBlank @Size(max = 300) String reference) {}
}
