package com.edispark.mft.api.rest;

import com.edispark.mft.api.dto.CreatePartnerRequest;
import com.edispark.mft.api.dto.PartnerResponse;
import com.edispark.mft.api.dto.RotatePartnerCertificatesRequest;
import com.edispark.mft.application.MftService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mft/partners")
@RequiredArgsConstructor
public class PartnerController {
    private final MftService service;

    @GetMapping
    @Operation(operationId = "listMftPartners", summary = "List AS2 partners")
    public List<PartnerResponse> list() {
        return service.listPartners().stream().map(PartnerResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createMftPartner", summary = "Create an AS2 partner")
    public PartnerResponse create(@Valid @RequestBody CreatePartnerRequest request) {
        return PartnerResponse.from(service.createPartner(new MftService.CreatePartner(request.name(),
                request.localAs2Id(), request.remoteAs2Id(), request.endpoint(), request.localCertificateId(),
                request.partnerSigningCertificateId(), request.partnerEncryptionCertificateId(),
                request.asynchronousMdnEndpoint(), request.mdnMode(),
                request.signingRequired(), request.encryptionRequired(), request.compressionEnabled(),
                request.legacyCryptoEnabled())));
    }

    @GetMapping("/{partnerId}")
    @Operation(operationId = "getMftPartner", summary = "Get an AS2 partner revision")
    public PartnerResponse get(@PathVariable java.util.UUID partnerId) {
        return PartnerResponse.from(service.listPartners().stream().filter(item -> item.id().equals(partnerId))
                .findFirst().orElseThrow(() -> new com.edispark.mft.application.MftNotFoundException("Partner was not found")));
    }

    @PostMapping("/{partnerId}/revisions") @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createMftPartnerRevision", summary = "Create an editable AS2 partner revision")
    public PartnerResponse revise(@PathVariable java.util.UUID partnerId) {
        return PartnerResponse.from(service.createPartnerRevision(partnerId));
    }

    @PutMapping("/{partnerId}/draft")
    @Operation(operationId = "updateMftPartnerDraft", summary = "Update a draft AS2 partner revision")
    public PartnerResponse update(@PathVariable java.util.UUID partnerId, @Valid @RequestBody CreatePartnerRequest request) {
        return PartnerResponse.from(service.updatePartnerDraft(partnerId, command(request)));
    }

    @PostMapping("/{partnerId}/activate")
    @Operation(operationId = "activateMftPartner", summary = "Activate an AS2 partner")
    public PartnerResponse activate(@PathVariable java.util.UUID partnerId) {
        return PartnerResponse.from(service.activatePartner(partnerId));
    }

    @PostMapping("/{partnerId}/schedule")
    @Operation(operationId = "scheduleMftPartnerActivation", summary = "Schedule AS2 partner activation")
    public PartnerResponse schedule(@PathVariable java.util.UUID partnerId, @RequestBody ActivationRequest request) {
        return PartnerResponse.from(service.activatePartner(partnerId, request.effectiveAt()));
    }

    @PostMapping("/{partnerId}/suspend")
    @Operation(operationId = "suspendMftPartner", summary = "Suspend an AS2 partner")
    public PartnerResponse suspend(@PathVariable java.util.UUID partnerId) {
        return PartnerResponse.from(service.suspendPartner(partnerId));
    }

    @PostMapping("/{partnerId}/certificates")
    @Operation(operationId = "rotateMftPartnerCertificates", summary = "Select replacement partner certificates")
    public PartnerResponse rotateCertificates(@PathVariable java.util.UUID partnerId,
            @Valid @RequestBody RotatePartnerCertificatesRequest request) {
        return PartnerResponse.from(service.rotatePartnerCertificates(partnerId,
                request.partnerSigningCertificateId(), request.partnerEncryptionCertificateId()));
    }

    @GetMapping("/{partnerId}/usage")
    @Operation(operationId = "getMftPartnerUsage", summary = "Explain AS2 profile dependencies")
    public MftService.Usage usage(@PathVariable java.util.UUID partnerId) { return service.partnerUsage(partnerId); }

    @PostMapping("/{partnerId}/archive")
    @Operation(operationId = "archiveMftPartnerRevision", summary = "Archive an AS2 partner revision")
    public PartnerResponse archive(@PathVariable java.util.UUID partnerId) {
        return PartnerResponse.from(service.archivePartner(partnerId));
    }

    @DeleteMapping("/{partnerId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteMftPartnerDraft", summary = "Delete an unused AS2 partner draft")
    public void delete(@PathVariable java.util.UUID partnerId) { service.deletePartnerDraft(partnerId); }

    private MftService.CreatePartner command(CreatePartnerRequest request) {
        return new MftService.CreatePartner(request.name(), request.localAs2Id(), request.remoteAs2Id(),
                request.endpoint(), request.localCertificateId(), request.partnerSigningCertificateId(),
                request.partnerEncryptionCertificateId(), request.asynchronousMdnEndpoint(), request.mdnMode(),
                request.signingRequired(), request.encryptionRequired(), request.compressionEnabled(),
                request.legacyCryptoEnabled());
    }

    public record ActivationRequest(java.time.Instant effectiveAt) { }
}
