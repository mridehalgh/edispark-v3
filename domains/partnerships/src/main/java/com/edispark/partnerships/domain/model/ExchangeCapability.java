package com.edispark.partnerships.domain.model;

import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public record ExchangeCapability(UUID id, ExchangeDirection direction, BusinessDocumentType documentType,
        EdiStandard standard, String release, String messageType, UUID sourceSchemaId, UUID targetSchemaId,
        UUID mappingRevisionId, UUID as2ConnectionId, AcknowledgementPolicy acknowledgementPolicy,
        BatchingPolicy batchingPolicy, OutboundLossPolicy outboundLossPolicy,
        WorkflowAutomationMode automationMode, WorkflowApprovalPolicy approvalPolicy,
        BusinessDocumentType responseDocumentType, UUID documentProfileRevisionId,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        Map<String, UUID> referenceTableRevisions) {
    public ExchangeCapability {
        Objects.requireNonNull(id);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(documentType);
        Objects.requireNonNull(standard);
        requireText(release, "release");
        requireText(messageType, "messageType");
        Objects.requireNonNull(sourceSchemaId);
        Objects.requireNonNull(targetSchemaId);
        Objects.requireNonNull(mappingRevisionId);
        Objects.requireNonNull(as2ConnectionId);
        Objects.requireNonNull(acknowledgementPolicy);
        Objects.requireNonNull(batchingPolicy);
        referenceTableRevisions = referenceTableRevisions == null ? Map.of() : referenceTableRevisions;
        if (referenceTableRevisions.size() > 20 || referenceTableRevisions.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || !entry.getKey().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")
                        || entry.getValue() == null)) {
            throw new IllegalArgumentException("Provide up to 20 valid reference table aliases and revisions.");
        }
        referenceTableRevisions = Map.copyOf(referenceTableRevisions);
        outboundLossPolicy = outboundLossPolicy == null ? OutboundLossPolicy.STRICT : outboundLossPolicy;
        if (direction == ExchangeDirection.OUTBOUND && documentType == BusinessDocumentType.ORDER_RESPONSE
                && standard == EdiStandard.TRADACOMS && outboundLossPolicy != OutboundLossPolicy.STRICT) {
            throw new IllegalArgumentException("B&Q acknowledgements require strict source coverage before transmission.");
        }
        automationMode = automationMode == null ? WorkflowAutomationMode.MANUAL : automationMode;
        approvalPolicy = approvalPolicy == null ? WorkflowApprovalPolicy.NONE : approvalPolicy;
        if (automationMode == WorkflowAutomationMode.APPROVAL_REQUIRED
                && approvalPolicy == WorkflowApprovalPolicy.NONE) {
            throw new IllegalArgumentException("Approval-required automation needs an approval policy");
        }
        if (automationMode != WorkflowAutomationMode.MANUAL && direction != ExchangeDirection.INBOUND) {
            throw new IllegalArgumentException("Response automation can only be configured on inbound capabilities");
        }
    }

    /** Compatibility constructor for persisted and source callers created before loss policy was configurable. */
    public ExchangeCapability(UUID id, ExchangeDirection direction, BusinessDocumentType documentType,
            EdiStandard standard, String release, String messageType, UUID sourceSchemaId, UUID targetSchemaId,
            UUID mappingRevisionId, UUID as2ConnectionId, AcknowledgementPolicy acknowledgementPolicy,
            BatchingPolicy batchingPolicy) {
        this(id, direction, documentType, standard, release, messageType, sourceSchemaId, targetSchemaId,
                mappingRevisionId, as2ConnectionId, acknowledgementPolicy, batchingPolicy, OutboundLossPolicy.STRICT,
                WorkflowAutomationMode.MANUAL, WorkflowApprovalPolicy.NONE, null, null);
    }

    public ExchangeCapability(UUID id, ExchangeDirection direction, BusinessDocumentType documentType,
            EdiStandard standard, String release, String messageType, UUID sourceSchemaId, UUID targetSchemaId,
            UUID mappingRevisionId, UUID as2ConnectionId, AcknowledgementPolicy acknowledgementPolicy,
            BatchingPolicy batchingPolicy, OutboundLossPolicy outboundLossPolicy) {
        this(id, direction, documentType, standard, release, messageType, sourceSchemaId, targetSchemaId,
                mappingRevisionId, as2ConnectionId, acknowledgementPolicy, batchingPolicy, outboundLossPolicy,
                WorkflowAutomationMode.MANUAL, WorkflowApprovalPolicy.NONE, null, null);
    }

    public ExchangeCapability(UUID id, ExchangeDirection direction, BusinessDocumentType documentType,
            EdiStandard standard, String release, String messageType, UUID sourceSchemaId, UUID targetSchemaId,
            UUID mappingRevisionId, UUID as2ConnectionId, AcknowledgementPolicy acknowledgementPolicy,
            BatchingPolicy batchingPolicy, OutboundLossPolicy outboundLossPolicy,
            WorkflowAutomationMode automationMode, WorkflowApprovalPolicy approvalPolicy,
            BusinessDocumentType responseDocumentType) {
        this(id, direction, documentType, standard, release, messageType, sourceSchemaId, targetSchemaId,
                mappingRevisionId, as2ConnectionId, acknowledgementPolicy, batchingPolicy, outboundLossPolicy,
                automationMode, approvalPolicy, responseDocumentType, null);
    }

    public ExchangeCapability(UUID id, ExchangeDirection direction, BusinessDocumentType documentType,
            EdiStandard standard, String release, String messageType, UUID sourceSchemaId, UUID targetSchemaId,
            UUID mappingRevisionId, UUID as2ConnectionId, AcknowledgementPolicy acknowledgementPolicy,
            BatchingPolicy batchingPolicy, OutboundLossPolicy outboundLossPolicy,
            WorkflowAutomationMode automationMode, WorkflowApprovalPolicy approvalPolicy,
            BusinessDocumentType responseDocumentType, UUID documentProfileRevisionId) {
        this(id, direction, documentType, standard, release, messageType, sourceSchemaId, targetSchemaId,
                mappingRevisionId, as2ConnectionId, acknowledgementPolicy, batchingPolicy, outboundLossPolicy,
                automationMode, approvalPolicy, responseDocumentType, documentProfileRevisionId, null);
    }

    public ExchangeCapability withReferenceTable(String alias, UUID revisionId) {
        var tables = new HashMap<>(referenceTableRevisions);
        tables.put(alias, revisionId);
        return new ExchangeCapability(id, direction, documentType, standard, release, messageType,
                sourceSchemaId, targetSchemaId, mappingRevisionId, as2ConnectionId, acknowledgementPolicy,
                batchingPolicy, outboundLossPolicy, automationMode, approvalPolicy, responseDocumentType,
                documentProfileRevisionId, tables);
    }

    public String routingKey() {
        return direction + ":" + standard + ":" + release + ":" + messageType;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
