package com.edispark.integrationapplication.inbound;

import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class InboundEdiMappingInputParser {
    private final ObjectMapper mapper;

    InboundEdiMappingInputParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    byte[] edifact(List<String> segments, BusinessDocumentType type) {
        return type == BusinessDocumentType.INVOICE ? edifactInvoice(segments) : edifactOrder(segments);
    }

    byte[] tradacoms(List<String> segments, BusinessDocumentType type) {
        return type == BusinessDocumentType.INVOICE ? tradacomsInvoice(segments) : tradacomsOrder(segments);
    }

    private byte[] edifactOrder(List<String> segments) {
        ObjectNode root = root();
        String id = element(required(segments, "BGM+"), 1);
        String requestedDate = qualifiedDate(segments, "2");
        String supplier = partyId(segments, "SU");
        String buyer = partyId(segments, "BY");
        String delivery = partyId(segments, "DP");

        ObjectNode document = root.withObject("/document");
        document.put("id", id);
        document.put("salesOrderId", reference(segments, "PD", id));
        document.put("issueDate", qualifiedDate(segments, "137"));
        document.put("issueTime", interchangeTime(segments));
        document.put("typeCode", element(required(segments, "BGM+"), 0));
        document.put("currency", composite(element(required(segments, "CUX+"), 0), 1));
        document.put("customerReference", reference(segments, "PD", id));
        document.put("note", freeText(segments, "Order " + id));

        ObjectNode parties = root.withObject("/parties");
        parties.put("sender", supplier);
        parties.put("receiver", buyer);
        parties.put("buyerId", buyer);
        parties.put("buyerName", partyName(segments, "BY"));
        parties.put("supplierId", supplier);
        parties.put("supplierName", partyName(segments, "SU"));
        parties.put("buyerTaxId", partyTaxId(segments, "BY"));
        parties.put("supplierTaxId", partyTaxId(segments, "SU"));

        ObjectNode deliveryNode = root.withObject("/delivery");
        deliveryNode.put("id", delivery);
        deliveryNode.put("requestedDate", requestedDate);
        deliveryNode.put("latestDate", valueOr(qualifiedDate(segments, "64"), requestedDate));
        deliveryNode.put("locationId", delivery);
        deliveryNode.put("country", partyCountry(segments, "DP"));
        deliveryNode.put("terms", deliveryTerms(segments));

        ObjectNode payment = root.withObject("/payment");
        payment.put("termsId", paymentTerms(segments));
        payment.put("dueDate", valueOr(qualifiedDate(segments, "13"), plusDays(document.path("issueDate").asText(), 30)));
        addOrderTotals(root, segments);
        addEdifactLines(root, segments, false);
        return bytes(root);
    }

    private byte[] tradacomsOrder(List<String> segments) {
        ObjectNode root = root();
        String order = required(segments, "ORD=");
        String id = element(order, 0);
        String supplier = tradacomsParty(segments, 2);
        String buyer = tradacomsParty(segments, 1);
        String deliveryDate = element(required(segments, "DIN="), 0);

        ObjectNode document = root.withObject("/document");
        document.put("id", id);
        document.put("salesOrderId", id);
        document.put("issueDate", valueOr(date(element(order, 1)), interchangeDate(segments)));
        document.put("issueTime", tradacomsTime(segments));
        document.put("typeCode", "220");
        document.put("currency", valueOr(element(order, 2), "GBP"));
        document.put("customerReference", id);
        document.put("note", "Retail order " + id);

        ObjectNode parties = root.withObject("/parties");
        parties.put("sender", supplier);
        parties.put("receiver", buyer);
        parties.put("buyerId", buyer);
        parties.put("buyerName", tradacomsPartyName(segments, "CDT="));
        parties.put("supplierId", supplier);
        parties.put("supplierName", tradacomsPartyName(segments, "SDT="));
        parties.put("buyerTaxId", "[tax_identifier]");
        parties.put("supplierTaxId", "[tax_identifier]");

        String location = composite(element(required(segments, "CLO="), 0), 0);
        ObjectNode delivery = root.withObject("/delivery");
        delivery.put("id", location);
        delivery.put("requestedDate", date(deliveryDate));
        delivery.put("latestDate", date(deliveryDate));
        delivery.put("locationId", location);
        delivery.put("country", "GB");
        delivery.put("terms", valueOr(element(required(segments, "DIN="), 2), "DAP"));

        ObjectNode payment = root.withObject("/payment");
        payment.put("termsId", "NET30");
        payment.put("dueDate", plusDays(document.path("issueDate").asText(), 30));
        addTradacomsTotals(root, segments);
        addTradacomsLines(root, segments);
        return bytes(root);
    }

    private byte[] edifactInvoice(List<String> segments) {
        ObjectNode root = root();
        String bgm = required(segments, "BGM+");
        String id = element(bgm, 1);
        String supplier = partyId(segments, "SU");
        String buyer = partyId(segments, "BY");
        String dueDate = qualifiedDate(segments, "13");
        String currency = composite(element(required(segments, "CUX+"), 0), 1);

        ObjectNode document = root.withObject("/document");
        document.put("id", id);
        document.put("issueDate", qualifiedDate(segments, "137"));
        document.put("dueDate", dueDate);
        document.put("typeCode", element(bgm, 0));
        document.put("currency", currency);
        document.put("taxCurrency", currency);
        document.put("orderReference", reference(segments, "ON", id));
        document.put("note", freeText(segments, "Invoice " + id));

        addParties(root, segments, supplier, buyer);
        ObjectNode delivery = root.withObject("/delivery");
        delivery.put("actualDate", valueOr(qualifiedDate(segments, "131"), document.path("issueDate").asText()));
        delivery.put("locationId", buyer);
        delivery.put("country", partyCountry(segments, "BY"));

        ObjectNode payment = root.withObject("/payment");
        payment.put("meansCode", "30");
        payment.put("dueDate", dueDate);
        payment.put("reference", id);
        payment.put("termsId", paymentTerms(segments));
        addInvoiceTax(root, segments, monetaryAmount(segments, "176"), monetaryAmount(segments, "125"));
        addOrderTotals(root, segments);
        addEdifactLines(root, segments, true);
        return bytes(root);
    }

    private byte[] tradacomsInvoice(List<String> segments) {
        ObjectNode root = root();
        String invoice = required(segments, "IRF=");
        String id = element(invoice, 0);
        String supplier = tradacomsParty(segments, 1);
        String buyer = tradacomsParty(segments, 2);
        String issueDate = valueOr(date(element(invoice, 1)), interchangeDate(segments));
        String total = required(segments, "TLR=");

        ObjectNode document = root.withObject("/document");
        document.put("id", id);
        document.put("issueDate", issueDate);
        document.put("dueDate", plusDays(issueDate, 30));
        document.put("typeCode", "380");
        document.put("currency", "GBP");
        document.put("taxCurrency", "GBP");
        document.put("orderReference", element(required(segments, "ODD="), 0));
        document.put("note", "Retail invoice " + id);

        ObjectNode parties = root.withObject("/parties");
        parties.put("sender", supplier);
        parties.put("receiver", buyer);
        parties.put("buyerId", buyer);
        parties.put("buyerName", tradacomsPartyName(segments, "CDT="));
        parties.put("supplierId", supplier);
        parties.put("supplierName", tradacomsPartyName(segments, "SDT="));
        parties.put("buyerTaxId", "[tax_identifier]");
        parties.put("supplierTaxId", "[tax_identifier]");

        ObjectNode delivery = root.withObject("/delivery");
        delivery.put("actualDate", issueDate);
        delivery.put("locationId", buyer);
        delivery.put("country", "GB");
        ObjectNode payment = root.withObject("/payment");
        payment.put("meansCode", "30");
        payment.put("dueDate", plusDays(issueDate, 30));
        payment.put("reference", id);
        payment.put("termsId", "NET30");

        addInvoiceTax(root, segments, element(total, 1), element(total, 0));
        ObjectNode totals = root.withObject("/totals");
        totals.put("lineExtension", decimal(element(total, 0)));
        totals.put("allowance", BigDecimal.ZERO);
        totals.put("taxExclusive", decimal(element(total, 0)));
        totals.put("taxInclusive", decimal(element(total, 2)));
        totals.put("payable", decimal(element(total, 2)));
        addTradacomsInvoiceLines(root, segments);
        return bytes(root);
    }

    private void addParties(ObjectNode root, List<String> segments, String supplier, String buyer) {
        ObjectNode parties = root.withObject("/parties");
        parties.put("sender", supplier);
        parties.put("receiver", buyer);
        parties.put("buyerId", buyer);
        parties.put("buyerName", partyName(segments, "BY"));
        parties.put("supplierId", supplier);
        parties.put("supplierName", partyName(segments, "SU"));
        parties.put("buyerTaxId", partyTaxId(segments, "BY"));
        parties.put("supplierTaxId", partyTaxId(segments, "SU"));
    }

    private void addInvoiceTax(ObjectNode root, List<String> segments, String amount, String taxableAmount) {
        String taxSegment = optional(segments, "TAX+");
        ObjectNode tax = root.withObject("/tax");
        tax.put("amount", decimal(amount));
        tax.put("taxableAmount", decimal(taxableAmount));
        tax.put("category", taxSegment.isBlank() ? "S" : valueOr(element(taxSegment, 5), "S"));
        tax.put("percent", taxSegment.isBlank() ? BigDecimal.valueOf(20)
                : decimal(valueOr(composite(element(taxSegment, 4), 3), "20")));
        tax.put("scheme", "VAT");
    }

    private ObjectNode root() {
        return mapper.createObjectNode();
    }

    private void addOrderTotals(ObjectNode root, List<String> segments) {
        ObjectNode totals = root.withObject("/totals");
        totals.put("lineExtension", decimal(monetaryAmount(segments, "79")));
        totals.put("allowance", decimal(optionalMonetaryAmount(segments, "8", "0")));
        totals.put("taxExclusive", decimal(monetaryAmount(segments, "125")));
        totals.put("taxInclusive", decimal(monetaryAmount(segments, "86")));
        totals.put("payable", decimal(monetaryAmount(segments, "86")));
    }

    private void addEdifactLines(ObjectNode root, List<String> segments, boolean invoice) {
        ArrayNode lines = root.putArray("lines");
        for (int index = 0; index < segments.size(); index++) {
            if (!segments.get(index).startsWith("LIN+")) continue;
            int end = nextLineOrSummary(segments, index + 1);
            lines.add(edifactLine(segments.subList(index, end), invoice));
        }
    }

    private ObjectNode edifactLine(List<String> group, boolean invoice) {
        String lin = group.getFirst();
        String pia = required(group, "PIA+");
        String quantity = element(required(group, "QTY+"), 0);
        String name = composite(element(required(group, "IMD+"), 2), 3);
        String tax = optional(group, "TAX+");
        ObjectNode line = mapper.createObjectNode();
        line.put("id", element(lin, 0));
        line.put("quantity", decimal(composite(quantity, 1)));
        line.put("unitCode", composite(quantity, 2));
        line.put("lineExtension", decimal(qualifiedComposite(group, "MOA+", "203", 1)));
        line.put("buyerItemId", composite(element(pia, 1), 0));
        line.put("sellerItemId", composite(element(pia, 2), 0));
        line.put("gtin", composite(element(lin, 2), 0));
        line.put("description", name);
        line.put("name", name);
        line.put("price", decimal(composite(element(required(group, "PRI+"), 0), 1)));
        line.put("taxCategory", tax.isBlank() ? "S" : element(tax, 5));
        line.put("taxPercent", tax.isBlank() ? BigDecimal.valueOf(20)
                : decimal(composite(element(tax, 4), 3)));
        if (invoice) line.put("orderLineId", element(lin, 0));
        return line;
    }

    private void addTradacomsTotals(ObjectNode root, List<String> segments) {
        String total = required(segments, "OTR=");
        ObjectNode totals = root.withObject("/totals");
        totals.put("lineExtension", decimal(element(total, 1)));
        totals.put("allowance", decimal(element(total, 2)));
        BigDecimal taxExclusive = decimal(element(total, 1)).subtract(decimal(element(total, 2)));
        totals.put("taxExclusive", taxExclusive);
        totals.put("taxInclusive", decimal(element(total, 3)));
        totals.put("payable", decimal(element(total, 3)));
    }

    private void addTradacomsLines(ObjectNode root, List<String> segments) {
        ArrayNode lines = root.putArray("lines");
        segments.stream().filter(segment -> segment.startsWith("OLD="))
                .map(this::tradacomsLine).forEach(lines::add);
    }

    private void addTradacomsInvoiceLines(ObjectNode root, List<String> segments) {
        ArrayNode lines = root.putArray("lines");
        segments.stream().filter(segment -> segment.startsWith("ILD="))
                .map(this::tradacomsInvoiceLine).forEach(lines::add);
    }

    private ObjectNode tradacomsInvoiceLine(String segment) {
        ObjectNode line = tradacomsLine(segment);
        line.put("lineExtension", decimal(element(segment, 6)));
        line.put("orderLineId", element(segment, 0));
        return line;
    }

    private ObjectNode tradacomsLine(String segment) {
        String productIds = element(segment, 2);
        String quantity = element(segment, 4);
        BigDecimal price = decimal(element(segment, 5));
        String name = element(segment, 3);
        ObjectNode line = mapper.createObjectNode();
        line.put("id", element(segment, 0));
        line.put("quantity", decimal(composite(quantity, 0)));
        line.put("unitCode", valueOr(composite(quantity, 1), "EA"));
        line.put("lineExtension", decimal(composite(quantity, 0)).multiply(price));
        line.put("buyerItemId", composite(productIds, 0));
        line.put("sellerItemId", composite(productIds, 1));
        line.put("gtin", element(segment, 1));
        line.put("description", name);
        line.put("name", name);
        line.put("price", price);
        line.put("taxCategory", "S");
        line.put("taxPercent", 20);
        return line;
    }

    private int nextLineOrSummary(List<String> segments, int start) {
        for (int index = start; index < segments.size(); index++) {
            if (segments.get(index).startsWith("LIN+") || segments.get(index).startsWith("UNS+")) return index;
        }
        return segments.size();
    }

    private String partyId(List<String> segments, String qualifier) {
        return composite(element(required(segments, "NAD+" + qualifier + "+"), 1), 0);
    }

    private String partyName(List<String> segments, String qualifier) {
        return element(required(segments, "NAD+" + qualifier + "+"), 3);
    }

    private String tradacomsPartyName(List<String> segments, String prefix) {
        String segment = optional(segments, prefix);
        return segment.isBlank() ? "[name]" : valueOr(composite(element(segment, 0), 1), "[name]");
    }

    private String partyTaxId(List<String> segments, String qualifier) {
        int start = indexOf(segments, "NAD+" + qualifier + "+");
        for (int index = start + 1; index < segments.size() && !segments.get(index).startsWith("NAD+"); index++) {
            if (segments.get(index).startsWith("RFF+VA:")) return composite(element(segments.get(index), 0), 1);
        }
        return "[tax_identifier]";
    }

    private String partyCountry(List<String> segments, String qualifier) {
        String[] values = body(required(segments, "NAD+" + qualifier + "+"));
        return values.length == 0 ? "GB" : valueOr(values[values.length - 1], "GB");
    }

    private String tradacomsParty(List<String> segments, int elementIndex) {
        return composite(element(required(segments, "STX="), elementIndex), 0);
    }

    private String qualifiedDate(List<String> segments, String qualifier) {
        return segments.stream().filter(segment -> segment.startsWith("DTM+" + qualifier + ":"))
                .map(segment -> date(composite(element(segment, 0), 1))).findFirst().orElse("");
    }

    private String reference(List<String> segments, String qualifier, String fallback) {
        return segments.stream().filter(segment -> segment.startsWith("RFF+" + qualifier + ":"))
                .map(segment -> composite(element(segment, 0), 1)).findFirst().orElse(fallback);
    }

    private String monetaryAmount(List<String> segments, String qualifier) {
        return qualifiedComposite(segments, "MOA+", qualifier, 1);
    }

    private String optionalMonetaryAmount(List<String> segments, String qualifier, String fallback) {
        return segments.stream().filter(segment -> segment.startsWith("MOA+" + qualifier + ":"))
                .map(segment -> composite(element(segment, 0), 1)).findFirst().orElse(fallback);
    }

    private String qualifiedComposite(List<String> segments, String prefix, String qualifier, int component) {
        return segments.stream().filter(segment -> segment.startsWith(prefix + qualifier + ":"))
                .map(segment -> composite(element(segment, 0), component)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Required EDI value is missing: " + prefix + qualifier));
    }

    private String freeText(List<String> segments, String fallback) {
        String segment = optional(segments, "FTX+AAI+");
        return segment.isBlank() ? fallback : element(segment, 3);
    }

    private String deliveryTerms(List<String> segments) {
        String segment = required(segments, "TOD+");
        return composite(element(segment, 2), 0);
    }

    private String paymentTerms(List<String> segments) {
        String segment = required(segments, "PAT+");
        String period = composite(element(segment, 2), 3);
        return period.isBlank() ? element(segment, 0) : "NET" + period;
    }

    private String interchangeTime(List<String> segments) {
        String value = composite(element(required(segments, "UNB+"), 3), 1);
        if (value.length() == 4) return value.substring(0, 2) + ":" + value.substring(2) + ":00Z";
        return "00:00:00Z";
    }

    private String interchangeDate(List<String> segments) {
        return date(composite(element(required(segments, "STX="), 3), 0));
    }

    private String tradacomsTime(List<String> segments) {
        String value = composite(element(required(segments, "STX="), 3), 1);
        if (value.length() == 6) return value.substring(0, 2) + ":" + value.substring(2, 4) + ":" + value.substring(4) + "Z";
        return "00:00:00Z";
    }

    private String plusDays(String value, int days) {
        return value.isBlank() ? "" : LocalDate.parse(value).plusDays(days).toString();
    }

    private String date(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 8) return LocalDate.parse(digits, DateTimeFormatter.BASIC_ISO_DATE).toString();
        if (digits.length() == 6) return LocalDate.parse(digits, DateTimeFormatter.ofPattern("yyMMdd")).toString();
        return "";
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private String required(List<String> segments, String prefix) {
        return segments.stream().filter(segment -> segment.startsWith(prefix)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Required EDI segment is missing: " + prefix));
    }

    private String optional(List<String> segments, String prefix) {
        return segments.stream().filter(segment -> segment.startsWith(prefix)).findFirst().orElse("");
    }

    private int indexOf(List<String> segments, String prefix) {
        for (int index = 0; index < segments.size(); index++) if (segments.get(index).startsWith(prefix)) return index;
        throw new IllegalArgumentException("Required EDI segment is missing: " + prefix);
    }

    private String element(String segment, int index) {
        String[] elements = body(segment);
        return index < elements.length ? elements[index] : "";
    }

    private String[] body(String segment) {
        return segment.substring(4).split("\\+", -1);
    }

    private String composite(String value, int index) {
        String[] components = value.split(":", -1);
        return index < components.length ? components[index] : "";
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private byte[] bytes(ObjectNode root) {
        try {
            return mapper.writeValueAsBytes(root);
        } catch (Exception exception) {
            throw new IllegalArgumentException("EDI mapping input could not be serialized", exception);
        }
    }
}
