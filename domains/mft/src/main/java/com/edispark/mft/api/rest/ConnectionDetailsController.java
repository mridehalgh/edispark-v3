package com.edispark.mft.api.rest;

import com.edispark.mft.api.dto.As2ConnectionDetailsResponse;
import com.edispark.mft.application.ConnectionDetailsService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mft/connection-details")
@RequiredArgsConstructor
public class ConnectionDetailsController {
    private final ConnectionDetailsService service;

    @GetMapping
    @Operation(operationId = "listMftConnectionDetails", summary = "List retailer-facing AS2 connection details")
    public List<As2ConnectionDetailsResponse> list() {
        return service.list().stream().map(As2ConnectionDetailsResponse::from).toList();
    }
}
