package com.edispark.integrationapplication.inbound;

import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class InboundEdiTranslator {
    private static final int MAXIMUM_BYTES = 25 * 1024 * 1024;
    private final InboundEdiMappingInputParser mappingInput;
    private final InboundEdiBatchSplitter splitter;

    public InboundEdiTranslator(ObjectMapper mapper) {
        this(mapper, 1_000);
    }

    @Autowired
    public InboundEdiTranslator(ObjectMapper mapper,
            @Value("${integration.inbound.max-messages-per-file:1000}") int maximumMessages) {
        this.mappingInput = new InboundEdiMappingInputParser(mapper);
        this.splitter = new InboundEdiBatchSplitter(maximumMessages);
    }

    public Translation translate(byte[] input) {
        List<InboundEdiBatchSplitter.InboundMessage> messages = split(input);
        if (messages.size() != 1) throw new IllegalArgumentException("Inbound EDI payload must contain exactly one message");
        return translate(messages.getFirst());
    }

    List<InboundEdiBatchSplitter.InboundMessage> split(byte[] input) {
        if (input == null || input.length == 0 || input.length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("Inbound EDI payload size is invalid");
        }
        return splitter.split(ascii(input));
    }

    Translation translate(InboundEdiBatchSplitter.InboundMessage message) {
        return message.standard() == EdiStandard.EDIFACT ? edifact(message) : tradacoms(message);
    }

    private Translation edifact(InboundEdiBatchSplitter.InboundMessage message) {
        List<String> segments = message.mappingSegments();
        String unh = required(segments, "UNH+");
        String[] identity = element(unh, 1).split(":", -1);
        if (identity.length < 4) throw new IllegalArgumentException("EDIFACT UNH identity is incomplete");
        String messageType = identity[0].toUpperCase();
        String release = (identity[1] + identity[2]).toUpperCase();
        BusinessDocumentType type = documentType(messageType);
        String reference = element(required(segments, "BGM+"), 1);
        String issueDate = segments.stream().filter(item -> item.startsWith("DTM+137:"))
                .map(item -> composite(element(item, 0), 1)).findFirst().map(this::date).orElse("");
        String buyer = party(segments, "BY");
        String supplier = party(segments, "SU");
        return translation(EdiStandard.EDIFACT, release, messageType, type, reference, issueDate,
                mappingInput.edifact(segments, type), message);
    }

    private Translation tradacoms(InboundEdiBatchSplitter.InboundMessage message) {
        List<String> segments = message.mappingSegments();
        String mhd = requiredLast(segments, "MHD=");
        String[] identity = element(mhd, 1).split(":", -1);
        if (identity.length < 2) throw new IllegalArgumentException("TRADACOMS MHD identity is incomplete");
        String wireType = identity[0].toUpperCase();
        String messageType = wireType.startsWith("ORD") ? "ORDERS" : wireType.startsWith("INV") ? "INVOIC" : wireType;
        BusinessDocumentType type = documentType(messageType);
        String reference;
        String issueDate = "";
        if (type == BusinessDocumentType.ORDER && segments.stream().anyMatch(item -> item.startsWith("ORD="))) {
            String order = required(segments, "ORD=");
            reference = element(order, 0);
            if (order.split("\\+", -1).length > 1) issueDate = date(element(order, 1));
        } else {
            reference = element(required(segments, "FIL="), 0);
        }
        String stx = required(segments, "STX=");
        String sender = element(stx, 1);
        String receiver = element(stx, 2);
        return translation(EdiStandard.TRADACOMS, identity[1], messageType, type, reference, issueDate,
                mappingInput.tradacoms(segments, type), message);
    }

    private Translation translation(EdiStandard standard, String release, String messageType,
            BusinessDocumentType type, String reference, String issueDate, byte[] mappingInputJson,
            InboundEdiBatchSplitter.InboundMessage message) {
        if (reference.isBlank()) throw new IllegalArgumentException("EDI business document reference is required");
        return new Translation(standard, release, messageType, type, mappingInputJson, message.rawMessage(),
                message.ordinal(), message.messageCount(), message.sourceReference());
    }
    private String party(List<String> segments, String qualifier) {
        return segments.stream().filter(item -> item.startsWith("NAD+" + qualifier + "+"))
                .map(item -> composite(element(item, 1), 0)).findFirst().orElse("");
    }
    private BusinessDocumentType documentType(String messageType) {
        return switch (messageType) {
            case "ORDERS" -> BusinessDocumentType.ORDER;
            case "INVOIC" -> BusinessDocumentType.INVOICE;
            default -> throw new IllegalArgumentException("Unsupported inbound EDI message type: " + messageType);
        };
    }
    private String required(List<String> segments, String prefix) {
        return segments.stream().filter(item -> item.startsWith(prefix)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Required EDI segment is missing: " + prefix));
    }
    private String requiredLast(List<String> segments, String prefix) {
        return segments.reversed().stream().filter(item -> item.startsWith(prefix)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Required EDI segment is missing: " + prefix));
    }
    private String element(String segment, int index) {
        String body = segment.substring(4);
        String[] elements = body.split("\\+", -1);
        if (index >= elements.length) throw new IllegalArgumentException("EDI segment element is missing");
        return elements[index];
    }
    private String composite(String value, int index) {
        String[] components = value.split(":", -1);
        return index < components.length ? components[index] : "";
    }
    private String date(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 8) return LocalDate.parse(digits, DateTimeFormatter.BASIC_ISO_DATE).toString();
        if (digits.length() == 6) return LocalDate.parse(digits, DateTimeFormatter.ofPattern("yyMMdd")).toString();
        return "";
    }
    private String ascii(byte[] input) {
        try {
            return StandardCharsets.US_ASCII.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(input)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Inbound EDI payload must be ASCII", exception);
        }
    }

    public record Translation(EdiStandard standard, String release, String messageType,
            BusinessDocumentType documentType, byte[] mappingInputJson, byte[] rawMessage,
            int messageOrdinal, int messageCount, String sourceMessageReference) { }
}
