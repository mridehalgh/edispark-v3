package com.tradacoms.parser.validation;

import com.tradacoms.parser.TradacomsSyntax;
import com.tradacoms.parser.model.Batch;
import com.tradacoms.parser.model.Element;
import com.tradacoms.parser.model.Group;
import com.tradacoms.parser.model.Message;
import com.tradacoms.parser.model.Segment;
import com.tradacoms.parser.model.SegmentOrGroup;
import com.tradacoms.parser.schema.ElementSchema;
import com.tradacoms.parser.schema.GroupSchema;
import com.tradacoms.parser.schema.MessageSchema;
import com.tradacoms.parser.schema.SegmentOrGroupSchema;
import com.tradacoms.parser.schema.SegmentSchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validator for TRADACOMS EDI batches and messages.
 * Validates envelope integrity, schema conformance, and group constraints.
 */
public final class EdiValidator {

    /**
     * Validates a batch and returns a validation report.
     */
    public BatchValidationReport validateBatch(Batch batch, ValidationConfig config) {
        if (batch == null) {
            return BatchValidationReport.builder()
                    .addBatchIssue(ValidationIssue.error(
                            ErrorCode.PARSE_002,
                            "Batch is null",
                            IssueLocation.batchLevel(config.getFilename())
                    ))
                    .build();
        }

        ValidationContext ctx = new ValidationContext(config);
        BatchValidationReport.Builder reportBuilder = BatchValidationReport.builder();

        // Validate envelope if configured
        if (config.isValidateEnvelope()) {
            List<ValidationIssue> envelopeIssues = validateEnvelope(batch, ctx);
            reportBuilder.addBatchIssues(envelopeIssues);
            
            if (ctx.shouldStop()) {
                return reportBuilder.build();
            }
        }

        // Validate each message
        for (int i = 0; i < batch.getMessages().size(); i++) {
            Message message = batch.getMessages().get(i);
            MessageValidationReport msgReport = validateMessage(message, config, ctx);
            reportBuilder.addMessageReport(i, msgReport);
            
            if (ctx.shouldStop()) {
                break;
            }
        }

        return reportBuilder.build();
    }

    /**
     * Validates a single message and returns a validation report.
     */
    public MessageValidationReport validateMessage(Message message, ValidationConfig config) {
        ValidationContext ctx = new ValidationContext(config);
        return validateMessage(message, config, ctx);
    }

    private MessageValidationReport validateMessage(Message message, ValidationConfig config, ValidationContext ctx) {
        List<ValidationIssue> issues = new ArrayList<>();
        int messageIndex = message.getMessageIndexInBatch();
        String filename = config.getFilename();

        // Validate MHD segment exists
        if (message.getHeader() == null) {
            issues.add(ValidationIssue.error(
                    ErrorCode.ENV_005,
                    "Missing MHD (message header) segment",
                    IssueLocation.messageLevel(filename, messageIndex)
            ));
            ctx.recordIssue();
            
            if (ctx.shouldStop()) {
                return MessageValidationReport.fromIssues(messageIndex, message.getMessageControlRef(), issues);
            }
        }

        // Validate MTR segment exists
        if (message.getTrailer() == null) {
            issues.add(ValidationIssue.error(
                    ErrorCode.ENV_006,
                    "Missing MTR (message trailer) segment",
                    IssueLocation.messageLevel(filename, messageIndex)
            ));
            ctx.recordIssue();
            
            if (ctx.shouldStop()) {
                return MessageValidationReport.fromIssues(messageIndex, message.getMessageControlRef(), issues);
            }
        }

        // Validate MTR segment count if present
        if (message.getTrailer() != null) {
            List<ValidationIssue> mtrIssues = validateMtrSegmentCount(message, filename, messageIndex);
            issues.addAll(mtrIssues);
            for (int i = 0; i < mtrIssues.size(); i++) {
                ctx.recordIssue();
                if (ctx.shouldStop()) {
                    return MessageValidationReport.fromIssues(messageIndex, message.getMessageControlRef(), issues);
                }
            }
        }

        // Validate against schema if configured and available
        if (config.isValidateSchema()) {
            Optional<MessageSchema> schemaOpt = config.getSchema(message.getMessageType());
            if (schemaOpt.isPresent()) {
                List<ValidationIssue> schemaIssues = validateAgainstSchema(message, schemaOpt.get(), filename, ctx);
                issues.addAll(schemaIssues);
            }
        }

        return MessageValidationReport.fromIssues(messageIndex, message.getMessageControlRef(), issues);
    }

    private List<ValidationIssue> validateEnvelope(Batch batch, ValidationContext ctx) {
        List<ValidationIssue> issues = new ArrayList<>();
        String filename = ctx.getConfig().getFilename();

        // Validate STX segment exists
        if (batch.getRawHeader() == null) {
            issues.add(ValidationIssue.error(
                    ErrorCode.ENV_001,
                    "Missing STX (start of transmission) segment",
                    IssueLocation.batchLevel(filename),
                    null
            ));
            ctx.recordIssue();
            
            if (ctx.shouldStop()) {
                return issues;
            }
        }

        // Validate END segment exists
        if (batch.getRawTrailer() == null) {
            issues.add(ValidationIssue.error(
                    ErrorCode.ENV_002,
                    "Missing END (end of transmission) segment",
                    IssueLocation.batchLevel(filename),
                    null
            ));
            ctx.recordIssue();
            
            if (ctx.shouldStop()) {
                return issues;
            }
        }

        // Validate message count in END segment
        if (batch.getRawTrailer() != null) {
            Segment endSegment = batch.getRawTrailer();
            // END segment format: END+<message_count>'
            // Element 0 is the message count
            if (!endSegment.elements().isEmpty()) {
                String countStr = endSegment.getElementValue(0);
                try {
                    int declaredCount = Integer.parseInt(countStr);
                    int actualCount = batch.getMessages().size();
                    
                    if (declaredCount != actualCount) {
                        issues.add(ValidationIssue.error(
                                ErrorCode.ENV_003,
                                String.format("Message count mismatch: END segment declares %d messages but batch contains %d",
                                        declaredCount, actualCount),
                                IssueLocation.segmentLevel(filename, -1, TradacomsSyntax.END, endSegment.lineNumber()),
                                endSegment.rawContent()
                        ));
                        ctx.recordIssue();
                    }
                } catch (NumberFormatException e) {
                    // Non-numeric count - could be a warning
                    issues.add(ValidationIssue.warning(
                            ErrorCode.VALID_006,
                            "END segment message count is not a valid number: " + countStr,
                            IssueLocation.segmentLevel(filename, -1, TradacomsSyntax.END, endSegment.lineNumber()),
                            endSegment.rawContent()
                    ));
                    ctx.recordIssue();
                }
            }
        }

        return issues;
    }

    private List<ValidationIssue> validateMtrSegmentCount(Message message, String filename, int messageIndex) {
        List<ValidationIssue> issues = new ArrayList<>();
        Segment mtr = message.getTrailer();
        
        // MTR segment format: MTR+<segment_count>'
        if (!mtr.elements().isEmpty()) {
            String countStr = mtr.getElementValue(0);
            try {
                int declaredCount = Integer.parseInt(countStr);
                // Count all segments including MHD and MTR
                int actualCount = countAllSegments(message);
                
                if (declaredCount != actualCount) {
                    issues.add(ValidationIssue.error(
                            ErrorCode.ENV_007,
                            String.format("Segment count mismatch in MTR: declares %d segments but message contains %d",
                                    declaredCount, actualCount),
                            IssueLocation.segmentLevel(filename, messageIndex, TradacomsSyntax.MTR, mtr.lineNumber()),
                            mtr.rawContent()
                    ));
                }
            } catch (NumberFormatException e) {
                issues.add(ValidationIssue.warning(
                        ErrorCode.VALID_006,
                        "MTR segment count is not a valid number: " + countStr,
                        IssueLocation.segmentLevel(filename, messageIndex, TradacomsSyntax.MTR, mtr.lineNumber()),
                        mtr.rawContent()
                ));
            }
        }
        
        return issues;
    }

    private int countAllSegments(Message message) {
        int count = 0;
        
        // Count MHD
        if (message.getHeader() != null) {
            count++;
        }
        
        // Count content segments
        count += countSegmentsInContent(message.getContent());
        
        // Count MTR
        if (message.getTrailer() != null) {
            count++;
        }
        
        return count;
    }

    private int countSegmentsInContent(List<SegmentOrGroup> content) {
        int count = 0;
        for (SegmentOrGroup item : content) {
            if (item instanceof Segment) {
                count++;
            } else if (item instanceof Group group) {
                count += countSegmentsInContent(group.getContent());
            }
        }
        return count;
    }

    private List<ValidationIssue> validateAgainstSchema(
            Message message,
            MessageSchema schema,
            String filename,
            ValidationContext ctx
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        int messageIndex = message.getMessageIndexInBatch();

        // Validate segments against schema
        for (SegmentOrGroup item : message.getContent()) {
            if (ctx.shouldStop()) {
                break;
            }
            
            if (item instanceof Segment segment) {
                List<ValidationIssue> segmentIssues = validateSegment(segment, schema, filename, messageIndex, ctx);
                issues.addAll(segmentIssues);
            } else if (item instanceof Group group) {
                List<ValidationIssue> groupIssues = validateGroup(group, schema, filename, messageIndex, ctx);
                issues.addAll(groupIssues);
            }
        }

        // Validate group occurrence constraints
        List<ValidationIssue> groupIssues = validateGroupOccurrences(message, schema, filename, messageIndex, ctx);
        issues.addAll(groupIssues);

        return issues;
    }

    private List<ValidationIssue> validateSegment(
            Segment segment,
            MessageSchema schema,
            String filename,
            int messageIndex,
            ValidationContext ctx
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        Optional<SegmentSchema> segmentSchemaOpt = schema.getSegmentSchema(segment.tag());
        if (segmentSchemaOpt.isEmpty()) {
            // Unknown segment - could be a warning depending on strictness
            issues.add(ValidationIssue.warning(
                    ErrorCode.VALID_009,
                    "Unknown segment tag: " + segment.tag(),
                    IssueLocation.segmentLevel(filename, messageIndex, segment.tag(), segment.lineNumber()),
                    segment.rawContent()
            ));
            ctx.recordIssue();
            return issues;
        }

        SegmentSchema segmentSchema = segmentSchemaOpt.get();
        
        // Validate elements
        Map<String, ElementSchema> elementSchemas = segmentSchema.getValues();
        int elementIndex = 0;
        
        for (Map.Entry<String, ElementSchema> entry : elementSchemas.entrySet()) {
            if (ctx.shouldStop()) {
                break;
            }
            
            ElementSchema elementSchema = entry.getValue();
            Element element = elementIndex < segment.elements().size() 
                    ? segment.elements().get(elementIndex) 
                    : null;
            
            // Check mandatory elements
            if (elementSchema.isMandatory() && (element == null || element.getValue().isEmpty())) {
                issues.add(ValidationIssue.error(
                        ErrorCode.VALID_005,
                        String.format("Missing required element %s in segment %s", 
                                elementSchema.getId(), segment.tag()),
                        IssueLocation.elementLevel(filename, messageIndex, segment.tag(), elementIndex, segment.lineNumber()),
                        segment.rawContent()
                ));
                ctx.recordIssue();
            }
            
            // Validate element value if present
            if (element != null && !element.getValue().isEmpty()) {
                List<ValidationIssue> elementIssues = validateElement(
                        element, elementSchema, segment, filename, messageIndex, elementIndex, ctx);
                issues.addAll(elementIssues);
            }
            
            elementIndex++;
        }

        return issues;
    }

    private List<ValidationIssue> validateElement(
            Element element,
            ElementSchema schema,
            Segment segment,
            String filename,
            int messageIndex,
            int elementIndex,
            ValidationContext ctx
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        String value = element.getValue();

        // Validate length constraints
        if (schema.getMinLength() != null && value.length() < schema.getMinLength()) {
            issues.add(ValidationIssue.error(
                    ErrorCode.VALID_010,
                    String.format("Element %s value '%s' is shorter than minimum length %d",
                            schema.getId(), value, schema.getMinLength()),
                    IssueLocation.elementLevel(filename, messageIndex, segment.tag(), elementIndex, segment.lineNumber()),
                    segment.rawContent()
            ));
            ctx.recordIssue();
        }

        if (schema.getMaxLength() != null && value.length() > schema.getMaxLength()) {
            issues.add(ValidationIssue.error(
                    ErrorCode.VALID_004,
                    String.format("Element %s value '%s' exceeds maximum length %d",
                            schema.getId(), value, schema.getMaxLength()),
                    IssueLocation.elementLevel(filename, messageIndex, segment.tag(), elementIndex, segment.lineNumber()),
                    segment.rawContent()
            ));
            ctx.recordIssue();
        }

        // Validate data type
        if (schema.getType() != null) {
            List<ValidationIssue> typeIssues = validateDataType(
                    value, schema, segment, filename, messageIndex, elementIndex, ctx);
            issues.addAll(typeIssues);
        }

        return issues;
    }

    private List<ValidationIssue> validateDataType(
            String value,
            ElementSchema schema,
            Segment segment,
            String filename,
            int messageIndex,
            int elementIndex,
            ValidationContext ctx
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        String type = schema.getType();

        if ("int".equals(type)) {
            try {
                Long.parseLong(value);
            } catch (NumberFormatException e) {
                issues.add(ValidationIssue.error(
                        ErrorCode.VALID_006,
                        String.format("Element %s value '%s' is not a valid integer",
                                schema.getId(), value),
                        IssueLocation.elementLevel(filename, messageIndex, segment.tag(), elementIndex, segment.lineNumber()),
                        segment.rawContent()
                ));
                ctx.recordIssue();
            }
        } else if (type != null && type.startsWith("num.")) {
            // Numeric with decimal places
            try {
                Double.parseDouble(value);
            } catch (NumberFormatException e) {
                issues.add(ValidationIssue.error(
                        ErrorCode.VALID_006,
                        String.format("Element %s value '%s' is not a valid number",
                                schema.getId(), value),
                        IssueLocation.elementLevel(filename, messageIndex, segment.tag(), elementIndex, segment.lineNumber()),
                        segment.rawContent()
                ));
                ctx.recordIssue();
            }
        }

        return issues;
    }

    private List<ValidationIssue> validateGroup(
            Group group,
            MessageSchema schema,
            String filename,
            int messageIndex,
            ValidationContext ctx
    ) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Validate segments within the group
        for (SegmentOrGroup item : group.getContent()) {
            if (ctx.shouldStop()) {
                break;
            }
            
            if (item instanceof Segment segment) {
                List<ValidationIssue> segmentIssues = validateSegment(segment, schema, filename, messageIndex, ctx);
                issues.addAll(segmentIssues);
            } else if (item instanceof Group nestedGroup) {
                List<ValidationIssue> nestedIssues = validateGroup(nestedGroup, schema, filename, messageIndex, ctx);
                issues.addAll(nestedIssues);
            }
        }

        return issues;
    }

    private List<ValidationIssue> validateGroupOccurrences(
            Message message,
            MessageSchema schema,
            String filename,
            int messageIndex,
            ValidationContext ctx
    ) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Count group occurrences
        Map<String, Integer> groupCounts = new HashMap<>();
        countGroups(message.getContent(), groupCounts);

        // Check against schema constraints
        for (Map.Entry<String, SegmentOrGroupSchema> entry : schema.getSegments().entrySet()) {
            if (ctx.shouldStop()) {
                break;
            }
            
            if (entry.getValue() instanceof GroupSchema groupSchema) {
                String groupId = groupSchema.getGroupId();
                int count = groupCounts.getOrDefault(groupId, 0);
                int minOccurs = groupSchema.getMinOccurs();
                int maxOccurs = groupSchema.getMaxOccurs();

                if (count < minOccurs) {
                    issues.add(ValidationIssue.error(
                            ErrorCode.VALID_007,
                            String.format("Group %s occurs %d times but minimum is %d",
                                    groupId, count, minOccurs),
                            IssueLocation.messageLevel(filename, messageIndex)
                    ));
                    ctx.recordIssue();
                }

                if (maxOccurs > 0 && count > maxOccurs) {
                    issues.add(ValidationIssue.error(
                            ErrorCode.VALID_008,
                            String.format("Group %s occurs %d times but maximum is %d",
                                    groupId, count, maxOccurs),
                            IssueLocation.messageLevel(filename, messageIndex)
                    ));
                    ctx.recordIssue();
                }
            }
        }

        return issues;
    }

    private void countGroups(List<SegmentOrGroup> content, Map<String, Integer> counts) {
        for (SegmentOrGroup item : content) {
            if (item instanceof Group group) {
                counts.merge(group.getGroupId(), 1, Integer::sum);
                // Also count nested groups
                countGroups(group.getContent(), counts);
            }
        }
    }

    /**
     * Internal context for tracking validation state.
     */
    private static class ValidationContext {
        private final ValidationConfig config;
        private int issueCount = 0;

        ValidationContext(ValidationConfig config) {
            this.config = config;
        }

        ValidationConfig getConfig() {
            return config;
        }

        void recordIssue() {
            issueCount++;
        }

        boolean shouldStop() {
            if (config.isFailFast() && issueCount > 0) {
                return true;
            }
            return issueCount >= config.getMaxIssues();
        }
    }
}
