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
        String bgmCode = capability.documentType() == BusinessDocumentType.INVOICE ? "380" : "220";
        String directoryRelease = capability.release().startsWith("D")
                ? capability.release().substring(1) : capability.release();
        List<String> message = new ArrayList<>();
        message.add("UNH+1+" + edi(capability.messageType()) + ":D:" + edi(directoryRelease) + ":UN");
        message.add("BGM+" + bgmCode + '+' + edi(reference) + "+9");
        if (!issueDate.isBlank()) message.add("DTM+137:" + edi(issueDate) + ":102");
        addEdifactCurrency(message, document);
        addEdifactLines(message, document, capability.documentType() == BusinessDocumentType.INVOICE);
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
        String message = capability.documentType() == BusinessDocumentType.INVOICE ? "INVFIL" : "ORDHDR";
        StringBuilder result = new StringBuilder("STX=ANA:1+" + edi(sender) + '+' + edi(receiver) + '+'
                + TRADACOMS_DATE.format(clock.instant()) + ':'
                + TRADACOMS_TIME.format(clock.instant()) + '+' + control + "++" + message + "'"
                + "MHD=1+" + message + ':' + edi(capability.release()) + "'"
                + "TYP=" + (capability.documentType() == BusinessDocumentType.INVOICE ? "0700" : "0430") + "'"
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

    private void addEdifactLines(List<String> message, JsonNode document, boolean invoice) {
        JsonNode lines = document.path("lines");
        if (!lines.isArray()) return;
        for (int index = 0; index < lines.size(); index++) addEdifactLine(message, lines.get(index), index + 1, invoice);
    }

    private void addEdifactLine(List<String> message, JsonNode line, int position, boolean invoice) {
        message.add("LIN+" + edi(text(line, "/id", Integer.toString(position))) + "++"
                + edi(text(line, "/gtin", "")) + ":EN");
        addEdifactItemIdentifiers(message, line);
        String name = text(line, "/name", text(line, "/description", ""));
        if (!name.isBlank()) message.add("IMD+F++:::" + edi(name));
        message.add("QTY+" + (invoice ? "47" : "21") + ':' + edi(text(line, "/quantity", "0")) + ':'
                + edi(text(line, "/unitCode", "")));
        addSegment(message, "PRI+AAA:", line, "/price");
        addSegment(message, "MOA+203:", line, "/lineExtension");
        if (invoice) addEdifactTax(message, line);
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
        result.append("MHD=2+").append(invoice ? "INVOIC" : "ORDERS").append(':')
                .append(edi(capability.release())).append("'");
        if (invoice) result.append("IRF=").append(edi(reference)).append('+')
                .append(edi(tradacomsDate(document))).append("'");
        else result.append("ORD=").append(edi(reference)).append("++")
                .append(edi(text(document, "/document/currency", ""))).append("'");
        addTradacomsLines(result, document, invoice);
        addTradacomsTotals(result, document, invoice);
        result.append("MTR=").append(invoice ? "7" : "6").append("'");
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
        else result.append(edi(text(document, "/totals/allowance", ""))).append('+');
        result.append(edi(text(document, "/totals/payable", ""))).append("'");
    }

    private String tradacomsDate(JsonNode document) {
        String date = text(document, "/document/issueDate", "").replace("-", "");
        return date.length() == 8 ? date.substring(2) : date;
    }

    private String text(JsonNode document, String pointer, String fallback) {
        JsonNode value = document.at(pointer);
        return value.isValueNode() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    private String edi(String value) {
        if (value == null) return "";
        String ascii = value.replaceAll("[^\\x20-\\x7E]", "").strip();
        if (ascii.length() > 70) ascii = ascii.substring(0, 70);
        return ascii.replace("?", "??").replace("+", "?+").replace(":", "?:").replace("'", "?'");
    }
}
