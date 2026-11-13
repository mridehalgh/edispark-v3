package com.edispark.wireup.modules;

import com.edispark.documents.application.handler.DocumentSetCommandHandler;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.edifact.OrderResponseD96a;
import com.edispark.integrationapplication.acl.OrderResponseTargetContract;
import com.edispark.integrationapplication.acl.BqAcknowledgementTargetContract;
import com.edispark.tradacoms.ack.BqAcknowledgementV14;
import com.edispark.transformations.application.TargetContractValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class InProcessMappingTargetValidation implements TargetContractValidator {
    private static final UUID SOURCE = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac16");
    private static final UUID TARGET = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac30");
    private static final UUID BQ_TARGET = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac34");
    private final DocumentSetCommandHandler documents;
    private final OrderResponseTargetContract target;
    private final BqAcknowledgementTargetContract bq;

    InProcessMappingTargetValidation(DocumentSetCommandHandler documents, OrderResponseTargetContract target, BqAcknowledgementTargetContract bq) {
        this.documents = documents; this.target = target; this.bq = bq;
    }

    @Override public Result validate(UUID sourceId, UUID targetId, byte[] source, byte[] mapped) {
        if (!SOURCE.equals(sourceId) || !(TARGET.equals(targetId) || BQ_TARGET.equals(targetId))) {
            return new Result("unavailable", "unavailable", List.of(new Finding("TARGET_RULES_UNAVAILABLE", "/",
                    "No qualified Order Response definition is available for this standard and release.", false)));
        }
        var findings = new ArrayList<Finding>();
        documents.validateCanonical(DocumentType.ORDER_RESPONSE, source).errors().forEach(error ->
                findings.add(new Finding(error.code(), error.path(), error.message(), true)));
        if (BQ_TARGET.equals(targetId)) {
            bq.validate(mapped).issues().forEach(issue -> findings.add(new Finding(issue.code(), issue.path(), issue.message(), false)));
            return new Result(BqAcknowledgementV14.RULES, BqAcknowledgementV14.fingerprint(), findings);
        }
        target.validate(mapped).issues().forEach(issue -> findings.add(new Finding(issue.code(), issue.path(), issue.message(), false)));
        return new Result(OrderResponseD96a.RULES, OrderResponseD96a.fingerprint(), findings);
    }
}
