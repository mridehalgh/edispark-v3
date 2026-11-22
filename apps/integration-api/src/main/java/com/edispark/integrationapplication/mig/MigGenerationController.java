package com.edispark.integrationapplication.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.edispark.integrationapplication.config.IntegrationExceptionHandler.ActionableErrorBody;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/mappings/mig")
@ApiResponse(responseCode = "400", description = "Invalid MIG request", content = @Content(schema = @Schema(implementation = ActionableErrorBody.class)))
@ApiResponse(responseCode = "404", description = "MIG generation unavailable or resource missing", content = @Content(schema = @Schema(implementation = ActionableErrorBody.class)))
@ApiResponse(responseCode = "409", description = "Job state changed or AI credits unavailable", content = @Content(schema = @Schema(implementation = ActionableErrorBody.class)))
public final class MigGenerationController {
    private final ObjectProvider<MigGeneration> generation;

    public MigGenerationController(ObjectProvider<MigGeneration> generation) { this.generation = generation; }

    @GetMapping("/availability")
    @Operation(operationId = "getMigGenerationAvailability", summary = "Check organisation access to MIG generation")
    public Availability availability() {
        var workflow = generation.getIfAvailable();
        boolean enabled = workflow != null;
        return new Availability(enabled, enabled ? workflow.demoJobId() : null, enabled ? workflow.modelMode() : null, enabled ? workflow.modelIds() : Map.of());
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "uploadMigFile", summary = "Upload a guide or fixture")
    public Upload upload(@Valid @RequestBody UploadRequest request) { return workflow().upload(request); }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "estimateMigJob", summary = "Prepare evidence and estimate maximum AI credits")
    public Job estimate(@Valid @RequestBody EstimateRequest request) { return workflow().estimate(request); }

    @GetMapping("/jobs/{jobId}")
    @Operation(operationId = "getMigJob", summary = "Get job status, evidence, and results")
    public Job status(@PathVariable UUID jobId) { return workflow().status(jobId); }

    @PostMapping("/jobs/{jobId}/rerun")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "rerunMigJob", summary = "Create a new estimate using the previous job’s uploads and settings")
    public Job rerun(@PathVariable UUID jobId) { return workflow().rerun(jobId); }

    @PostMapping("/jobs/{jobId}/start")
    @Operation(operationId = "startMigJob", summary = "Reserve AI credits and queue independent generation")
    public Job start(@PathVariable UUID jobId) { return workflow().start(jobId); }

    @PostMapping("/jobs/{jobId}/retry")
    @Operation(operationId = "retryMigJob", summary = "Reserve credits and retry an incomplete candidate")
    public Job retry(@PathVariable UUID jobId) { return workflow().retry(jobId); }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(operationId = "cancelMigJob", summary = "Cancel generation and release unused credits")
    public Job cancel(@PathVariable UUID jobId) { return workflow().cancel(jobId); }

    @PostMapping("/jobs/{jobId}/review")
    @Operation(operationId = "reviewMigJob", summary = "Save analyst decisions and revalidate the reviewed mapping")
    public Job review(@PathVariable UUID jobId, @Valid @RequestBody ReviewRequest request) { return workflow().review(jobId, request); }

    @PostMapping("/jobs/{jobId}/draft")
    @Operation(operationId = "createMigDraft", summary = "Create an editable draft from a validated review")
    public Job draft(@PathVariable UUID jobId, @Valid @RequestBody DraftRequest request) { return workflow().createDraft(jobId, request); }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ActionableErrorBody> unavailable(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ActionableErrorBody("MIG_UNAVAILABLE",
                "This MIG job or upload is unavailable.", Instant.now(), "MIG_GENERATION", false, null,
                UUID.randomUUID().toString(), Map.of()));
    }

    private MigGeneration workflow() {
        var workflow = generation.getIfAvailable();
        if (workflow == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return workflow;
    }
}
