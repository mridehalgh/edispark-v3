package com.edispark.mft.api.rest;

import com.edispark.mft.api.dto.TransferResponse;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mft/transfers")
@RequiredArgsConstructor
@Validated
public class TransferController {
    private final MftService service;

    @GetMapping
    @Operation(operationId = "listMftTransfers", summary = "List AS2 transfers")
    public List<TransferResponse> list() {
        return service.listTransfers().stream().map(TransferResponse::from).toList();
    }

    @GetMapping("/{transferId}")
    @Operation(operationId = "getMftTransfer", summary = "Get AS2 transfer evidence")
    public TransferResponse get(@PathVariable UUID transferId) {
        return TransferResponse.from(service.getTransfer(new TransferId(transferId)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "submitMftTransfer", summary = "Submit an outbound AS2 transfer")
    public TransferResponse submit(@RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
                                   @RequestParam UUID partnerId,
                                   @RequestParam MultipartFile file,
                                   @RequestParam(required = false) UUID exchangeId,
                                   @RequestParam(required = false) UUID agreementId,
                                   @RequestParam(required = false) Integer agreementRevision,
                                   @RequestParam(required = false) UUID capabilityId,
                                   @RequestParam(required = false) UUID documentSetId,
                                   @RequestParam(required = false) UUID documentId,
                                   @RequestParam(required = false) UUID mappingRevisionId) throws IOException {
        String contentType = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        var provenance = provenance(exchangeId, agreementId, agreementRevision, capabilityId, documentSetId,
                documentId, mappingRevisionId);
        Transfer transfer = service.submitOutbound(new MftService.SubmitOutbound(partnerId, idempotencyKey,
                safeFilename(file), contentType, file.getInputStream(), null, provenance));
        return TransferResponse.from(transfer);
    }

    @GetMapping("/{transferId}/content")
    @Operation(operationId = "downloadMftTransferPayload", summary = "Download retained transfer payload")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID transferId) throws IOException {
        Transfer transfer = service.getTransfer(new TransferId(transferId));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(transfer.contentType()));
        headers.setContentLength(transfer.payload().size());
        headers.setContentDisposition(ContentDisposition.attachment().filename(transfer.filename()).build());
        return new ResponseEntity<>(new InputStreamResource(service.getPayload(transfer.id())), headers, HttpStatus.OK);
    }

    @PostMapping("/{transferId}/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "resendMftTransfer", summary = "Create a linked resend")
    public TransferResponse resend(@PathVariable UUID transferId) throws IOException {
        return TransferResponse.from(service.resend(new TransferId(transferId)));
    }

    private String safeFilename(MultipartFile file) {
        String original = file.getOriginalFilename();
        return original == null || original.isBlank() ? "payload.edi" : original;
    }

    private com.edispark.mft.domain.model.TransferProvenance provenance(UUID exchangeId, UUID agreementId,
            Integer agreementRevision, UUID capabilityId, UUID documentSetId, UUID documentId,
            UUID mappingRevisionId) {
        int present = java.util.stream.Stream.of(exchangeId, agreementId, agreementRevision, capabilityId,
                documentSetId, documentId, mappingRevisionId).mapToInt(value -> value == null ? 0 : 1).sum();
        if (present == 0) return null;
        if (present != 7) throw new IllegalArgumentException("All transfer provenance fields must be supplied together");
        return new com.edispark.mft.domain.model.TransferProvenance(exchangeId, agreementId, agreementRevision,
                capabilityId, documentSetId, documentId, mappingRevisionId);
    }
}
