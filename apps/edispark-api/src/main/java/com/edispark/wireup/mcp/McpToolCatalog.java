package com.edispark.wireup.mcp;

import static com.edispark.wireup.mcp.McpTools.*;

import com.edispark.documents.application.reconciliation.UblReconciliation.Comparison;
import com.edispark.documents.domain.model.DocumentLifecycleStatus;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.exchange.domain.ExchangeStatus;
import com.edispark.integrationapplication.inbound.InboundEdiTranslator;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class McpToolCatalog {
    private static final Map<String, Object> ID = Map.of("type", "string", "format", "uuid",
            "pattern", "^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
    private static final Map<String, Object> INSTANT = Map.of("type", "string", "format", "date-time", "maxLength", 40);
    private static final Map<String, Object> PINS = Map.of("documentSetId", ID, "documentId", ID, "representationId", ID,
            "contentHash", Map.of("type", "string", "pattern", "^sha-256:[0-9a-fA-F]{64}$"));
    private static final Map<String, Object> ROUTE = Map.of("agreementId", ID, "capabilityId", ID);
    private static final Map<String, Object> DOCUMENT = Map.of("documentSetId", ID, "documentId", ID);

    private McpToolCatalog() { }

    static void register(McpTools tools, McpCursor cursor, McpExchanges exchanges, McpDocuments documents,
                         McpMappings mappings, McpPartnerships partnerships, McpMft mft, InboundEdiTranslator inbound) {
        registerExchangeTools(tools, cursor, exchanges, mappings, partnerships);
        registerDocumentTools(tools, cursor, documents);
        registerConfigurationTools(tools, cursor, exchanges, documents, mappings, partnerships, inbound);
        registerMftTools(tools, cursor, mft);
    }

    private static void registerExchangeTools(McpTools tools, McpCursor cursor, McpExchanges exchanges,
                                              McpMappings mappings, McpPartnerships partnerships) {
        var filters = new LinkedHashMap<>(Map.<String, Object>of("exchangeId", ID, "from", INSTANT, "to", INSTANT, "partnerId", ID,
                "sourceMessageReference", Map.of("type", "string", "maxLength", 128, "pattern", "^[^\\p{Cntrl}]+$"),
                "direction", enumValues(ExchangeDirection.values()), "status", enumValues(ExchangeStatus.values()),
                "cursor", boundedString(McpCursor.TOKEN_LENGTH)));
        filters.put("documentType", enumValues(DocumentType.values()));
        filters.putAll(DOCUMENT);
        tools.register("find_exchanges", "Find exchanges by ID, document, or a date window of up to 31 days.", READ,
                schema(filters), "matches", (args, principal) -> exchanges.find(args, cursor, principal.tenant().asString()));
        tools.register("trace_exchange", "Read persisted document, mapping, transfer and acknowledgement evidence.", READ,
                schema(Map.of("exchangeId", ID), "exchangeId"), "exchangeId", (args, principal) -> exchanges.trace(args));
        tools.register("get_route_context", "Read a persisted trading agreement capability and its qualification and revision pins.", READ,
                schema(ROUTE, "agreementId", "capabilityId"), "capabilityId", (args, principal) -> partnerships.route(args));
        var mapping = new LinkedHashMap<>(PINS);
        mapping.putAll(Map.of("mappingRevisionId", ID, "input", boundedString(32_768), "expected", boundedString(32_768)));
        tools.register("preview_mapping", "Test a mapping revision using synthetic input and expected JSON strings, or an exact retained outbound representation.",
                PREVIEW, schema(mapping, "mappingRevisionId"), "passed", (args, principal) -> mappings.preview(args));
        var readiness = new LinkedHashMap<>(PINS);
        readiness.putAll(ROUTE);
        tools.register("check_outbound_readiness", "Check whether a pinned document is ready for the selected outbound route.", PREVIEW,
                schema(readiness, "documentSetId", "documentId", "representationId", "contentHash", "agreementId", "capabilityId"),
                "ready", (args, principal) -> exchanges.readiness(args));
    }

    private static void registerDocumentTools(McpTools tools, McpCursor cursor, McpDocuments documents) {
        tools.register("find_documents", "Find documents by exact business reference or a date window of up to 31 days.", READ,
                schema(Map.of("businessReference", boundedString(128), "from", INSTANT, "to", INSTANT, "partnerId", ID,
                        "documentType", enumValues(DocumentType.values()), "lifecycleStatus", enumValues(DocumentLifecycleStatus.values()),
                        "cursor", boundedString(McpCursor.PAGE_TOKEN_LENGTH))), "matches",
                (args, principal) -> documents.find(args, cursor, principal.tenant().asString()));
        tools.register("get_document_context", "Read the document lifecycle, current canonical pins, and retained artifact references.", READ,
                schema(DOCUMENT, "documentSetId", "documentId"), "documentId",
                (args, principal) -> documents.context(new McpArguments(args).id("documentSetId"), new McpArguments(args).id("documentId")));
        var workflow = new LinkedHashMap<>(DOCUMENT);
        workflow.put("cursor", boundedString(McpCursor.PAGE_TOKEN_LENGTH));
        tools.register("get_document_workflow", "Follow recorded document links in both directions across document sets.", READ,
                schema(workflow, "documentSetId", "documentId"), "documentId",
                (args, principal) -> documents.workflow(args, cursor, principal.tenant().asString()));
        tools.register("list_eligible_routes", "Find currently recorded qualified outbound routes for this document.", READ,
                schema(DOCUMENT, "documentSetId", "documentId"), "documentId",
                (args, principal) -> documents.routes(new McpArguments(args).id("documentSetId"), new McpArguments(args).id("documentId")));
        tools.register("prepare_related_document_draft", "Open the existing smart-copy authoring form for an order response.", PREVIEW,
                schema(DOCUMENT, "documentSetId", "documentId"), "workspacePath", (args, principal) -> documents.orderResponseLink(new McpArguments(args).id("documentSetId"), new McpArguments(args).id("documentId")));
        tools.register("prepare_invoice_draft", "Prepare a review link for a related invoice from a recorded order, order response, despatch advice or receipt advice. The link opens an editable smart-copy form. It does not save or send an invoice.", PREVIEW,
                schema(DOCUMENT, "documentSetId", "documentId"), "workspacePath", (args, principal) -> documents.invoiceLink(new McpArguments(args).id("documentSetId"), new McpArguments(args).id("documentId")));
        tools.register("prepare_document_correction", "Open the existing smart-copy form for a corrected draft of a finalized document.", PREVIEW,
                schema(DOCUMENT, "documentSetId", "documentId"), "workspacePath", (args, principal) -> documents.correctionLink(new McpArguments(args).id("documentSetId"), new McpArguments(args).id("documentId")));
        var reconciliationPins = schema(PINS, "documentSetId", "documentId", "representationId", "contentHash");
        var oneRelated = Map.of("type", "array", "items", reconciliationPins, "minItems", 1, "maxItems", 1);
        var multipleRelated = Map.of("type", "array", "items", reconciliationPins, "minItems", 1, "maxItems", 8);
        tools.register("compare_order_to_response", "Compare explicitly referenced order and order response quantities.", READ,
                schema(Map.of("primary", reconciliationPins, "related", oneRelated), "primary", "related"), "findings",
                (args, principal) -> documents.reconcile(Comparison.ORDER_RESPONSE, args, principal.scopes().contains(COMMERCIAL)));
        tools.register("reconcile_despatch_and_receipt", "Compare shipped and received quantities across related receipts.", READ,
                schema(Map.of("primary", reconciliationPins, "related", multipleRelated), "primary", "related"), "findings",
                (args, principal) -> documents.reconcile(Comparison.DESPATCH_RECEIPT, args, principal.scopes().contains(COMMERCIAL)));
        tools.register("reconcile_invoice_and_remittance", "Compare invoice payable amount with explicitly referenced remittance allocations.", READ,
                schema(Map.of("primary", reconciliationPins, "related", multipleRelated), "primary", "related"), "findings",
                (args, principal) -> documents.reconcile(Comparison.INVOICE_REMITTANCE, args, principal.scopes().contains(COMMERCIAL)));
    }

    private static void registerConfigurationTools(McpTools tools, McpCursor cursor, McpExchanges exchanges,
                                                    McpDocuments documents, McpMappings mappings,
                                                    McpPartnerships partnerships, InboundEdiTranslator inbound) {
        tools.register("get_mapping_revision", "Read persisted mapping revision identity, schema and compatibility metadata.", READ,
                schema(Map.of("mappingRevisionId", ID), "mappingRevisionId"), "mappingRevisionId", (args, principal) -> mappings.mappingRevision(args));
        tools.register("compare_mapping_revisions", "Compare the node identities of two revisions with the same source and target schemas.", PREVIEW,
                schema(Map.of("baselineRevisionId", ID, "candidateRevisionId", ID), "baselineRevisionId", "candidateRevisionId"),
                "candidateRevisionId", (args, principal) -> mappings.compareMappings(args));
        tools.register("get_document_profile", "Read a retained document profile and its field requirement categories.", READ,
                schema(Map.of("documentProfileRevisionId", ID), "documentProfileRevisionId"), "documentProfileRevisionId",
                (args, principal) -> partnerships.documentProfile(args));
        tools.register("validate_document_preview", "Validate the current retained canonical representation without storing findings.", PREVIEW,
                schema(PINS, "documentSetId", "documentId", "representationId", "contentHash"), "passed",
                (args, principal) -> documents.validate(args));
        tools.register("find_document_exceptions", "Find recorded document review, processing failure, and pending receipt states.", READ,
                schema(Map.of("from", INSTANT, "to", INSTANT, "partnerId", ID, "cursor", boundedString(McpCursor.TOKEN_LENGTH),
                        "groupByDocument", Map.of("type", "boolean")),
                        "from", "to"), "matches", (args, principal) -> exchanges.documentExceptions(args, cursor, principal.tenant().asString()));
        tools.register("trace_interchange", "Find recorded business exchanges split from or linked to a transfer.", READ,
                schema(Map.of("transferId", ID, "cursor", boundedString(McpCursor.TOKEN_LENGTH)), "transferId"), "matches",
                (args, principal) -> exchanges.interchange(args, cursor, principal.tenant().asString()));
        var codeCatalog = new com.edispark.transformations.domain.BusinessCodeCatalog();
        tools.register("list_business_codes", "Read standard partner-independent business code lists and their syntax representations.", READ,
                schema(Map.of("listId", enumValues(com.edispark.transformations.domain.BusinessCodeList.values()))), "matches",
                (args, principal) -> {
                    var lists = args.containsKey("listId")
                            ? List.of(codeCatalog.get(com.edispark.transformations.domain.BusinessCodeList.valueOf((String) args.get("listId"))))
                            : codeCatalog.list();
                    return Map.of("matches", lists.stream().map(list ->
                            args.containsKey("listId") ? McpResponseFields.fields("listId", list.id(), "authority", list.authority(),
                                    "version", list.version(), "values", list.values())
                                    : McpResponseFields.fields("listId", list.id(), "authority", list.authority(), "version", list.version())).toList());
                });
        tools.register("preview_route_qualification", "Run existing route qualification checks on a persisted agreement without saving a result.", PREVIEW,
                schema(Map.of("agreementId", ID), "agreementId"), "passed",
                (args, principal) -> partnerships.previewQualification(args));
        tools.register("preview_inbound_interchange", "Inspect the split and translated message structure of a supplied EDI fixture.", PREVIEW,
                schema(Map.of("input", boundedString(32768)), "input"), "messageCount", (args, principal) -> {
                    byte[] input = ((String) args.get("input")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    if (input.length > 32768) throw new IllegalArgumentException();
                    var messages = inbound.preview(input, 32);
                    int count = messages.getFirst().messageCount();
                    return McpResponseFields.fields("messageCount", count, "truncated", count > messages.size(), "messages",
                            messages.stream().map(message -> McpResponseFields.fields("messageOrdinal", message.ordinal(),
                                    "standard", message.standard(), "release", message.valid() ? McpResponseFields.safePath(message.release()) : null,
                                    "messageType", message.valid() ? McpResponseFields.safeCode(message.messageType()) : null,
                                    "documentType", message.documentType(), "valid", message.valid())).toList());
                });
    }

    private static void registerMftTools(McpTools tools, McpCursor cursor, McpMft mft) {
        var mftCursor = boundedString(McpCursor.TOKEN_LENGTH);
        tools.register("find_transfers", "Find a standalone or B2B-linked AS2 transfer.", MFT_READ,
                schema(Map.of("transferId", ID, "connectionId", ID, "from", INSTANT, "to", INSTANT, "cursor", mftCursor,
                        "direction", enumValues(com.edispark.mft.domain.model.TransferDirection.values()),
                        "status", enumValues(com.edispark.mft.domain.model.TransferStatus.values()))), "matches",
                (args, principal) -> mft.transfers(args, cursor, principal.tenant().asString()));
        tools.register("trace_transfer", "Read recorded AS2 receipt, attempts, retry timing and provenance.", MFT_READ,
                schema(Map.of("transferId", ID), "transferId"), "transferId", (args, principal) -> mft.trace(args));
        tools.register("find_mdn_deliveries", "Find asynchronous AS2 receipts sent for an inbound transfer.", MFT_READ,
                schema(Map.of("transferId", ID, "cursor", mftCursor), "transferId"), "matches",
                (args, principal) -> mft.receipts(args, cursor, principal.tenant().asString()));
        tools.register("find_connections", "Read persisted AS2 connection revision metadata.", MFT_READ,
                schema(Map.of("connectionId", ID, "cursor", mftCursor,
                        "status", enumValues(com.edispark.mft.domain.model.PartnerStatus.values()))), "matches",
                (args, principal) -> mft.connections(args, cursor, principal.tenant().asString()));
        tools.register("find_certificates", "Find AS2 certificate validity and fingerprint metadata.", MFT_READ,
                schema(Map.of("certificateId", ID, "cursor", mftCursor, "from", INSTANT, "to", INSTANT,
                        "kind", enumValues(com.edispark.mft.domain.model.CertificateKind.values()))), "matches",
                (args, principal) -> mft.certificates(args, cursor, principal.tenant().asString()));
        tools.register("get_connection_setup", "Read AS2 identity, supported algorithms and public certificate fingerprints for onboarding.", MFT_READ,
                schema(Map.of("connectionId", ID), "connectionId"), "connectionId", (args, principal) -> mft.setup(args));
        tools.register("get_certificate_usage", "Find recorded usage of a certificate across AS2 connection revisions.", MFT_READ,
                schema(Map.of("certificateId", ID, "cursor", mftCursor), "certificateId"), "matches",
                (args, principal) -> mft.certificateUsage(args, cursor, principal.tenant().asString()));
        tools.register("compare_connection_revisions", "Identify configuration fields that changed across revisions of one connection.", MFT_READ,
                schema(Map.of("baselineConnectionId", ID, "candidateConnectionId", ID), "baselineConnectionId", "candidateConnectionId"),
                "candidateConnectionId", (args, principal) -> mft.compareConnections(args));
        tools.register("check_transfer_readiness", "Check the recorded connection and certificate policy without sending a file.", MFT_READ,
                schema(Map.of("connectionId", ID), "connectionId"), "ready", (args, principal) -> mft.readiness(args));
        tools.register("preview_certificate_rollover", "Check a proposed partner certificate pair against current AS2 connection policy.", MFT_READ,
                schema(Map.of("connectionId", ID, "signingCertificateId", ID, "encryptionCertificateId", ID),
                        "connectionId", "signingCertificateId", "encryptionCertificateId"), "ready", (args, principal) -> mft.rollover(args));
    }

    private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        return Map.of("type", "object", "properties", properties, "required", List.of(required), "additionalProperties", false);
    }

    private static Map<String, Object> boundedString(int length) {
        return Map.of("type", "string", "minLength", 1, "maxLength", length);
    }

    private static Map<String, Object> enumValues(Enum<?>[] values) {
        return Map.of("type", "string", "enum", Arrays.stream(values).map(Enum::name).toList());
    }

}
