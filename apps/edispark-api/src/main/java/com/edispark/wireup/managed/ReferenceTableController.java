package com.edispark.wireup.managed;

import com.edispark.integrationapplication.persistence.ReferenceTables;
import com.edispark.transformations.domain.ReferenceTableRevision;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/managed-partners/reference-tables")
public final class ReferenceTableController {
    private final ObjectProvider<ReferenceTables> tables;

    ReferenceTableController(ObjectProvider<ReferenceTables> tables) {
        this.tables = tables;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "publishPartnerReferenceTable")
    public ReferenceTableRevision publish(@Valid @RequestBody Publish request) {
        return tables.getObject().publish(new ReferenceTableRevision(request.id(), request.previousRevisionId(),
                request.name(), request.keyColumn(), request.rows()));
    }

    @GetMapping("/{revisionId}")
    @Operation(operationId = "getPartnerReferenceTable")
    public ReferenceTableRevision get(@PathVariable UUID revisionId) {
        return tables.getObject().require(revisionId);
    }

    public record Publish(@NotNull UUID id, UUID previousRevisionId, @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 64) String keyColumn, @NotEmpty @Size(max = 1_000) List<Map<String, String>> rows) { }
}
