package com.edispark.wireup.mig;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.integrationapplication.mig.MigGeneration.State;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@Hidden
@Profile("!openapi-export & (local | production)")
@RequestMapping("/api/platform/mig")
@RequiredArgsConstructor
final class MigReconciliationController {
    private static final Logger LOG = LoggerFactory.getLogger(MigReconciliationController.class);
    private final ObjectProvider<MigWorkflow> workflows;

    @PostMapping("/{jobId}/invocations/{invocationId}/reconcile")
    State reconcile(@PathVariable UUID jobId, @PathVariable UUID invocationId, @Valid @RequestBody Receipt receipt,
            JwtAuthenticationToken actor) {
        var workflow = workflows.getIfAvailable();
        if (workflow == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var tenant = TenantId.fromString(receipt.tenantId());
        var result = TenantContext.getWhere(TenantContextHolder.of(tenant), () ->
                workflow.reconcile(jobId, invocationId, receipt.inputTokens(), receipt.outputTokens(), receipt.reference()));
        LOG.info("MIG usage reconciliation job={} invocation={} tenant={} operator={} state={}",
                jobId, invocationId, tenant, actor.getName(), result);
        return result;
    }

    record Receipt(@NotBlank @Pattern(regexp = "[A-Za-z0-9]{27}") String tenantId,
            @NotNull @Min(0) @Max(MigCandidateModel.INPUT_TOKEN_LIMIT) Integer inputTokens,
            @NotNull @Min(0) @Max(MigCandidateModel.OUTPUT_TOKENS) Integer outputTokens,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_:/.-]{1,120}") String reference) { }
}
