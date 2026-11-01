package com.edispark.mft.api.rest;

import com.edispark.mft.api.dto.MdnDeliveryResponse;
import com.edispark.mft.domain.repository.MftRepository;
import io.cottn.core.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mft/mdn-deliveries")
@RequiredArgsConstructor
public class MdnDeliveryController {
    private final MftRepository repository;

    @GetMapping
    @Operation(operationId = "listMftMdnDeliveries", summary = "List asynchronous receipt delivery evidence")
    public List<MdnDeliveryResponse> list() {
        return repository.listMdnDeliveries(TenantContext.getCurrentTenant().asString()).stream()
                .map(MdnDeliveryResponse::from).toList();
    }
}
