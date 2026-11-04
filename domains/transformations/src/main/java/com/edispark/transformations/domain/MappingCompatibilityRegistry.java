package com.edispark.transformations.domain;

import java.util.Map;
import java.util.UUID;

final class MappingCompatibilityRegistry {
    private static final UUID UBL_ORDER = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac10");
    private static final UUID UBL_INVOICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac11");
    private static final UUID EDIFACT_ORDER = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac12");
    private static final UUID EDIFACT_INVOICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac13");
    private static final UUID TRADACOMS_ORDER = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac14");
    private static final UUID TRADACOMS_INVOICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac15");
    private static final UUID UBL_ORDER_RESPONSE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac16");
    private static final UUID UBL_DESPATCH_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac17");
    private static final UUID UBL_REMITTANCE_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac18");
    private static final UUID UBL_RECEIPT_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac19");
    private static final UUID EDIFACT_ORDER_RESPONSE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac30");
    private static final UUID EDIFACT_DESPATCH_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac31");
    private static final UUID EDIFACT_REMITTANCE_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac32");
    private static final UUID EDIFACT_RECEIPT_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac33");
    private static final UUID TRADACOMS_ORDER_RESPONSE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac34");
    private static final UUID TRADACOMS_DESPATCH_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac35");
    private static final UUID TRADACOMS_REMITTANCE_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac36");
    private static final UUID TRADACOMS_RECEIPT_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac37");

    private static final Map<UUID, DocumentSchema> DOCUMENTS = Map.ofEntries(
            Map.entry(UBL_ORDER, new DocumentSchema("ORDER", "ORDERS", null, null)),
            Map.entry(UBL_INVOICE, new DocumentSchema("INVOICE", "INVOIC", null, null)),
            Map.entry(UBL_ORDER_RESPONSE, new DocumentSchema("ORDER_RESPONSE", "ORDRSP", null, null)),
            Map.entry(UBL_DESPATCH_ADVICE, new DocumentSchema("DESPATCH_ADVICE", "DESADV", null, null)),
            Map.entry(UBL_REMITTANCE_ADVICE, new DocumentSchema("REMITTANCE_ADVICE", "REMADV", null, null)),
            Map.entry(UBL_RECEIPT_ADVICE, new DocumentSchema("RECEIPT_ADVICE", "RECADV", null, null)),
            Map.entry(EDIFACT_ORDER, new DocumentSchema("ORDER", "ORDERS", "EDIFACT", "D96A")),
            Map.entry(EDIFACT_INVOICE, new DocumentSchema("INVOICE", "INVOIC", "EDIFACT", "D96A")),
            Map.entry(EDIFACT_ORDER_RESPONSE, new DocumentSchema("ORDER_RESPONSE", "ORDRSP", "EDIFACT", "D96A")),
            Map.entry(EDIFACT_DESPATCH_ADVICE, new DocumentSchema("DESPATCH_ADVICE", "DESADV", "EDIFACT", "D96A")),
            Map.entry(EDIFACT_REMITTANCE_ADVICE, new DocumentSchema("REMITTANCE_ADVICE", "REMADV", "EDIFACT", "D96A")),
            Map.entry(EDIFACT_RECEIPT_ADVICE, new DocumentSchema("RECEIPT_ADVICE", "RECADV", "EDIFACT", "D96A")),
            Map.entry(TRADACOMS_ORDER, new DocumentSchema("ORDER", "ORDERS", "TRADACOMS", "9")),
            Map.entry(TRADACOMS_INVOICE, new DocumentSchema("INVOICE", "INVOIC", "TRADACOMS", "9")),
            Map.entry(TRADACOMS_ORDER_RESPONSE, new DocumentSchema("ORDER_RESPONSE", "ORDRSP", "TRADACOMS", "9")),
            Map.entry(TRADACOMS_DESPATCH_ADVICE, new DocumentSchema("DESPATCH_ADVICE", "DESADV", "TRADACOMS", "9")),
            Map.entry(TRADACOMS_REMITTANCE_ADVICE, new DocumentSchema("REMITTANCE_ADVICE", "REMADV", "TRADACOMS", "9")),
            Map.entry(TRADACOMS_RECEIPT_ADVICE, new DocumentSchema("RECEIPT_ADVICE", "RECADV", "TRADACOMS", "9")));

    private MappingCompatibilityRegistry() { }

    static MappingCompatibility resolve(UUID sourceSchemaId, UUID targetSchemaId) {
        DocumentSchema source = DOCUMENTS.get(sourceSchemaId);
        DocumentSchema target = DOCUMENTS.get(targetSchemaId);
        if (source == null || target == null || !source.documentType().equals(target.documentType())) return null;
        boolean inbound = source.standard() != null && target.standard() == null;
        boolean outbound = source.standard() == null && target.standard() != null;
        if (!inbound && !outbound) return null;
        DocumentSchema edi = inbound ? source : target;
        return new MappingCompatibility(inbound ? "INBOUND" : "OUTBOUND", source.documentType(), edi.standard(),
                edi.release(), edi.messageType());
    }

    private static UUID id(String value) { return UUID.fromString(value); }

    private record DocumentSchema(String documentType, String messageType, String standard, String release) { }
}
