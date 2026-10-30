package com.edispark.mftapplication.as2;

import com.edispark.mft.infrastructure.as2.KmsAs2InboundService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("production")
public class ProductionAs2Controller {
    private final KmsAs2InboundService service;

    public ProductionAs2Controller(KmsAs2InboundService service) {
        this.service = service;
    }

    @PostMapping(path = "/as2")
    public ResponseEntity<byte[]> receive(
            @RequestHeader("AS2-From") String as2From,
            @RequestHeader("AS2-To") String as2To,
            @RequestHeader("Message-ID") String messageId,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
            @RequestHeader(value = "Receipt-Delivery-Option", required = false) String receiptDeliveryOption,
            @RequestBody byte[] body) {
        KmsAs2InboundService.InboundResponse response = service.receive(
                as2From, as2To, messageId, contentType, receiptDeliveryOption, body);
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
            @RequestBody byte[] body) {
        service.receiveMdn(as2From, as2To, contentType, body);
        return ResponseEntity.ok().build();
    }
}
