package com.edispark.wireup.mcp;

import static com.edispark.wireup.mcp.McpResponseFields.fields;

import com.edispark.documents.application.handler.DocumentSetCommandHandler;
import com.edispark.documents.application.query.DocumentContentQueryService;
import com.edispark.documents.application.query.DocumentContentQueryService.RepresentationPin;
import com.edispark.documents.application.query.DocumentWorkflowQuery;
import com.edispark.documents.application.query.DocumentWorkflowQuery.AuthoringSource;
import com.edispark.documents.application.query.DocumentWorkflowQuery.Summary;
import com.edispark.documents.application.reconciliation.DocumentReconciliation;
import com.edispark.documents.application.reconciliation.UblReconciliation.Comparison;
import com.edispark.documents.domain.model.DocumentId;
import com.edispark.documents.domain.model.DocumentLifecycleStatus;
import com.edispark.documents.domain.model.DocumentSetId;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.exchange.application.ExchangeService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class McpDocuments {
    private static final int DETAIL_LIMIT = 32;
    private static final int RECONCILIATION_LIMIT = 128;

    private final DocumentWorkflowQuery workflow;
    private final DocumentContentQueryService content;
    private final DocumentReconciliation reconciliation;
    private final DocumentSetCommandHandler commands;
    private final ExchangeService exchanges;

    McpDocuments(DocumentWorkflowQuery workflow, DocumentContentQueryService content, DocumentReconciliation reconciliation,
                 DocumentSetCommandHandler commands, ExchangeService exchanges) {
        this.workflow = workflow;
        this.content = content;
        this.reconciliation = reconciliation;
        this.commands = commands;
        this.exchanges = exchanges;
    }

    Map<String, Object> find(Map<String, Object> values, McpCursor cursor, String tenant) {
        var args = new McpArguments(values);
        var binding = args.cursorBinding("find_documents");
        var search = new DocumentWorkflowQuery.Search(args.optionalText("businessReference"), args.optionalInstant("from"),
                args.optionalInstant("to"), args.optionalId("partnerId"), args.optionalEnum("documentType", DocumentType.class),
                args.optionalEnum("lifecycleStatus", DocumentLifecycleStatus.class));
        var page = workflow.find(search, args.pagePosition(cursor, tenant, binding));
        return fields("matches", page.items().stream().map(McpDocuments::summary).toList(), "nextCursor",
                page.continuationToken().map(position -> cursor.wrapPage(position, tenant, binding)).orElse(null));
    }

    Map<String, Object> context(UUID setId, UUID documentId) {
        var info = workflow.describe(new DocumentSetId(setId), new DocumentId(documentId));
        var representation = info.representation();
        var result = summary(info.document());
        result.putAll(fields("representationId", representation == null ? null : representation.id(),
                "contentHash", representation == null ? null : representation.hash().toFullString(),
                "representationStatus", representation == null ? "NOT_RECORDED" : "RECORDED",
                "artifacts", info.artifacts().stream()
                        .map(item -> fields("representationId", item.representationId(), "format", item.format())).toList(),
                "artifactsTruncated", info.artifactsTruncated()));
        return result;
    }

    Map<String, Object> workflow(Map<String, Object> values, McpCursor cursor, String tenant) {
        var args = new McpArguments(values);
        var setId = new DocumentSetId(args.id("documentSetId"));
        var documentId = new DocumentId(args.id("documentId"));
        var binding = args.cursorBinding("get_document_workflow");
        var result = workflow.follow(setId, documentId, args.pagePosition(cursor, tenant, binding));
        var links = result.links().stream().map(link -> fields("relation", link.relation(), "direction", link.direction(),
                "document", link.document() == null ? null : summary(link.document()),
                "evidence", link.document() == null ? "UNAVAILABLE" : null)).toList();
        return fields("documentId", documentId.value(), "links", links, "outgoingTruncated", result.outgoingTruncated(),
                "nextCursor", result.nextPosition() == null ? null : cursor.wrapPage(result.nextPosition(), tenant, binding));
    }

    Map<String, Object> routes(UUID setId, UUID documentId) {
        workflow.describe(new DocumentSetId(setId), new DocumentId(documentId));
        var routes = exchanges.previewEligibleRoutes(setId, documentId);
        return fields("documentId", documentId, "routes", routes.stream().limit(DETAIL_LIMIT).map(route ->
                fields("agreementId", route.agreementId(), "agreementRevision", route.agreementRevision(),
                        "capabilityId", route.capability().id(), "mappingRevisionId", route.capability().mappingRevisionId(),
                        "documentProfileRevisionId", route.capability().documentProfileRevisionId())).toList(),
                "routesTruncated", routes.size() > DETAIL_LIMIT);
    }

    Map<String, Object> validate(Map<String, Object> args) {
        var source = content.readPinnedCanonical(pin(args));
        var result = commands.validateCanonical(source.type(), source.content());
        return fields("passed", result.errors().isEmpty(), "findings", result.errors().stream().limit(DETAIL_LIMIT)
                        .map(issue -> fields("code", McpResponseFields.safeCode(issue.code()),
                                "path", McpResponseFields.safePath(issue.path()), "severity", "ERROR")).toList(),
                "findingsTruncated", result.errors().size() > DETAIL_LIMIT);
    }

    Map<String, Object> reconcile(Comparison comparison, Map<String, Object> args, boolean commercial) {
        var primary = pin((Map<?, ?>) args.get("primary"));
        var related = ((List<?>) args.get("related")).stream().map(item -> pin((Map<?, ?>) item)).toList();
        var report = reconciliation.compare(comparison, primary, related);
        return fields("findings", report.findings().stream().limit(RECONCILIATION_LIMIT).map(finding ->
                        fields("code", finding.code(), "sourceIndex", finding.sourceIndex(),
                                "relatedIndex", finding.relatedIndex(), "lineIndex", finding.lineIndex())).toList(),
                "matched", report.findings().isEmpty(), "findingsTruncated", report.findings().size() > RECONCILIATION_LIMIT,
                "values", commercial ? report.values().stream().limit(RECONCILIATION_LIMIT).toList() : null,
                "valuesTruncated", commercial ? report.values().size() > RECONCILIATION_LIMIT : null);
    }

    Map<String, Object> orderResponseLink(UUID setId, UUID documentId) {
        var source = workflow.orderResponseSource(new DocumentSetId(setId), new DocumentId(documentId));
        return authoringLink(setId, documentId, source, "");
    }

    Map<String, Object> correctionLink(UUID setId, UUID documentId) {
        var source = workflow.correctionSource(new DocumentSetId(setId), new DocumentId(documentId));
        return authoringLink(setId, documentId, source, "&mode=correct");
    }

    private static Map<String, Object> authoringLink(UUID setId, UUID documentId, AuthoringSource source, String mode) {
        var representation = source.representation();
        if (representation == null) throw new McpResponseFields.EvidenceMissing();
        String path = "/documents/new?type=" + source.target() + "&fromSet=" + setId + "&fromDocument=" + documentId
                + "&fromRepresentation=" + representation.id() + "&fromHash=" + representation.hash().toFullString() + mode;
        return fields("workspacePath", path, "documentType", source.target(), "status", "REVIEW_IN_WORKSPACE");
    }

    private static RepresentationPin pin(Map<?, ?> source) {
        return new RepresentationPin(new DocumentSetId(UUID.fromString((String) source.get("documentSetId"))),
                new DocumentId(UUID.fromString((String) source.get("documentId"))),
                UUID.fromString((String) source.get("representationId")), (String) source.get("contentHash"));
    }

    private static Map<String, Object> summary(Summary summary) {
        var setId = summary.documentSetId().value();
        var documentId = summary.documentId().value();
        return fields("documentSetId", setId, "documentId", documentId, "documentType", summary.documentType(),
                "lifecycleStatus", summary.lifecycleStatus(), "partnerId", summary.partnerId(),
                "businessReference", summary.businessReference(), "workspacePath", "/documents/" + setId + "/" + documentId);
    }
}
