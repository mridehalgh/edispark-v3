package com.edispark.mft.api.rest;

import com.edispark.mft.api.dto.CreatePartnerRequest;
import com.edispark.mft.api.dto.PartnerResponse;
import com.edispark.mft.application.MftService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
                request.partnerCertificateId(), request.mdnMode(),
                request.signingRequired(), request.encryptionRequired(), request.compressionEnabled(),
                request.legacyCryptoEnabled())));
    }
}
