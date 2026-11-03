package com.edispark.exchange.api;

import com.edispark.exchange.application.ExchangeService;
import com.edispark.exchange.domain.DocumentReference;
import com.edispark.exchange.domain.EdiExchange;
import com.edispark.exchange.domain.ExchangeStage;
import com.edispark.exchange.domain.ExchangeStatus;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exchanges")
@RequiredArgsConstructor
public final class ExchangeController {
    private final ExchangeService service;

    @GetMapping("/eligible-routes")
    @Operation(operationId = "listEligibleExchangeRoutes", summary = "List active agreement routes for a document")
    public List<ExchangeService.EligibleRoute> eligibleRoutes(@RequestParam UUID documentSetId,
                                                               @RequestParam UUID documentId) {
        return service.eligibleRoutes(documentSetId, documentId);
    }

    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "submitOutboundExchange", summary = "Submit a document under an active agreement")
    public ExchangeResponse submit(@Valid @RequestBody SubmitExchangeRequest request) {
        return ExchangeResponse.from(service.submitOutbound(new ExchangeService.SubmitOutbound(
                request.agreementId(), request.capabilityId(), request.documentSetId(), request.documentId(),
                request.idempotencyKey())));
    }

    @GetMapping @Operation(operationId = "listExchanges", summary = "List EDI exchanges")
    public List<ExchangeResponse> list() { return service.list().stream().map(ExchangeResponse::from).toList(); }

    @GetMapping("/{exchangeId}") @Operation(operationId = "getExchange", summary = "Get EDI exchange evidence")
    public ExchangeResponse get(@PathVariable UUID exchangeId) { return ExchangeResponse.from(service.get(exchangeId)); }

    public record SubmitExchangeRequest(@NotNull UUID agreementId, @NotNull UUID capabilityId,
            @NotNull UUID documentSetId, @NotNull UUID documentId, @NotBlank String idempotencyKey) { }

    public record ExchangeResponse(UUID id, ExchangeDirection direction, UUID partnerId, UUID agreementId,
            int agreementRevision, UUID capabilityId, ExchangeStatus status, DocumentReference sourceDocument,
            DocumentReference generatedDocument, UUID mftTransferId, Instant createdAt, Instant updatedAt,
            List<ExchangeStage> stages, int messageOrdinal, String sourceMessageReference) {
        static ExchangeResponse from(EdiExchange exchange) {
            return new ExchangeResponse(exchange.id(), exchange.direction(), exchange.partnerId(), exchange.agreementId(),
                    exchange.agreementRevision(), exchange.capabilityId(), exchange.status(), exchange.sourceDocument(),
                    exchange.generatedDocument(), exchange.mftTransferId(), exchange.createdAt(), exchange.updatedAt(),
                    exchange.stages(), exchange.messageOrdinal(), exchange.sourceMessageReference());
        }
    }
}
