package com.edispark.transformations.api;

import com.edispark.transformations.application.MappingService;
import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingRevision;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mappings")
@RequiredArgsConstructor
public final class MappingController {
    private final MappingService service;

    @GetMapping @Operation(operationId = "listMappings", summary = "List visual mappings")
    public List<MappingRevision> list() { return service.list(); }

    @GetMapping("/{mappingId}") @Operation(operationId = "getMapping", summary = "Get a mapping revision")
    public MappingRevision get(@PathVariable UUID mappingId) { return service.get(mappingId); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createMapping", summary = "Create a visual mapping draft")
    public MappingRevision create(@Valid @RequestBody CreateMappingRequest request) {
        return service.create(request.name(), request.sourceSchemaId(), request.targetSchemaId(), request.nodes());
    }

    @PutMapping("/{mappingId}/draft") @Operation(operationId = "updateMappingDraft", summary = "Replace a draft mapping graph")
    public MappingRevision update(@PathVariable UUID mappingId, @Valid @RequestBody UpdateMappingRequest request) {
        return service.update(mappingId, request.nodes());
    }

    @PostMapping("/{mappingId}/test") @Operation(operationId = "testMapping", summary = "Run a mapping against a synthetic fixture")
    public MappingService.TestResult test(@PathVariable UUID mappingId, @Valid @RequestBody MappingFixtureRequest request) {
        return service.test(mappingId, bytes(request.input()), request.expected() == null ? null : bytes(request.expected()));
    }

    @PostMapping("/{mappingId}/publish") @Operation(operationId = "publishMapping", summary = "Publish an immutable mapping revision")
    public MappingRevision publish(@PathVariable UUID mappingId, @Valid @RequestBody PublishMappingRequest request) {
        return service.publish(mappingId, request.fixtures().stream()
                .map(item -> new MappingService.Fixture(item.name(), bytes(item.input()), bytes(item.expected()))).toList(),
                request.effectiveAt());
    }

    @PostMapping("/{mappingId}/archive")
    @Operation(operationId = "archiveMappingRevision", summary = "Archive a retained mapping revision")
    public MappingRevision archive(@PathVariable UUID mappingId) { return service.archive(mappingId); }

    @GetMapping("/{mappingId}/usage")
    @Operation(operationId = "getMappingUsage", summary = "Explain mapping deletion dependencies")
    public MappingService.Usage usage(@PathVariable UUID mappingId) { return service.usage(mappingId); }

    @DeleteMapping("/{mappingId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteMappingDraft", summary = "Permanently delete an unused mapping draft")
    public void delete(@PathVariable UUID mappingId) { service.deleteDraft(mappingId); }

    @PostMapping("/{mappingId}/revisions") @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createMappingRevision", summary = "Create an editable revision from a published mapping")
    public MappingRevision revise(@PathVariable UUID mappingId) { return service.createRevision(mappingId); }

    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    public record CreateMappingRequest(@NotBlank @Size(max = 160) String name, @NotNull UUID sourceSchemaId,
                                       @NotNull UUID targetSchemaId, @NotEmpty List<@Valid MappingNode> nodes) { }
    public record UpdateMappingRequest(@NotEmpty List<@Valid MappingNode> nodes) { }
    public record MappingFixtureRequest(@NotBlank String name, @NotBlank String input, String expected) { }
    public record PublishMappingRequest(@NotEmpty List<@Valid MappingFixtureRequest> fixtures,
                                        java.time.Instant effectiveAt) { }
}
