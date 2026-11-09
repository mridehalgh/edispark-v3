package com.edispark.exchange.application;

import com.edispark.partnerships.domain.model.DocumentProfile;
import com.edispark.partnerships.domain.model.FieldRequirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Applies pinned partner rules on the server, independently of the authoring UI. */
final class PartnerProfileValidation {
    private static final Map<String, String> FIELDS = Map.ofEntries(
            Map.entry("document.uuid", "UUID"), Map.entry("document.copyIndicator", "CopyIndicator"),
            Map.entry("order.typeCode", "OrderTypeCode"), Map.entry("order.customerReference", "CustomerReference"),
            Map.entry("order.pricingCurrency", "PricingCurrencyCode"), Map.entry("orderResponse.salesOrderId", "SalesOrderID"),
            Map.entry("orderResponse.customerReference", "CustomerReference"), Map.entry("despatch.typeCode", "DespatchAdviceTypeCode"),
            Map.entry("despatch.lineCount", "LineCountNumeric"), Map.entry("invoice.typeCode", "InvoiceTypeCode"),
            Map.entry("invoice.dueDate", "DueDate"), Map.entry("invoice.taxPointDate", "TaxPointDate"),
            Map.entry("invoice.buyerReference", "BuyerReference"), Map.entry("invoice.accountingCost", "AccountingCost"),
            Map.entry("remittance.typeCode", "RemittanceAdviceTypeCode"), Map.entry("remittance.payerReference", "PayerReference"),
            Map.entry("remittance.invoicingReference", "InvoicingPartyReference"), Map.entry("receipt.typeCode", "ReceiptAdviceTypeCode"),
            Map.entry("receipt.lineCount", "LineCountNumeric"), Map.entry("invoice.payment.meansCode", "PaymentMeans/PaymentMeansCode"),
            Map.entry("despatch.shipment.id", "Shipment/ID"), Map.entry("despatch.shipment.handlingUnits[].sscc", "Shipment/TransportHandlingUnit/TraceID"));

    static List<ReadinessIssue> validate(DocumentProfile profile, byte[] bytes) {
        List<ReadinessIssue> issues = new ArrayList<>();
        try {
            JsonNode envelope = new ObjectMapper().readTree(bytes);
            JsonNode root = null;
            var fields = envelope.fields();
            while (fields.hasNext()) { var entry = fields.next(); if (!entry.getKey().startsWith("_") && entry.getValue().isArray()) { root = entry.getValue().path(0); break; } }
            for (var rule : profile.fieldRules()) {
                String path = FIELDS.get(rule.fieldKey());
                boolean constrained = rule.requirement() == FieldRequirement.REQUIRED || rule.fixedValue() != null || !rule.allowedValues().isEmpty()
                        || rule.minimum() != null || rule.maximum() != null || rule.pattern() != null;
                if (path == null) { if (constrained) issues.add(ReadinessIssue.configuration("PROFILE_RULE_UNSUPPORTED", "The selected profile contains an unsupported rule: " + rule.fieldKey())); continue; }
                List<String> values = new ArrayList<>(); collect(root, path.split("/"), 0, values);
                boolean invalid = rule.requirement() == FieldRequirement.REQUIRED && (values.isEmpty() || values.stream().anyMatch(String::isBlank));
                for (String value : values) {
                    if (value.isBlank()) continue;
                    invalid |= rule.fixedValue() != null && !rule.fixedValue().equals(value);
                    invalid |= !rule.allowedValues().isEmpty() && !rule.allowedValues().contains(value);
                    invalid |= rule.pattern() != null && !value.matches(rule.pattern());
                    try { if (rule.minimum() != null) invalid |= new BigDecimal(value).compareTo(rule.minimum()) < 0;
                        if (rule.maximum() != null) invalid |= new BigDecimal(value).compareTo(rule.maximum()) > 0;
                    } catch (NumberFormatException exception) { invalid = true; }
                }
                if (invalid) issues.add(ReadinessIssue.document("PARTNER_PROFILE_INVALID", path,
                        rule.message() == null ? "Check " + rule.fieldKey() + " against the selected partner requirements." : rule.message()));
            }
        } catch (Exception exception) { issues.add(ReadinessIssue.configuration("PROFILE_VALIDATION_UNAVAILABLE", "Partner profile validation could not be completed.")); }
        return issues;
    }

    private static void collect(JsonNode value, String[] path, int index, List<String> result) {
        if (value == null || value.isMissingNode()) { result.add(""); return; }
        if (value.isArray()) { value.forEach(child -> collect(child, path, index, result)); return; }
        if (index == path.length) { result.add(value.path("_").asText("")); return; }
        collect(value.path(path[index]), path, index + 1, result);
    }
}
