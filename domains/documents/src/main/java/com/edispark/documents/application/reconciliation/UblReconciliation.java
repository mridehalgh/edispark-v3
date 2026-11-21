package com.edispark.documents.application.reconciliation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Compares stated values on explicitly correlated UBL documents. */
public final class UblReconciliation {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final int MAX_LINES = 400;

    private UblReconciliation() { }

    public enum Comparison {
        ORDER_RESPONSE("Order", "OrderResponse", "OrderReference"),
        DESPATCH_RECEIPT("DespatchAdvice", "ReceiptAdvice", "DespatchDocumentReference"),
        INVOICE_REMITTANCE("Invoice", "RemittanceAdvice", null);

        private final String primaryRoot;
        private final String relatedRoot;
        private final String reference;

        Comparison(String primaryRoot, String relatedRoot, String reference) {
            this.primaryRoot = primaryRoot;
            this.relatedRoot = relatedRoot;
            this.reference = reference;
        }
    }

    public record Finding(String code, Integer sourceIndex, Integer relatedIndex, Integer lineIndex) { }
    public record Value(Integer sourceIndex, BigDecimal expected, BigDecimal observed, String unit) { }
    public record Report(List<Finding> findings, List<Value> values) { }

    public static Report compare(Comparison kind, byte[] primary, List<byte[]> related) {
        var base = document(primary, kind.primaryRoot);
        var counterparts = related.stream().map(bytes -> document(bytes, kind.relatedRoot)).toList();
        String businessId = text(base.path("ID"));
        if (businessId == null) throw new IllegalArgumentException("Primary UBL document needs an ID");
        var counterpartIds = new HashSet<String>();
        for (var counterpart : counterparts) {
            String id = text(counterpart.path("ID"));
            if (id == null || !counterpartIds.add(id)) {
                return new Report(List.of(new Finding("RELATED_DOCUMENT_REFERENCE_AMBIGUOUS", null, null, null)), List.of());
            }
        }
        var findings = new ArrayList<Finding>();
        var values = new ArrayList<Value>();
        if (kind == Comparison.INVOICE_REMITTANCE) {
            reconcileInvoice(base, counterparts, businessId, findings, values);
        } else {
            reconcileLines(kind, base, counterparts, businessId, findings, values);
        }
        return new Report(List.copyOf(findings), List.copyOf(values));
    }

    private static void reconcileLines(Comparison kind, JsonNode base, List<JsonNode> related, String businessId,
            List<Finding> findings, List<Value> values) {
        var primaryLines = lines(base, kind == Comparison.ORDER_RESPONSE ? "OrderLine" : "DespatchLine");
        if (primaryLines.isEmpty()) {
            findings.add(new Finding("PRIMARY_LINES_UNAVAILABLE", null, null, null));
            return;
        }
        var expected = new HashMap<String, IndexedMeasure>();
        var ambiguous = new HashSet<String>();
        for (int index = 0; index < primaryLines.size(); index++) {
            var line = kind == Comparison.ORDER_RESPONSE ? first(primaryLines.get(index).path("LineItem")) : primaryLines.get(index);
            String id = text(line.path("ID"));
            var quantity = measure(line.path(kind == Comparison.ORDER_RESPONSE ? "Quantity" : "DeliveredQuantity"), "unitCode");
            if (id == null || quantity == null) {
                findings.add(new Finding("PRIMARY_LINE_INCOMPLETE", index, null, null));
            } else if (expected.putIfAbsent(id, new IndexedMeasure(index, quantity)) != null) {
                ambiguous.add(id);
                findings.add(new Finding("AMBIGUOUS_PRIMARY_LINE_REFERENCE", index, null, null));
            }
        }
        var observed = new HashMap<String, BigDecimal>();
        for (int index = 0; index < related.size(); index++) {
            var document = related.get(index);
            if (!references(document.path(kind.reference), businessId)) {
                findings.add(new Finding("DOCUMENT_REFERENCE_UNMATCHED", null, index, null));
                continue;
            }
            var counterpartLines = lines(document, kind == Comparison.ORDER_RESPONSE ? "OrderLine" : "ReceiptLine");
            for (int lineIndex = 0; lineIndex < counterpartLines.size(); lineIndex++) {
                var line = counterpartLines.get(lineIndex);
                String reference = kind == Comparison.ORDER_RESPONSE
                        ? text(first(line.path("OrderLineReference")).path("LineID"))
                        : text(first(line.path("DespatchLineReference")).path("LineID"));
                var valueNode = kind == Comparison.ORDER_RESPONSE ? first(line.path("LineItem")).path("Quantity")
                        : line.path("ReceivedQuantity");
                var target = reference == null ? null : expected.get(reference);
                if (target == null || ambiguous.contains(reference)) {
                    findings.add(new Finding("LINE_REFERENCE_UNMATCHED", null, index, lineIndex));
                    continue;
                }
                var amount = measure(valueNode, "unitCode");
                if (amount == null || !target.measure.unit.equals(amount.unit)) {
                    findings.add(new Finding("UNIT_UNAVAILABLE_OR_INCOMPATIBLE", target.index, index, lineIndex));
                    continue;
                }
                observed.merge(reference, amount.amount, BigDecimal::add);
                if (kind == Comparison.DESPATCH_RECEIPT) {
                    var shortQuantity = measure(line.path("ShortQuantity"), "unitCode");
                    var rejected = measure(line.path("RejectedQuantity"), "unitCode");
                    if (positive(shortQuantity)) findings.add(new Finding("SHORT_QUANTITY_RECORDED", target.index, index, lineIndex));
                    if (positive(rejected)) findings.add(new Finding("REJECTED_QUANTITY_RECORDED", target.index, index, lineIndex));
                }
            }
        }
        expected.entrySet().stream().sorted(java.util.Comparator.comparingInt(item -> item.getValue().index)).forEach(entry -> {
            IndexedMeasure target = entry.getValue();
            if (ambiguous.contains(entry.getKey())) return;
            var actual = observed.get(entry.getKey());
            addComparison(findings, values, target.index, target.measure, actual,
                    actual == null ? "NO_MATCHING_LINE" : "QUANTITY_DIFFERENCE");
        });
    }

    private static void reconcileInvoice(JsonNode invoice, List<JsonNode> remittances, String invoiceId,
            List<Finding> findings, List<Value> values) {
        var payable = measure(first(invoice.path("LegalMonetaryTotal")).path("PayableAmount"), "currencyID");
        if (payable == null) {
            findings.add(new Finding("INVOICE_PAYABLE_UNAVAILABLE", null, null, null));
            return;
        }
        BigDecimal applied = null;
        for (int index = 0; index < remittances.size(); index++) {
            var lines = lines(remittances.get(index), "RemittanceAdviceLine");
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                var line = lines.get(lineIndex);
                var billing = first(line.path("BillingReference"));
                if (!references(billing.path("InvoiceDocumentReference"), invoiceId)) {
                    findings.add(new Finding("INVOICE_REFERENCE_UNMATCHED", null, index, lineIndex));
                    continue;
                }
                var credit = measure(line.path("CreditLineAmount"), "currencyID");
                var debit = measure(line.path("DebitLineAmount"), "currencyID");
                if (credit == null && debit == null || credit != null && !credit.unit.equals(payable.unit)
                        || debit != null && !debit.unit.equals(payable.unit)) {
                    findings.add(new Finding("CURRENCY_UNAVAILABLE_OR_INCOMPATIBLE", null, index, lineIndex));
                    continue;
                }
                var amount = (credit == null ? BigDecimal.ZERO : credit.amount).subtract(debit == null ? BigDecimal.ZERO : debit.amount);
                applied = (applied == null ? BigDecimal.ZERO : applied).add(amount);
            }
        }
        addComparison(findings, values, null, payable, applied, applied == null ? "NO_MATCHING_ALLOCATION" : "ALLOCATION_DIFFERENCE");
    }

    private static void addComparison(List<Finding> findings, List<Value> values, Integer index,
            Measure expected, BigDecimal observed, String difference) {
        values.add(new Value(index, expected.amount, observed, expected.unit));
        if (observed == null || expected.amount.compareTo(observed) != 0) findings.add(new Finding(difference, index, null, null));
    }

    private static boolean positive(Measure measure) {
        return measure != null && measure.amount.signum() > 0;
    }

    private static List<JsonNode> lines(JsonNode document, String name) {
        JsonNode lines = document.path(name);
        if (lines.size() > MAX_LINES) throw new IllegalArgumentException("Reconciliation line limit exceeded");
        return lines.isArray() ? lines.valueStream().toList() : List.of();
    }

    private static boolean references(JsonNode node, String id) {
        return node.isArray() && !node.isEmpty() && node.valueStream().allMatch(ref -> id.equals(text(ref.path("ID"))));
    }

    private static String text(JsonNode node) {
        var value = first(node).path("_");
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static Measure measure(JsonNode node, String unitKey) {
        var item = first(node);
        var value = item.path("_");
        var unit = item.path(unitKey);
        if (!value.isNumber() || !unit.isTextual() || !unit.asText().matches("[A-Z0-9]{2,8}")) return null;
        BigDecimal amount = value.decimalValue();
        if (amount.precision() > 38 || amount.scale() < -12 || amount.scale() > 12) return null;
        return new Measure(amount, unit.asText());
    }

    private static JsonNode first(JsonNode node) {
        return node.path(0);
    }

    private static JsonNode document(byte[] bytes, String root) {
        if (bytes.length > 1024 * 1024) throw new IllegalArgumentException("Reconciliation input too large");
        try {
            var envelope = JSON.readTree(bytes);
            var document = first(envelope.path(root));
            if (!document.isObject() || !("urn:oasis:names:specification:ubl:schema:xsd:" + root + "-2")
                    .equals(envelope.path("_D").asText())) {
                throw new IllegalArgumentException("Unexpected UBL document type");
            }
            return document;
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid UBL JSON", error);
        }
    }

    private record Measure(BigDecimal amount, String unit) { }
    private record IndexedMeasure(int index, Measure measure) { }
}
