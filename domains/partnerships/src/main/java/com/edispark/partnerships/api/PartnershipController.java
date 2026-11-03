package com.edispark.partnerships.api;

import com.edispark.partnerships.application.PartnershipService;
import com.edispark.partnerships.domain.model.AcknowledgementPolicy;
import com.edispark.partnerships.domain.model.BatchingPolicy;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.BusinessIdentity;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.partnerships.domain.model.InboundFileFailurePolicy;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.model.TradingPartner;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading-partners")
@RequiredArgsConstructor
public final class PartnershipController {
    private final PartnershipService service;

    @GetMapping @Operation(operationId = "listTradingPartners", summary = "List trading partners")
    public List<TradingPartner> listPartners() { return service.listPartners(); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createTradingPartner", summary = "Create a retailer or other trading partner")
    public TradingPartner createPartner(@Valid @RequestBody CreatePartnerRequest request) {
        return service.createPartner(request.displayName(), request.identities(), request.inboundFileFailurePolicy());
    }

    @GetMapping("/{partnerId}") @Operation(operationId = "getTradingPartner", summary = "Get a partner revision")
    public TradingPartner getPartner(@PathVariable UUID partnerId) { return service.getPartner(partnerId); }

    @PostMapping("/{partnerId}/revisions") @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createTradingPartnerRevision", summary = "Create an editable partner revision")
    public TradingPartner revisePartner(@PathVariable UUID partnerId) { return service.createPartnerRevision(partnerId); }

    @PutMapping("/{partnerId}/draft")
    @Operation(operationId = "updateTradingPartnerDraft", summary = "Update a draft partner revision")
    public TradingPartner updatePartner(@PathVariable UUID partnerId, @Valid @RequestBody CreatePartnerRequest request) {
        return service.updatePartner(partnerId, request.displayName(), request.identities(),
                request.inboundFileFailurePolicy());
    }

    @PostMapping("/{partnerId}/activate")
    @Operation(operationId = "activateTradingPartnerRevision", summary = "Activate a partner revision now or later")
    public TradingPartner activatePartner(@PathVariable UUID partnerId, @RequestBody(required = false) ActivationRequest request) {
        return service.activatePartner(partnerId, request == null ? null : request.effectiveAt());
    }

    @PostMapping("/{partnerId}/archive")
    @Operation(operationId = "archiveTradingPartnerRevision", summary = "Archive a partner revision")
    public TradingPartner archivePartner(@PathVariable UUID partnerId) { return service.archivePartner(partnerId); }

    @DeleteMapping("/{partnerId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteTradingPartnerDraft", summary = "Delete an unused partner draft")
    public void deletePartner(@PathVariable UUID partnerId) { service.deletePartnerDraft(partnerId); }

    @GetMapping("/agreements") @Operation(operationId = "listTradingAgreements", summary = "List trading agreements")
    public List<TradingAgreement> listAgreements() { return service.listAgreements(); }

    @PostMapping("/{partnerId}/agreements") @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createTradingAgreement", summary = "Create a draft trading agreement")
    public TradingAgreement createAgreement(@PathVariable UUID partnerId, @Valid @RequestBody CreateAgreementRequest request) {
        return service.createAgreement(partnerId, request.name());
    }

    @GetMapping("/agreements/{agreementId}") @Operation(operationId = "getTradingAgreement", summary = "Get a trading agreement")
    public TradingAgreement getAgreement(@PathVariable UUID agreementId) { return service.getAgreement(agreementId); }

    @PostMapping("/agreements/{agreementId}/capabilities")
    @Operation(operationId = "addExchangeCapability", summary = "Add a directional EDI capability")
    public TradingAgreement addCapability(@PathVariable UUID agreementId, @Valid @RequestBody CapabilityRequest request) {
        return service.addCapability(agreementId, request.toDomain());
    }

    @PutMapping("/agreements/{agreementId}/capabilities/{capabilityId}")
    @Operation(operationId = "replaceExchangeCapability", summary = "Replace a draft agreement capability")
    public TradingAgreement replaceCapability(@PathVariable UUID agreementId, @PathVariable UUID capabilityId,
                                              @Valid @RequestBody CapabilityRequest request) {
        return service.replaceCapability(agreementId, capabilityId, request.toDomain(capabilityId));
    }

    @DeleteMapping("/agreements/{agreementId}/capabilities/{capabilityId}")
    @Operation(operationId = "removeExchangeCapability", summary = "Remove a draft agreement capability")
    public TradingAgreement removeCapability(@PathVariable UUID agreementId, @PathVariable UUID capabilityId) {
        return service.removeCapability(agreementId, capabilityId);
    }

    @PostMapping("/agreements/{agreementId}/qualify")
    @Operation(operationId = "qualifyTradingAgreement", summary = "Run strict agreement qualification")
    public TradingAgreement qualify(@PathVariable UUID agreementId) { return service.qualify(agreementId); }

    @PostMapping("/agreements/{agreementId}/activate")
    @Operation(operationId = "activateTradingAgreement", summary = "Activate a qualified agreement revision")
    public TradingAgreement activate(@PathVariable UUID agreementId, @RequestBody(required = false) ActivationRequest request) {
        return service.activate(agreementId, request == null ? null : request.effectiveAt());
    }

    @PostMapping("/agreements/{agreementId}/suspend")
    @Operation(operationId = "suspendTradingAgreement", summary = "Suspend an active agreement")
    public TradingAgreement suspend(@PathVariable UUID agreementId) { return service.suspend(agreementId); }

    @PostMapping("/agreements/{agreementId}/revisions") @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createTradingAgreementRevision", summary = "Create an editable revision from an active agreement")
    public TradingAgreement revise(@PathVariable UUID agreementId) { return service.createRevision(agreementId); }

    @PostMapping("/agreements/{agreementId}/archive")
    @Operation(operationId = "archiveTradingAgreementRevision", summary = "Archive an agreement revision")
    public TradingAgreement archiveAgreement(@PathVariable UUID agreementId) { return service.archiveAgreement(agreementId); }

    @GetMapping("/agreements/{agreementId}/usage")
    @Operation(operationId = "getTradingAgreementUsage", summary = "Explain agreement deletion dependencies")
    public PartnershipService.Usage agreementUsage(@PathVariable UUID agreementId) { return service.agreementUsage(agreementId); }

    @DeleteMapping("/agreements/{agreementId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteTradingAgreementDraft", summary = "Delete an unused agreement draft")
    public void deleteAgreement(@PathVariable UUID agreementId) { service.deleteAgreementDraft(agreementId); }

    public record CreatePartnerRequest(@NotBlank @Size(max = 120) String displayName,
            @NotEmpty List<@Valid BusinessIdentity> identities,
            InboundFileFailurePolicy inboundFileFailurePolicy) { }
    public record CreateAgreementRequest(@NotBlank @Size(max = 120) String name) { }
    public record CapabilityRequest(@NotNull ExchangeDirection direction, @NotNull BusinessDocumentType documentType,
            @NotNull EdiStandard standard, @NotBlank String release, @NotBlank String messageType,
            @NotNull UUID sourceSchemaId, @NotNull UUID targetSchemaId, @NotNull UUID mappingRevisionId,
            @NotNull UUID as2ConnectionId, @NotNull AcknowledgementPolicy acknowledgementPolicy,
            @NotNull BatchingPolicy batchingPolicy) {
        ExchangeCapability toDomain() { return toDomain(UUID.randomUUID()); }
        ExchangeCapability toDomain(UUID id) {
            return new ExchangeCapability(id, direction, documentType, standard, release, messageType,
                    sourceSchemaId, targetSchemaId, mappingRevisionId, as2ConnectionId, acknowledgementPolicy, batchingPolicy);
        }
    }
    public record ActivationRequest(java.time.Instant effectiveAt) { }
}
