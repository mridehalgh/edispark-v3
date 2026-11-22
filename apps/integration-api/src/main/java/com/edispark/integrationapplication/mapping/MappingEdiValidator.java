package com.edispark.integrationapplication.mapping;

import com.edispark.edifact.OrderResponseD96a;
import com.edispark.integrationapplication.mapping.MappingValidationReport.Finding;
import com.edispark.tradacoms.ack.BqAcknowledgementV14;
import com.edispark.transformations.domain.MappingCompatibility;
import com.tradacoms.parser.EdiParser;
import com.tradacoms.parser.ParseException;
import com.tradacoms.parser.model.Message;
import com.tradacoms.parser.model.Segment;
import com.tradacoms.parser.validation.EdiValidator;
import com.tradacoms.parser.validation.ValidationConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class MappingEdiValidator {
    private static final Set<String> BUSINESS_MESSAGES = Set.of("ORDERS", "ORDRSP", "DESADV", "INVOIC", "REMADV", "RECADV");

    public MappingValidationReport validate(MappingCompatibility format, String content) {
        boolean edifact = "EDIFACT".equals(format.standard());
        if ("OUTBOUND".equals(format.direction()) && "ORDRSP".equals(format.messageType())) {
            return edifact
                    ? MappingValidationReport.of("EDIFACT D96A Order Response core profile",
                        new OrderResponseD96a().validateBytes(content).issues().stream()
                                .map(issue -> Finding.error(issue.code(), issue.path(), issue.message())).toList())
                    : MappingValidationReport.of("B&Q TRADACOMS ACK v1.4 profile",
                        new BqAcknowledgementV14().validateBytes(content).issues().stream()
                                .map(issue -> Finding.error(issue.code(), issue.path(), issue.message())).toList());
        }
        var findings = edifact ? new EdifactMappingChecks().validate(format, content) : tradacoms(format, content);
        return MappingValidationReport.of(format.standard() + " " + format.release() + " envelope and message structure", findings);
    }

    private List<Finding> tradacoms(MappingCompatibility format, String content) {
        List<Finding> findings = new ArrayList<>();
        try {
            var batch = new EdiParser().parseBatch(content);
            var config = ValidationConfig.builder().validateSchema(false).maxIssues(100).build();
            new EdiValidator().validateBatch(batch, config).getAllIssues().forEach(issue -> {
                var location = issue.getLocation();
                String segment = location == null ? null : location.segmentTag();
                String path = location != null && location.lineNumber() > 0 ? "Line " + location.lineNumber()
                        : segment == null ? "Interchange" : segment;
                findings.add(new Finding(issue.getSeverity().name(), issue.getCode(), path, issue.getMessage()));
            });
            checkCount(batch.getRawTrailer(), findings);
            batch.getMessages().forEach(message -> checkCount(message.getTrailer(), findings));
            var business = batch.getMessages().stream().filter(message -> BUSINESS_MESSAGES.contains(message.getMessageType())).toList();
            if (business.isEmpty() || business.stream().anyMatch(message -> !matches(message, format))) {
                findings.add(Finding.error("EDI_MESSAGE_IDENTITY", "MHD", "Expected " + format.messageType()
                        + " release " + format.release() + "."));
            }
        } catch (ParseException invalid) {
            findings.add(Finding.error("EDI_SYNTAX", "Interchange", "The TRADACOMS file could not be parsed. Check its segment syntax and delimiters."));
        }
        return findings;
    }

    private void checkCount(Segment trailer, List<Finding> findings) {
        if (trailer != null && !trailer.getElementValue(0).matches("[0-9]{1,10}")) {
            findings.add(Finding.error("EDI_CONTROL_COUNT", "Line " + trailer.lineNumber(), "The trailer must declare a numeric count."));
        }
    }

    private boolean matches(Message message, MappingCompatibility format) {
        return format.messageType().equals(message.getMessageType()) && message.getHeader() != null
                && message.getHeader().getElement(1).map(element -> format.release().equals(element.getComponent(1))).orElse(false);
    }
}
