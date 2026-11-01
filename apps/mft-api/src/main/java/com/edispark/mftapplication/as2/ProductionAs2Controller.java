package com.edispark.mftapplication.as2;

import com.edispark.mft.infrastructure.as2.KmsAs2InboundService;
import com.edispark.mft.infrastructure.as2.As2AdmissionController;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("production")
public class ProductionAs2Controller {
    private final KmsAs2InboundService service;
    private final As2RequestBodyReader bodies;
    private final As2AdmissionController admission;

    public ProductionAs2Controller(KmsAs2InboundService service,
            As2AdmissionController admission,
            @Value("${mft.as2.max-message-bytes:26214400}") long maximumMessageBytes) {
        this.service = service;
        this.admission = admission;
        this.bodies = new As2RequestBodyReader(maximumMessageBytes);
    }

    @PostMapping(path = "/as2")
    public ResponseEntity<byte[]> receive(
            @RequestHeader("AS2-From") String as2From,
            @RequestHeader("AS2-To") String as2To,
            @RequestHeader("Message-ID") String messageId,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
            @RequestHeader(value = "Receipt-Delivery-Option", required = false) String receiptDeliveryOption,
            HttpServletRequest request) throws IOException {
        KmsAs2InboundService.InboundResponse response;
        try (As2AdmissionController.Permit ignored = admission.acquireGlobal()) {
            response = service.receive(as2From, as2To, messageId, contentType,
                    receiptDeliveryOption, bodies.read(request));
        }
        if (response.contentType() == null) return ResponseEntity.ok().build();
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, response.contentType())
                .header("AS2-Version", "1.2")
                .header("AS2-From", as2To)
                .header("AS2-To", as2From)
                .header("Message-ID", response.messageId())
                .body(response.body());
    }

    @PostMapping(path = "/as2/mdn")
    public ResponseEntity<Void> receiveMdn(
            @RequestHeader("AS2-From") String as2From,
            @RequestHeader("AS2-To") String as2To,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
            HttpServletRequest request) throws IOException {
        try (As2AdmissionController.Permit ignored = admission.acquireGlobal()) {
            service.receiveMdn(as2From, as2To, contentType, bodies.read(request));
        }
        return ResponseEntity.ok().build();
    }
}
