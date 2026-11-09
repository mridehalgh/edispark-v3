package com.edispark.integrationapplication.acl;

import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class EdiPayloadWriter {
    private static final DateTimeFormatter EDIFACT_DATE = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter EDIFACT_TIME = DateTimeFormatter.ofPattern("HHmm").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TRADACOMS_DATE = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TRADACOMS_TIME = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC);
    private final ObjectMapper mapper;
    private final Clock clock;

    public EdiPayloadWriter(ObjectMapper mapper, Clock clock) { this.mapper = mapper; this.clock = clock; }

    public byte[] write(ExchangeCapability capability, byte[] mappedJson) {
        try {
            JsonNode document = mapper.readTree(mappedJson);
            validateModel(capability, document);
            String payload = capability.standard() == EdiStandard.EDIFACT
                    ? edifact(capability, document) : tradacoms(capability, document);
            return payload.getBytes(StandardCharsets.US_ASCII);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Mapped EDI model is invalid", exception);
        }
    }

    private String edifact(ExchangeCapability capability, JsonNode document) {
        String control = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        String reference = text(document, "/document/id", control);
        String issueDate = text(document, "/document/issueDate", "").replace("-", "");
        String sender = text(document, "/parties/sender", "EDISPARK");
        String receiver = text(document, "/parties/receiver", "PARTNER");
        String bgmCode = bgmCode(capability.documentType());
        String directoryRelease = capability.release().startsWith("D")
                ? capability.release().substring(1) : capability.release();
        List<String> message = new ArrayList<>();
        message.add("UNH+1+" + edi(capability.messageType()) + ":D:" + edi(directoryRelease) + ":UN");
        message.add("BGM+" + bgmCode + '+' + edi(reference) + "+9");
        if (!issueDate.isBlank()) message.add("DTM+137:" + edi(issueDate) + ":102");
        addEdifactParties(message, sender, receiver);
        addEdifactDocumentRequirements(message, document, capability.documentType(), issueDate);
        addEdifactReference(message, document, capability.documentType());
        addEdifactCurrency(message, document);
        addEdifactRemittanceAmount(message, document, capability.documentType());
        addEdifactLines(message, document, capability.documentType());
        addEdifactTotals(message, document);
        message.add("UNT+" + (message.size() + 1) + "+1");
        String interchange = "UNB+UNOC:3+" + edi(sender) + '+' + edi(receiver) + '+'
                + EDIFACT_DATE.format(clock.instant()) + ':' + EDIFACT_TIME.format(clock.instant()) + '+' + control + "'";
        return interchange + String.join("'", message) + "'UNZ+1+" + control + "'";
    }

    private String tradacoms(ExchangeCapability capability, JsonNode document) {
        String control = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        String reference = text(document, "/document/id", control);
        String sender = text(document, "/parties/sender", "EDISPARK");
        String receiver = text(document, "/parties/receiver", "PARTNER");
        String message = tradacomsFileMessage(capability.documentType());
        StringBuilder result = new StringBuilder("STX=ANA:1+" + edi(sender) + '+' + edi(receiver) + '+'
                + TRADACOMS_DATE.format(clock.instant()) + ':'
                + TRADACOMS_TIME.format(clock.instant()) + '+' + control + "++" + message + "'"
                + "MHD=1+" + message + ':' + edi(capability.release()) + "'"
                + "TYP=" + tradacomsTypeCode(capability.documentType()) + "'"
                + "FIL=" + edi(reference) + "+1'"
                + "MTR=4'");
        addTradacomsMessage(result, capability, document, reference);
        result.append("END=2'");
        return result.toString();
    }

    private void addEdifactCurrency(List<String> message, JsonNode document) {
        String currency = text(document, "/document/currency", "");
        if (!currency.isBlank()) message.add("CUX+2:" + edi(currency) + ":9");
    }

    private void addEdifactRemittanceAmount(List<String> message, JsonNode document, BusinessDocumentType type) {
        if (type == BusinessDocumentType.REMITTANCE_ADVICE) {
            addSegment(message, "MOA+9:", document, "/document/totalPaymentAmount");
        }
    }

    private void addEdifactReference(List<String> message, JsonNode document, BusinessDocumentType type) {
        String reference = text(document, "/document/orderReference",
                text(document, "/document/despatchReference", text(document, "/document/invoiceReference", "")));
        if (!reference.isBlank() && type != BusinessDocumentType.ORDER) message.add("RFF+ON:" + edi(reference));
    }

    private void addEdifactParties(List<String> message, String sender, String receiver) {
        message.add("NAD+SU+" + edi(sender) + "::9");
        message.add("NAD+BY+" + edi(receiver) + "::9");
    }

    private void addEdifactDocumentRequirements(List<String> message, JsonNode document,
            BusinessDocumentType type, String issueDate) {
        if (type == BusinessDocumentType.ORDER) {
            String requestedDate = text(document, "/delivery/requestedDate", "");
            if (!requestedDate.isBlank()) message.add("DTM+2:" + edi(requestedDate.replace("-", "")) + ":102");
            String location = text(document, "/delivery/locationId", "");
            if (!location.isBlank()) message.add("NAD+DP+" + edi(location) + "::92");
            String terms = text(document, "/delivery/terms", "");
            if (!terms.isBlank()) message.add("TOD+5++" + edi(terms));
        } else if (type == BusinessDocumentType.INVOICE) {
            String dueDate = text(document, "/document/dueDate", "");
            if (!dueDate.isBlank()) message.add("DTM+13:" + edi(dueDate.replace("-", "")) + ":102");
        }
    }

    private void addEdifactLines(List<String> message, JsonNode document, BusinessDocumentType type) {
        JsonNode lines = document.path("lines");
        if (!lines.isArray()) return;
        for (int index = 0; index < lines.size(); index++) addEdifactLine(message, lines.get(index), index + 1, type);
    }

    private void addEdifactLine(List<String> message, JsonNode line, int position, BusinessDocumentType type) {
        message.add("LIN+" + edi(text(line, "/id", Integer.toString(position))) + "++"
                + edi(text(line, "/gtin", "")) + ":EN");
        addEdifactItemIdentifiers(message, line);
        String name = text(line, "/name", text(line, "/description", ""));
        if (!name.isBlank()) message.add("IMD+F++:::" + edi(name));
        String description = text(line, "/description", "");
        if (!description.isBlank() && !description.equals(name)) message.add("IMD+F++:::" + edi(description));
        message.add("QTY+" + quantityQualifier(type) + ':' + edi(text(line, "/quantity", "0")) + ':'
                + edi(text(line, "/unitCode", "")));
        addSegment(message, "PRI+AAA:", line, "/price");
        addSegment(message, "MOA+203:", line, "/lineExtension");
        if (type == BusinessDocumentType.INVOICE) addEdifactTax(message, line);
    }

    private void addEdifactItemIdentifiers(List<String> message, JsonNode line) {
        String buyer = text(line, "/buyerItemId", "");
        String seller = text(line, "/sellerItemId", "");
        if (!buyer.isBlank() || !seller.isBlank()) {
            message.add("PIA+1+" + edi(buyer) + ":BP+" + edi(seller) + ":SA");
        }
    }

    private void addEdifactTax(List<String> message, JsonNode line) {
        String category = text(line, "/taxCategory", "");
        String percent = text(line, "/taxPercent", "");
        if (!category.isBlank() || !percent.isBlank()) {
            message.add("TAX+7+VAT+++:" + "::" + edi(percent) + '+' + edi(category));
        }
    }

    private void addEdifactTotals(List<String> message, JsonNode document) {
        if (!document.path("totals").isObject()) return;
        message.add("UNS+S");
        addSegment(message, "MOA+79:", document, "/totals/lineExtension");
        addSegment(message, "MOA+125:", document, "/totals/taxExclusive");
        addSegment(message, "MOA+176:", document, "/tax/amount");
        addSegment(message, "MOA+86:", document, "/totals/payable");
    }

    private void addSegment(List<String> message, String prefix, JsonNode value, String pointer) {
        String content = text(value, pointer, "");
        if (!content.isBlank()) message.add(prefix + edi(content));
    }

    private void addTradacomsMessage(StringBuilder result, ExchangeCapability capability, JsonNode document,
            String reference) {
        boolean invoice = capability.documentType() == BusinessDocumentType.INVOICE;
        result.append("MHD=2+").append(edi(capability.messageType())).append(':')
                .append(edi(capability.release())).append("'");
        if (invoice) result.append("IRF=").append(edi(reference)).append('+')
                .append(edi(tradacomsDate(document))).append("'");
        else result.append("DOC=").append(edi(reference)).append('+')
                .append(edi(tradacomsDate(document))).append("'");
        if (capability.documentType() == BusinessDocumentType.ORDER) result.append("ORD=").append(edi(reference)).append("++")
                .append(edi(text(document, "/document/currency", ""))).append("'");
        if (capability.documentType() == BusinessDocumentType.ORDER_RESPONSE) {
            result.append("ORD=").append(edi(text(document, "/document/orderReference", reference))).append("'");
        }
        if (capability.documentType() == BusinessDocumentType.ORDER) {
            String location = text(document, "/delivery/locationId", "");
            if (!location.isBlank()) result.append("CLO=").append(edi(location)).append("'");
        }
        if (invoice) result.append("ODD=").append(edi(text(document, "/document/orderReference", reference)))
                .append("'");
        addTradacomsLines(result, document, invoice);
        addTradacomsTotals(result, document, invoice);
        int start = result.indexOf("MHD=2+");
        long segments = segmentCount(result.substring(start));
        result.append("MTR=").append(segments + 1).append("'");
    }

    private void addTradacomsLines(StringBuilder result, JsonNode document, boolean invoice) {
        JsonNode lines = document.path("lines");
        if (!lines.isArray()) return;
        for (int index = 0; index < lines.size(); index++) {
            JsonNode line = lines.get(index);
            result.append(invoice ? "ILD=" : "OLD=").append(edi(text(line, "/id", Integer.toString(index + 1))))
                    .append('+').append(edi(text(line, "/gtin", ""))).append('+')
                    .append(edi(text(line, "/buyerItemId", ""))).append(':')
                    .append(edi(text(line, "/sellerItemId", ""))).append('+')
                    .append(edi(text(line, "/name", text(line, "/description", "")))).append('+')
                    .append(edi(text(line, "/quantity", "0"))).append(':')
                    .append(edi(text(line, "/unitCode", ""))).append('+')
                    .append(edi(text(line, "/price", "")));
            if (invoice) result.append('+').append(edi(text(line, "/lineExtension", "")));
            result.append("'");
        }
    }

    private void addTradacomsTotals(StringBuilder result, JsonNode document, boolean invoice) {
        result.append(invoice ? "STL=" : "OTR=").append(document.path("lines").size()).append('+')
                .append(edi(text(document, "/totals/lineExtension", ""))).append('+');
        if (invoice) result.append(edi(text(document, "/tax/amount", ""))).append('+');
        else result.append(edi(text(document, "/totals/allowance", "0"))).append('+');
        result.append(edi(text(document, "/totals/payable",
                text(document, "/document/totalPaymentAmount", "")))).append("'");
        if (invoice) result.append("TLR=").append(edi(text(document, "/totals/lineExtension", ""))).append('+')
                .append(edi(text(document, "/tax/amount", ""))).append('+')
                .append(edi(text(document, "/totals/payable", ""))).append("'");
    }

    private String tradacomsDate(JsonNode document) {
        String date = text(document, "/document/issueDate", "").replace("-", "");
        return date.length() == 8 ? date.substring(2) : date;
    }

    private String bgmCode(BusinessDocumentType type) {
        return switch (type) {
            case ORDER -> "220";
            case ORDER_RESPONSE -> "231";
            case DESPATCH_ADVICE -> "351";
            case INVOICE -> "380";
            case REMITTANCE_ADVICE -> "481";
            case RECEIPT_ADVICE -> "352";
        };
    }

    private String quantityQualifier(BusinessDocumentType type) {
        return switch (type) {
            case INVOICE -> "47";
            case DESPATCH_ADVICE -> "12";
            case RECEIPT_ADVICE -> "194";
            default -> "21";
        };
    }

    private String tradacomsFileMessage(BusinessDocumentType type) {
        return switch (type) {
            case ORDER -> "ORDHDR";
            case ORDER_RESPONSE -> "ACKHDR";
            case DESPATCH_ADVICE, RECEIPT_ADVICE -> "DELHDR";
            case INVOICE -> "INVFIL";
            case REMITTANCE_ADVICE -> "PAYHDR";
        };
    }

    private String tradacomsTypeCode(BusinessDocumentType type) {
        return switch (type) {
            case ORDER -> "0430";
            case ORDER_RESPONSE -> "0440";
            case DESPATCH_ADVICE -> "0400";
            case INVOICE -> "0700";
            case REMITTANCE_ADVICE -> "0900";
            case RECEIPT_ADVICE -> "0410";
        };
    }

    private String text(JsonNode document, String pointer, String fallback) {
        JsonNode value = document.at(pointer);
        return value.isValueNode() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    private String edi(String value) {
        if (value == null) return "";
        if (!value.matches("[\\x20-\\x7E]*") || value.length() > 70) {
            throw new IllegalArgumentException("A mapped EDI value cannot be represented without data loss. Review its character set or length.");
        }
        String ascii = value;
        return ascii.replace("?", "??").replace("+", "?+").replace(":", "?:").replace("'", "?'");
    }

    private void validateModel(ExchangeCapability capability, JsonNode model) {
        String expectedMessage = switch (capability.documentType()) {
            case ORDER -> "ORDERS"; case ORDER_RESPONSE -> "ORDRSP"; case DESPATCH_ADVICE -> "DESADV";
            case INVOICE -> "INVOIC"; case REMITTANCE_ADVICE -> "REMADV"; case RECEIPT_ADVICE -> "RECADV";
        };
        if (!capability.messageType().equals(expectedMessage)) throw new IllegalArgumentException("Target message type does not match the business document.");
        if (!(capability.standard() == EdiStandard.EDIFACT && capability.release().equals("D96A"))
                && !(capability.standard() == EdiStandard.TRADACOMS && capability.release().equals("9"))) {
            throw new IllegalArgumentException("Target validation is unavailable for this EDI release.");
        }
        for (String field : List.of("/document/id", "/document/issueDate", "/parties/sender", "/parties/receiver")) {
            if (text(model, field, "").isBlank()) throw new IllegalArgumentException("Required target field is missing: " + field);
        }
        try { java.time.LocalDate.parse(text(model, "/document/issueDate", "")); }
        catch (java.time.DateTimeException exception) { throw new IllegalArgumentException("The target issue date is invalid."); }
        if (!model.path("lines").isArray() || model.path("lines").isEmpty()) throw new IllegalArgumentException("The target document must contain its document lines.");
        for (JsonNode line : model.path("lines")) {
            if (capability.standard() == EdiStandard.TRADACOMS && line.has("name") && line.has("description")
                    && !text(line, "/name", "").equals(text(line, "/description", ""))) {
                throw new IllegalArgumentException("This target cannot carry separate item names and descriptions. Review an explicit mapping decision.");
            }
            if (text(line, "/id", "").isBlank()) throw new IllegalArgumentException("Every target line needs an identifier.");
            if (capability.documentType() != BusinessDocumentType.REMITTANCE_ADVICE
                    && (text(line, "/quantity", "").isBlank() || text(line, "/unitCode", "").isBlank())) {
                throw new IllegalArgumentException("Every target line needs a quantity and unit.");
            }
        }
        verifySupportedFields(model, "", capability);
    }

    private long segmentCount(String value) {
        long count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '?') index++;
            else if (value.charAt(index) == '\'') count++;
        }
        return count;
    }

    /** A successful JSON mapping must not hide fields that this target encoder never emits. */
    private void verifySupportedFields(JsonNode node, String path, ExchangeCapability capability) {
        if (node.isObject()) { node.fields().forEachRemaining(entry -> verifySupportedFields(entry.getValue(), path + "/" + entry.getKey(), capability)); return; }
        if (node.isArray()) { for (JsonNode child : node) verifySupportedFields(child, path + "/*", capability); return; }
        if (node.isNull() || node.asText().isBlank()) return;
        var common = java.util.Set.of("/document/id", "/document/issueDate", "/document/currency", "/document/orderReference",
                "/parties/sender", "/parties/receiver", "/lines/*/id", "/lines/*/quantity", "/lines/*/unitCode",
                "/lines/*/gtin", "/lines/*/buyerItemId", "/lines/*/sellerItemId", "/lines/*/name", "/lines/*/description",
                "/lines/*/price", "/lines/*/lineExtension", "/totals/lineExtension", "/totals/payable", "/tax/amount");
        var extra = capability.standard() == EdiStandard.EDIFACT
                ? java.util.Set.of("/document/dueDate", "/delivery/requestedDate", "/delivery/locationId", "/delivery/terms",
                        "/document/totalPaymentAmount", "/totals/taxExclusive", "/lines/*/taxCategory", "/lines/*/taxPercent")
                : java.util.Set.of("/document/totalPaymentAmount", "/delivery/locationId", "/totals/allowance");
        boolean tradacoms = capability.standard() == EdiStandard.TRADACOMS;
        boolean invoice = capability.documentType() == BusinessDocumentType.INVOICE;
        boolean order = capability.documentType() == BusinessDocumentType.ORDER;
        boolean response = capability.documentType() == BusinessDocumentType.ORDER_RESPONSE;
        boolean unused = tradacoms && path.equals("/document/currency") && !order
                || tradacoms && path.equals("/lines/*/lineExtension") && !invoice
                || tradacoms && path.equals("/tax/amount") && !invoice
                || tradacoms && path.equals("/document/orderReference") && !invoice && !response
                || !tradacoms && path.equals("/document/orderReference") && order
                || !tradacoms && path.equals("/document/dueDate") && !invoice
                || path.startsWith("/delivery/") && !order
                || path.startsWith("/lines/*/tax") && !invoice
                || path.equals("/document/totalPaymentAmount") && capability.documentType() != BusinessDocumentType.REMITTANCE_ADVICE;
        if (unused || !common.contains(path) && !extra.contains(path)) throw new IllegalArgumentException(
                "The target encoder cannot represent mapped field " + path + ". Map it to a supported field or review an explicit omission.");
    }
}
