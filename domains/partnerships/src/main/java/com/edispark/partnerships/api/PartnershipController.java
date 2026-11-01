package com.edispark.partnerships.api;

import com.edispark.partnerships.application.PartnershipService;
import com.edispark.partnerships.domain.model.AcknowledgementPolicy;
import com.edispark.partnerships.domain.model.BatchingPolicy;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.BusinessIdentity;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
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
        return service.createPartner(request.displayName(), request.identities());
    }

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

    @PostMapping("/agreements/{agreementId}/qualify")
    @Operation(operationId = "qualifyTradingAgreement", summary = "Run strict agreement qualification")
    public TradingAgreement qualify(@PathVariable UUID agreementId) { return service.qualify(agreementId); }

    @PostMapping("/agreements/{agreementId}/activate")
    @Operation(operationId = "activateTradingAgreement", summary = "Activate a qualified agreement revision")
    public TradingAgreement activate(@PathVariable UUID agreementId) { return service.activate(agreementId); }

    @PostMapping("/agreements/{agreementId}/suspend")
    @Operation(operationId = "suspendTradingAgreement", summary = "Suspend an active agreement")
    public TradingAgreement suspend(@PathVariable UUID agreementId) { return service.suspend(agreementId); }

    @PostMapping("/agreements/{agreementId}/revisions") @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createTradingAgreementRevision", summary = "Create an editable revision from an active agreement")
    public TradingAgreement revise(@PathVariable UUID agreementId) { return service.createRevision(agreementId); }

    public record CreatePartnerRequest(@NotBlank @Size(max = 120) String displayName,
                                       @NotEmpty List<@Valid BusinessIdentity> identities) { }
    public record CreateAgreementRequest(@NotBlank @Size(max = 120) String name) { }
    public record CapabilityRequest(@NotNull ExchangeDirection direction, @NotNull BusinessDocumentType documentType,
            @NotNull EdiStandard standard, @NotBlank String release, @NotBlank String messageType,
            @NotNull UUID sourceSchemaId, @NotNull UUID targetSchemaId, @NotNull UUID mappingRevisionId,
            @NotNull UUID as2ConnectionId, @NotNull AcknowledgementPolicy acknowledgementPolicy,
            @NotNull BatchingPolicy batchingPolicy) {
        ExchangeCapability toDomain() {
            return new ExchangeCapability(UUID.randomUUID(), direction, documentType, standard, release, messageType,
                    sourceSchemaId, targetSchemaId, mappingRevisionId, as2ConnectionId, acknowledgementPolicy, batchingPolicy);
        }
    }
}
