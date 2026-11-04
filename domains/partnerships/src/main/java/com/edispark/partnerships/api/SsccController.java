package com.edispark.partnerships.api;

import com.edispark.partnerships.application.SsccService;
import com.edispark.partnerships.domain.model.SsccIssuer;
import com.edispark.partnerships.domain.model.SsccReservation;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/sscc")
@RequiredArgsConstructor
public final class SsccController {
    private final SsccService service;

    @GetMapping("/issuers")
    @Operation(operationId = "listSsccIssuers", summary = "List organisation SSCC issuers and number ranges")
    public List<SsccIssuer> listIssuers() { return service.listIssuers(); }

    @PostMapping("/issuers") @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createSsccIssuer", summary = "Create a draft GS1 SSCC issuer and exclusive number range")
    public SsccIssuer createIssuer(@Valid @RequestBody CreateSsccIssuerRequest request) {
        return service.createIssuer(request.name(), request.gs1CompanyPrefix(), request.extensionDigit(),
                request.serialStart(), request.serialEnd(), request.reuseAfterMonths(), request.defaultIssuer(),
                request.ownershipAttested());
    }

    @PostMapping("/issuers/{issuerId}/activate")
    @Operation(operationId = "activateSsccIssuer", summary = "Activate an attested SSCC number range")
    public SsccIssuer activate(@PathVariable UUID issuerId) { return service.activate(issuerId); }

    @PostMapping("/issuers/{issuerId}/retire")
    @Operation(operationId = "retireSsccIssuer", summary = "Retire an SSCC number range")
    public SsccIssuer retire(@PathVariable UUID issuerId) { return service.retire(issuerId); }

    @PostMapping("/issuers/{issuerId}/default")
    @Operation(operationId = "makeDefaultSsccIssuer", summary = "Use an active SSCC range by default")
    public SsccIssuer makeDefault(@PathVariable UUID issuerId) { return service.makeDefault(issuerId); }

    @PostMapping("/reservations") @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "reserveSsccs", summary = "Idempotently reserve SSCCs for handling units")
    public SsccReservation reserve(@Valid @RequestBody ReserveSsccRequest request) {
        return service.reserve(request.issuerId(), request.count(), request.idempotencyKey(), request.context());
    }

    public record CreateSsccIssuerRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 4, max = 12) String gs1CompanyPrefix,
            @Min(0) @Max(9) int extensionDigit,
            @Min(0) long serialStart,
            @Min(0) long serialEnd,
            @Min(12) int reuseAfterMonths,
            boolean defaultIssuer,
            boolean ownershipAttested) { }

    public record ReserveSsccRequest(
            UUID issuerId,
            @Min(1) @Max(100) int count,
            @NotBlank @Size(max = 160) String idempotencyKey,
            @NotBlank @Size(max = 240) String context) { }
}
