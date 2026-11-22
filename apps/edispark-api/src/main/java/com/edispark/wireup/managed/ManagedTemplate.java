package com.edispark.wireup.managed;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.partnerships.domain.model.AcknowledgementPolicy;
import com.edispark.partnerships.domain.model.BatchingPolicy;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.partnerships.domain.model.InboundFileFailurePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ManagedTemplate(UUID id, int revision, long version, Status status, @Valid Definition definition,
                              String changedBy, Instant changedAt) {
    public enum Status { DRAFT, PUBLISHED, RETIRED }
    public enum Verification { UNVERIFIED, SIMULATOR_TESTED, LOCAL_DEMO }

    public record Definition(@NotBlank @Size(max = 120) String name,
                             @NotNull Verification verification,
                             @NotNull InboundFileFailurePolicy inboundFileFailurePolicy,
                             @Valid @NotNull Transport transport,
                             @NotEmpty @Size(max = 12) List<@Valid @NotNull Capability> capabilities) {
        public Definition {
            if (capabilities != null) capabilities = List.copyOf(capabilities);
        }
    }

    public record Transport(@NotNull MdnMode mdnMode, boolean signingRequired, boolean encryptionRequired,
                            boolean compressionEnabled) {}

    public record Capability(@NotNull ExchangeDirection direction, @NotNull BusinessDocumentType documentType,
                             @NotNull EdiStandard standard, @NotBlank @Size(max = 12) String release,
                             @NotBlank @Size(max = 12) String messageType,
                             @NotNull UUID sourceSchemaId, @NotNull UUID targetSchemaId, @NotNull UUID mappingRevisionId) {
        ExchangeCapability bind(UUID id, UUID connection) {
            return new ExchangeCapability(id, direction, documentType, standard, release, messageType, sourceSchemaId,
                    targetSchemaId, mappingRevisionId, connection, AcknowledgementPolicy.TRANSPORT_ONLY,
                    BatchingPolicy.INDIVIDUAL);
        }
    }
}
