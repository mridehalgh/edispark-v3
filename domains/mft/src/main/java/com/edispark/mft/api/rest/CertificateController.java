package com.edispark.mft.api.rest;

import com.edispark.mft.api.dto.CertificateResponse;
import com.edispark.mft.application.CertificateService;
import com.edispark.mft.domain.model.CertificateKind;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mft/certificates")
@RequiredArgsConstructor
public class CertificateController {
    private final CertificateService service;

    @GetMapping
    @Operation(operationId = "listMftCertificates", summary = "List certificate metadata")
    public List<CertificateResponse> list() {
        return service.list().stream().map(CertificateResponse::from).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "uploadMftCertificate", summary = "Validate and store certificate material")
    public CertificateResponse upload(@RequestParam String name,
                                      @RequestParam CertificateKind kind,
                                      @RequestParam(required = false, defaultValue = "") String password,
                                      @RequestParam MultipartFile file) throws IOException {
        return CertificateResponse.from(service.upload(name, kind, file.getBytes(), password.toCharArray()));
    }

    @GetMapping("/{certificateId}/usage")
    @Operation(operationId = "getMftCertificateUsage", summary = "Explain certificate assignments")
    public CertificateService.Usage usage(@PathVariable java.util.UUID certificateId) { return service.usage(certificateId); }

    @DeleteMapping("/{certificateId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteUnusedMftCertificate", summary = "Delete an unused certificate and its material")
    public void delete(@PathVariable java.util.UUID certificateId) throws IOException { service.deleteUnused(certificateId); }
}
