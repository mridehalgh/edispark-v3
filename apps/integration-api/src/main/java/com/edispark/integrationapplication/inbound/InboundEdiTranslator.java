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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class InboundEdiTranslator {
    private static final int MAXIMUM_BYTES = 25 * 1024 * 1024;
    private final ObjectMapper mapper;

    public InboundEdiTranslator(ObjectMapper mapper) { this.mapper = mapper; }

    public Translation translate(byte[] input) {
        if (input == null || input.length == 0 || input.length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("Inbound EDI payload size is invalid");
        }
        String edi = ascii(input);
        return edi.contains("UNH+") ? edifact(edi) : tradacoms(edi);
    }

    private Translation edifact(String edi) {
        List<String> segments = segments(edi);
        requireSingleMessage(segments, "UNH+");
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
        return translation(EdiStandard.EDIFACT, release, messageType, type, reference, issueDate, buyer, supplier);
    }

    private Translation tradacoms(String edi) {
        List<String> segments = segments(edi);
        requireSingleMessage(segments, "MHD=");
        String mhd = required(segments, "MHD=");
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
                receiver, sender);
    }

    private Translation translation(EdiStandard standard, String release, String messageType,
            BusinessDocumentType type, String reference, String issueDate, String buyer, String supplier) {
        if (reference.isBlank()) throw new IllegalArgumentException("EDI business document reference is required");
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("ID", value(reference));
        if (!issueDate.isBlank()) document.put("IssueDate", value(issueDate));
        if (type == BusinessDocumentType.ORDER) {
            document.put("BuyerCustomerParty", party("Party", buyer));
            document.put("SellerSupplierParty", party("Party", supplier));
            root.put("Order", List.of(document));
        } else {
            document.put("AccountingSupplierParty", party("Party", supplier));
            document.put("AccountingCustomerParty", party("Party", buyer));
            root.put("Invoice", List.of(document));
        }
        try {
            return new Translation(standard, release, messageType, type, mapper.writeValueAsBytes(root));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Canonical document could not be serialized", exception);
        }
    }

    private List<Map<String, Object>> party(String key, String endpoint) {
        return endpoint.isBlank() ? List.of() : List.of(Map.of(key, List.of(Map.of("EndpointID", value(endpoint)))));
    }

    private List<Map<String, String>> value(String value) { return List.of(Map.of("_", value)); }
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
    private List<String> segments(String edi) {
        return Arrays.stream(edi.replace("\r", "").replace("\n", "").split("'"))
                .map(String::strip).filter(value -> !value.isBlank()).toList();
    }
    private String required(List<String> segments, String prefix) {
        return segments.stream().filter(item -> item.startsWith(prefix)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Required EDI segment is missing: " + prefix));
    }
    private void requireSingleMessage(List<String> segments, String prefix) {
        long messages = segments.stream().filter(item -> item.startsWith(prefix)).count();
        if (messages != 1) {
            throw new IllegalArgumentException("Inbound EDI payload must contain exactly one message");
        }
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
                              BusinessDocumentType documentType, byte[] canonicalJson) { }
}
