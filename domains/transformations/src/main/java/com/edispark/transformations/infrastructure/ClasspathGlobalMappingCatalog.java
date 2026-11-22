package com.edispark.transformations.infrastructure;

import com.edispark.transformations.domain.GlobalMappingCatalog;
import com.edispark.transformations.domain.BusinessCodeCatalog;
import com.edispark.transformations.domain.BusinessCodeList;
import com.edispark.transformations.domain.BusinessCodeSyntax;
import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingOperation;
import com.edispark.transformations.domain.MappingRevision;
import com.edispark.transformations.domain.MappingSample;
import com.edispark.transformations.domain.MappingScope;
import com.edispark.transformations.domain.MappingStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class ClasspathGlobalMappingCatalog implements GlobalMappingCatalog {
    public static final String GLOBAL_OWNER = "__GLOBAL__";
    public static final String VENDOR = "EdiSpark";
    private static final Instant RELEASED_AT = Instant.parse("2026-11-01T00:00:00Z");
    private static final UUID UBL_ORDER = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac10");
    private static final UUID UBL_INVOICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac11");
    private static final UUID EDIFACT_ORDER = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac12");
    private static final UUID EDIFACT_INVOICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac13");
    private static final UUID TRADACOMS_ORDER = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac14");
    private static final UUID TRADACOMS_INVOICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac15");
    private static final UUID UBL_ORDER_RESPONSE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac16");
    private static final UUID UBL_DESPATCH_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac17");
    private static final UUID UBL_REMITTANCE_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac18");
    private static final UUID UBL_RECEIPT_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac19");
    private static final UUID EDIFACT_ORDER_RESPONSE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac30");
    private static final UUID EDIFACT_DESPATCH_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac31");
    private static final UUID EDIFACT_REMITTANCE_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac32");
    private static final UUID EDIFACT_RECEIPT_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac33");
    private static final UUID TRADACOMS_ORDER_RESPONSE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac34");
    private static final UUID TRADACOMS_DESPATCH_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac35");
    private static final UUID TRADACOMS_REMITTANCE_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac36");
    private static final UUID TRADACOMS_RECEIPT_ADVICE = id("d9c01ea5-f555-4c6d-9c87-1465ea78ac37");

    private final List<MappingRevision> mappings;
    private final BusinessCodeCatalog codeCatalog = new BusinessCodeCatalog();

    public ClasspathGlobalMappingCatalog() {
        String ublOrder = resource("ubl-order-2.1.json");
        String ublInvoice = resource("ubl-invoice-2.1.json");
        String orderModel = resource("order-edi-model.json");
        String invoiceModel = resource("invoice-edi-model.json");
        List<MappingRevision> published = new ArrayList<>(List.of(
                mapping("d9c01ea5-f555-4c6d-9c87-1465ea78ac20", "UBL Order 2.1 → EDIFACT ORDERS D96A",
                        UBL_ORDER, EDIFACT_ORDER, orderPaths(), false,
                        sample("UBL Order 2.1 JSON", ublOrder, null, "Parsed EDIFACT ORDERS D96A", orderModel,
                                resource("edifact-orders-d96a.edi"))),
                mapping("d9c01ea5-f555-4c6d-9c87-1465ea78ac21", "UBL Invoice 2.1 → EDIFACT INVOIC D96A",
                        UBL_INVOICE, EDIFACT_INVOICE, invoicePaths(), false,
                        sample("UBL Invoice 2.1 JSON", ublInvoice, null, "Parsed EDIFACT INVOIC D96A", invoiceModel,
                                resource("edifact-invoic-d96a.edi"))),
                mapping("d9c01ea5-f555-4c6d-9c87-1465ea78ac22", "UBL Order 2.1 → TRADACOMS ORDERS 9",
                        UBL_ORDER, TRADACOMS_ORDER, orderPaths(), false,
                        sample("UBL Order 2.1 JSON", ublOrder, null, "Parsed TRADACOMS ORDERS 9", orderModel,
                                resource("tradacoms-orders-9.edi"))),
                mapping("d9c01ea5-f555-4c6d-9c87-1465ea78ac23", "UBL Invoice 2.1 → TRADACOMS INVOIC 9",
                        UBL_INVOICE, TRADACOMS_INVOICE, invoicePaths(), false,
                        sample("UBL Invoice 2.1 JSON", ublInvoice, null, "Parsed TRADACOMS INVOIC 9", invoiceModel,
                                resource("tradacoms-invoic-9.edi"))),
                mapping("d9c01ea5-f555-4c6d-9c87-1465ea78ac24", "EDIFACT ORDERS D96A → UBL Order 2.1",
                        EDIFACT_ORDER, UBL_ORDER, orderPaths(), true,
                        sample("Parsed EDIFACT ORDERS D96A", orderModel, resource("edifact-orders-d96a.edi"),
                                "UBL Order 2.1 JSON", ublOrder, null)),
                mapping("d9c01ea5-f555-4c6d-9c87-1465ea78ac25", "EDIFACT INVOIC D96A → UBL Invoice 2.1",
                        EDIFACT_INVOICE, UBL_INVOICE, invoicePaths(), true,
                        sample("Parsed EDIFACT INVOIC D96A", invoiceModel, resource("edifact-invoic-d96a.edi"),
                                "UBL Invoice 2.1 JSON", ublInvoice, null)),
                mapping("d9c01ea5-f555-4c6d-9c87-1465ea78ac26", "TRADACOMS ORDERS 9 → UBL Order 2.1",
                        TRADACOMS_ORDER, UBL_ORDER, orderPaths(), true,
                        sample("Parsed TRADACOMS ORDERS 9", orderModel, resource("tradacoms-orders-9.edi"),
                                "UBL Order 2.1 JSON", ublOrder, null)),
                mapping("d9c01ea5-f555-4c6d-9c87-1465ea78ac27", "TRADACOMS INVOIC 9 → UBL Invoice 2.1",
                        TRADACOMS_INVOICE, UBL_INVOICE, invoicePaths(), true,
                        sample("Parsed TRADACOMS INVOIC 9", invoiceModel, resource("tradacoms-invoic-9.edi"),
                                "UBL Invoice 2.1 JSON", ublInvoice, null))));
        published.addAll(operationalMappings());
        published.add(BqAcknowledgementMapping.create(sample("UBL Order Response 2.1 JSON",
                resource("ubl-bq-ack-v14.json"), null, "B&Q ACK v1.4 full acceptance",
                resource("bq-ack-v14-model.json"), resource("bq-ack-v14.edi"))));
        published.add(OrderResponseCoreMapping.create(sample("UBL Order Response 2.1 JSON",
                resource("ubl-order-response-core-v2.json"), null, "EDIFACT D96A core demo model",
                resource("order-response-core-v2-model.json"), resource("edifact-ordrsp-core-v2.edi"))));
        published.addAll(TescoUkMappings.revisions());
        mappings = List.copyOf(published);
    }

    @Override public List<MappingRevision> list() { return mappings; }
    @Override public Optional<MappingRevision> find(UUID id) {
        return mappings.stream().filter(mapping -> mapping.id().equals(id)).findFirst();
    }

    private MappingRevision mapping(String value, String name, UUID source, UUID target,
            List<PathPair> paths, boolean reverse, MappingSample sample) {
        UUID mappingId = id(value);
        BusinessCodeSyntax sourceSyntax = syntax(source);
        BusinessCodeSyntax targetSyntax = syntax(target);
        List<MappingNode> nodes = new ArrayList<>(paths.stream()
                .map(path -> node(mappingId, path, reverse, sourceSyntax, targetSyntax)).toList());
        if (reverse) nodes.addAll(ublEnvelopeNodes(mappingId, target));
        return new MappingRevision(mappingId, GLOBAL_OWNER, name, 1, source, target, MappingStatus.PUBLISHED,
                List.copyOf(nodes), "edispark-global-v1-" + mappingId, RELEASED_AT, RELEASED_AT, mappingId, null, null, 1,
                MappingScope.GLOBAL, VENDOR, sample, null);
    }

    private MappingNode node(UUID mappingId, PathPair path, boolean reverse,
            BusinessCodeSyntax sourceSyntax, BusinessCodeSyntax targetSyntax) {
        String source = reverse ? path.target() : path.source();
        String target = reverse ? path.source() : path.target();
        UUID nodeId = UUID.nameUUIDFromBytes((mappingId + ":" + source + ":" + target)
                .getBytes(StandardCharsets.UTF_8));
        if (path.codeList() != null) {
            return new MappingNode(nodeId, MappingOperation.CODE_LOOKUP, List.of(source), target, null, null,
                    codeCatalog.translationMap(path.codeList(), sourceSyntax, targetSyntax), path.codeList(),
                    sourceSyntax, targetSyntax);
        }
        return new MappingNode(nodeId, MappingOperation.COPY, List.of(source), target, null, null);
    }

    private List<MappingNode> ublEnvelopeNodes(UUID mappingId, UUID target) {
        String rootName = rootName(target);
        String root = "/" + rootName + "/0";
        String documentNamespace = "urn:oasis:names:specification:ubl:schema:xsd:"
                + rootName + "-2";
        return List.of(
                constant(mappingId, "/_D", documentNamespace),
                constant(mappingId, "/_A",
                        "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"),
                constant(mappingId, "/_B",
                        "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"),
                constant(mappingId, root + "/UBLVersionID/0/_", "2.1"),
                constant(mappingId, root + "/CustomizationID/0/_", "urn:edispark:ubl:mapped:1"),
                constant(mappingId, root + "/ProfileID/0/_",
                        "urn:edispark:profile:retail-" + rootName.toLowerCase() + ":1"));
    }

    private List<MappingRevision> operationalMappings() {
        List<OperationalDocument> documents = List.of(
                new OperationalDocument("Order Response", "ORDRSP", UBL_ORDER_RESPONSE,
                        EDIFACT_ORDER_RESPONSE, TRADACOMS_ORDER_RESPONSE),
                new OperationalDocument("Despatch Advice", "DESADV", UBL_DESPATCH_ADVICE,
                        EDIFACT_DESPATCH_ADVICE, TRADACOMS_DESPATCH_ADVICE),
                new OperationalDocument("Remittance Advice", "REMADV", UBL_REMITTANCE_ADVICE,
                        EDIFACT_REMITTANCE_ADVICE, TRADACOMS_REMITTANCE_ADVICE),
                new OperationalDocument("Receipt Advice", "RECADV", UBL_RECEIPT_ADVICE,
                        EDIFACT_RECEIPT_ADVICE, TRADACOMS_RECEIPT_ADVICE));
        List<MappingRevision> result = new ArrayList<>();
        for (int index = 0; index < documents.size(); index++) {
            OperationalDocument document = documents.get(index);
            List<PathPair> paths = operationalPaths(document.ubl());
            int outboundBase = 40 + index * 2;
            int inboundBase = 50 + index * 2;
            result.add(mapping(mappingId(outboundBase), "UBL " + document.label() + " 2.1 → EDIFACT "
                    + document.messageType() + " D96A", document.ubl(), document.edifact(), paths, false, null));
            result.add(mapping(mappingId(outboundBase + 1), "UBL " + document.label() + " 2.1 → TRADACOMS "
                    + document.messageType() + " 9", document.ubl(), document.tradacoms(), paths, false, null));
            result.add(mapping(mappingId(inboundBase), "EDIFACT " + document.messageType() + " D96A → UBL "
                    + document.label() + " 2.1", document.edifact(), document.ubl(), paths, true, null));
            result.add(mapping(mappingId(inboundBase + 1), "TRADACOMS " + document.messageType() + " 9 → UBL "
                    + document.label() + " 2.1", document.tradacoms(), document.ubl(), paths, true, null));
        }
        return List.copyOf(result);
    }

    private static List<PathPair> operationalPaths(UUID schemaId) {
        String root = "/" + rootName(schemaId) + "/0";
        String sender = schemaId.equals(UBL_ORDER_RESPONSE) ? "SellerSupplierParty"
                : schemaId.equals(UBL_DESPATCH_ADVICE) || schemaId.equals(UBL_RECEIPT_ADVICE)
                        ? "DespatchSupplierParty" : "AccountingCustomerParty";
        String receiver = schemaId.equals(UBL_ORDER_RESPONSE) ? "BuyerCustomerParty"
                : schemaId.equals(UBL_DESPATCH_ADVICE) || schemaId.equals(UBL_RECEIPT_ADVICE)
                        ? "DeliveryCustomerParty" : "AccountingSupplierParty";
        String line = schemaId.equals(UBL_ORDER_RESPONSE) ? "OrderLine/0/LineItem/0"
                : schemaId.equals(UBL_DESPATCH_ADVICE) ? "DespatchLine/0"
                : schemaId.equals(UBL_RECEIPT_ADVICE) ? "ReceiptLine/0" : "RemittanceAdviceLine/0";
        String quantity = schemaId.equals(UBL_DESPATCH_ADVICE) ? "DeliveredQuantity"
                : schemaId.equals(UBL_RECEIPT_ADVICE) ? "ReceivedQuantity" : "Quantity";
        List<PathPair> paths = new ArrayList<>(List.of(
                pair(root + "/ID/0/_", "/document/id"),
                pair(root + "/IssueDate/0/_", "/document/issueDate"),
                pair(root + "/" + sender + "/0/Party/0/EndpointID/0/_", "/parties/sender"),
                pair(root + "/" + receiver + "/0/Party/0/EndpointID/0/_", "/parties/receiver"),
                pair(root + "/" + line + "/ID/0/_", "/lines/0/id")));
        if (schemaId.equals(UBL_ORDER_RESPONSE)) {
            paths.add(pair(root + "/OrderReference/0/ID/0/_", "/document/orderReference"));
        }
        if (schemaId.equals(UBL_REMITTANCE_ADVICE)) {
            paths.add(pair(root + "/TotalPaymentAmount/0/_", "/document/totalPaymentAmount"));
            paths.add(coded(root + "/TotalPaymentAmount/0/currencyID", "/document/currency",
                    BusinessCodeList.CURRENCY));
        } else {
            paths.add(pair(root + "/" + line + "/" + quantity + "/0/_", "/lines/0/quantity"));
            paths.add(coded(root + "/" + line + "/" + quantity + "/0/unitCode", "/lines/0/unitCode",
                    BusinessCodeList.UNIT_OF_MEASURE));
        }
        return List.copyOf(paths);
    }

    private static String rootName(UUID schemaId) {
        if (schemaId.equals(UBL_ORDER)) return "Order";
        if (schemaId.equals(UBL_INVOICE)) return "Invoice";
        if (schemaId.equals(UBL_ORDER_RESPONSE)) return "OrderResponse";
        if (schemaId.equals(UBL_DESPATCH_ADVICE)) return "DespatchAdvice";
        if (schemaId.equals(UBL_REMITTANCE_ADVICE)) return "RemittanceAdvice";
        if (schemaId.equals(UBL_RECEIPT_ADVICE)) return "ReceiptAdvice";
        throw new IllegalArgumentException("No UBL root is registered for schema " + schemaId);
    }

    private static String mappingId(int suffix) {
        return "d9c01ea5-f555-4c6d-9c87-1465ea78ac" + suffix;
    }

    private MappingNode constant(UUID mappingId, String target, String value) {
        UUID nodeId = UUID.nameUUIDFromBytes((mappingId + ":constant:" + target + ":" + value)
                .getBytes(StandardCharsets.UTF_8));
        return new MappingNode(nodeId, MappingOperation.CONSTANT, List.of(), target, value, null);
    }

    private MappingSample sample(String sourceFormat, String sourceDocument, String sourceRaw,
            String targetFormat, String targetDocument, String targetRaw) {
        return new MappingSample(sourceFormat, sourceDocument, sourceRaw, targetFormat, targetDocument, targetRaw);
    }

    private static List<PathPair> orderPaths() {
        List<PathPair> paths = new ArrayList<>();
        paths.add(pair("/Order/0/ID/0/_", "/document/id"));
        paths.add(pair("/Order/0/SalesOrderID/0/_", "/document/salesOrderId"));
        paths.add(pair("/Order/0/IssueDate/0/_", "/document/issueDate"));
        paths.add(pair("/Order/0/IssueTime/0/_", "/document/issueTime"));
        paths.add(pair("/Order/0/OrderTypeCode/0/_", "/document/typeCode"));
        paths.add(coded("/Order/0/DocumentCurrencyCode/0/_", "/document/currency", BusinessCodeList.CURRENCY));
        paths.add(pair("/Order/0/CustomerReference/0/_", "/document/customerReference"));
        paths.add(pair("/Order/0/Note/0/_", "/document/note"));
        paths.add(pair("/Order/0/SellerSupplierParty/0/Party/0/EndpointID/0/_", "/parties/sender"));
        paths.add(pair("/Order/0/BuyerCustomerParty/0/Party/0/EndpointID/0/_", "/parties/receiver"));
        paths.add(pair("/Order/0/BuyerCustomerParty/0/Party/0/PartyIdentification/0/ID/0/_", "/parties/buyerId"));
        paths.add(pair("/Order/0/BuyerCustomerParty/0/Party/0/PartyName/0/Name/0/_", "/parties/buyerName"));
        paths.add(pair("/Order/0/SellerSupplierParty/0/Party/0/PartyIdentification/0/ID/0/_", "/parties/supplierId"));
        paths.add(pair("/Order/0/SellerSupplierParty/0/Party/0/PartyName/0/Name/0/_", "/parties/supplierName"));
        paths.add(pair("/Order/0/BuyerCustomerParty/0/Party/0/PartyTaxScheme/0/CompanyID/0/_", "/parties/buyerTaxId"));
        paths.add(pair("/Order/0/SellerSupplierParty/0/Party/0/PartyTaxScheme/0/CompanyID/0/_", "/parties/supplierTaxId"));
        paths.add(pair("/Order/0/Delivery/0/ID/0/_", "/delivery/id"));
        paths.add(pair("/Order/0/Delivery/0/ActualDeliveryDate/0/_", "/delivery/requestedDate"));
        paths.add(pair("/Order/0/Delivery/0/LatestDeliveryDate/0/_", "/delivery/latestDate"));
        paths.add(pair("/Order/0/Delivery/0/DeliveryLocation/0/ID/0/_", "/delivery/locationId"));
        paths.add(coded("/Order/0/Delivery/0/DeliveryLocation/0/Address/0/Country/0/IdentificationCode/0/_",
                "/delivery/country", BusinessCodeList.COUNTRY));
        paths.add(pair("/Order/0/DeliveryTerms/0/ID/0/_", "/delivery/terms"));
        paths.add(pair("/Order/0/PaymentTerms/0/ID/0/_", "/payment/termsId"));
        paths.add(pair("/Order/0/PaymentTerms/0/PaymentDueDate/0/_", "/payment/dueDate"));
        paths.add(pair("/Order/0/AnticipatedMonetaryTotal/0/LineExtensionAmount/0/_", "/totals/lineExtension"));
        paths.add(pair("/Order/0/AnticipatedMonetaryTotal/0/AllowanceTotalAmount/0/_", "/totals/allowance"));
        paths.add(pair("/Order/0/AnticipatedMonetaryTotal/0/TaxExclusiveAmount/0/_", "/totals/taxExclusive"));
        paths.add(pair("/Order/0/AnticipatedMonetaryTotal/0/TaxInclusiveAmount/0/_", "/totals/taxInclusive"));
        paths.add(pair("/Order/0/AnticipatedMonetaryTotal/0/PayableAmount/0/_", "/totals/payable"));
        for (int index = 0; index < 8; index++) {
            paths.addAll(linePaths("/Order/0/OrderLine/" + index + "/LineItem/0", false, index));
        }
        return List.copyOf(paths);
    }

    private static List<PathPair> invoicePaths() {
        List<PathPair> paths = new ArrayList<>();
        paths.add(pair("/Invoice/0/ID/0/_", "/document/id"));
        paths.add(pair("/Invoice/0/IssueDate/0/_", "/document/issueDate"));
        paths.add(pair("/Invoice/0/DueDate/0/_", "/document/dueDate"));
        paths.add(pair("/Invoice/0/InvoiceTypeCode/0/_", "/document/typeCode"));
        paths.add(coded("/Invoice/0/DocumentCurrencyCode/0/_", "/document/currency", BusinessCodeList.CURRENCY));
        paths.add(coded("/Invoice/0/TaxCurrencyCode/0/_", "/document/taxCurrency", BusinessCodeList.CURRENCY));
        paths.add(pair("/Invoice/0/OrderReference/0/ID/0/_", "/document/orderReference"));
        paths.add(pair("/Invoice/0/Note/0/_", "/document/note"));
        paths.add(pair("/Invoice/0/AccountingSupplierParty/0/Party/0/EndpointID/0/_", "/parties/sender"));
        paths.add(pair("/Invoice/0/AccountingCustomerParty/0/Party/0/EndpointID/0/_", "/parties/receiver"));
        paths.add(pair("/Invoice/0/AccountingCustomerParty/0/Party/0/PartyIdentification/0/ID/0/_", "/parties/buyerId"));
        paths.add(pair("/Invoice/0/AccountingCustomerParty/0/Party/0/PartyName/0/Name/0/_", "/parties/buyerName"));
        paths.add(pair("/Invoice/0/AccountingSupplierParty/0/Party/0/PartyIdentification/0/ID/0/_", "/parties/supplierId"));
        paths.add(pair("/Invoice/0/AccountingSupplierParty/0/Party/0/PartyName/0/Name/0/_", "/parties/supplierName"));
        paths.add(pair("/Invoice/0/AccountingCustomerParty/0/Party/0/PartyTaxScheme/0/CompanyID/0/_", "/parties/buyerTaxId"));
        paths.add(pair("/Invoice/0/AccountingSupplierParty/0/Party/0/PartyTaxScheme/0/CompanyID/0/_", "/parties/supplierTaxId"));
        paths.add(pair("/Invoice/0/Delivery/0/ActualDeliveryDate/0/_", "/delivery/actualDate"));
        paths.add(pair("/Invoice/0/Delivery/0/DeliveryLocation/0/ID/0/_", "/delivery/locationId"));
        paths.add(coded("/Invoice/0/Delivery/0/DeliveryLocation/0/Address/0/Country/0/IdentificationCode/0/_",
                "/delivery/country", BusinessCodeList.COUNTRY));
        paths.add(coded("/Invoice/0/PaymentMeans/0/PaymentMeansCode/0/_", "/payment/meansCode",
                BusinessCodeList.PAYMENT_MEANS));
        paths.add(pair("/Invoice/0/PaymentMeans/0/PaymentDueDate/0/_", "/payment/dueDate"));
        paths.add(pair("/Invoice/0/PaymentMeans/0/PaymentID/0/_", "/payment/reference"));
        paths.add(pair("/Invoice/0/PaymentTerms/0/ID/0/_", "/payment/termsId"));
        paths.add(pair("/Invoice/0/TaxTotal/0/TaxAmount/0/_", "/tax/amount"));
        paths.add(pair("/Invoice/0/TaxTotal/0/TaxSubtotal/0/TaxableAmount/0/_", "/tax/taxableAmount"));
        paths.add(coded("/Invoice/0/TaxTotal/0/TaxSubtotal/0/TaxCategory/0/ID/0/_", "/tax/category",
                BusinessCodeList.TAX_CATEGORY));
        paths.add(pair("/Invoice/0/TaxTotal/0/TaxSubtotal/0/TaxCategory/0/Percent/0/_", "/tax/percent"));
        paths.add(pair("/Invoice/0/TaxTotal/0/TaxSubtotal/0/TaxCategory/0/TaxScheme/0/ID/0/_", "/tax/scheme"));
        paths.add(pair("/Invoice/0/LegalMonetaryTotal/0/LineExtensionAmount/0/_", "/totals/lineExtension"));
        paths.add(pair("/Invoice/0/LegalMonetaryTotal/0/AllowanceTotalAmount/0/_", "/totals/allowance"));
        paths.add(pair("/Invoice/0/LegalMonetaryTotal/0/TaxExclusiveAmount/0/_", "/totals/taxExclusive"));
        paths.add(pair("/Invoice/0/LegalMonetaryTotal/0/TaxInclusiveAmount/0/_", "/totals/taxInclusive"));
        paths.add(pair("/Invoice/0/LegalMonetaryTotal/0/PayableAmount/0/_", "/totals/payable"));
        for (int index = 0; index < 8; index++) {
            paths.addAll(linePaths("/Invoice/0/InvoiceLine/" + index, true, index));
            paths.add(pair("/Invoice/0/InvoiceLine/" + index + "/OrderLineReference/0/LineID/0/_",
                    "/lines/" + index + "/orderLineId"));
        }
        return List.copyOf(paths);
    }

    private static List<PathPair> linePaths(String base, boolean invoice, int index) {
        String quantity = invoice ? "InvoicedQuantity" : "Quantity";
        String target = "/lines/" + index;
        return List.of(pair(base + "/ID/0/_", target + "/id"),
                pair(base + "/" + quantity + "/0/_", target + "/quantity"),
                coded(base + "/" + quantity + "/0/unitCode", target + "/unitCode",
                        BusinessCodeList.UNIT_OF_MEASURE),
                pair(base + "/LineExtensionAmount/0/_", target + "/lineExtension"),
                pair(base + "/Item/0/BuyersItemIdentification/0/ID/0/_", target + "/buyerItemId"),
                pair(base + "/Item/0/SellersItemIdentification/0/ID/0/_", target + "/sellerItemId"),
                pair(base + "/Item/0/StandardItemIdentification/0/ID/0/_", target + "/gtin"),
                pair(base + "/Item/0/Description/0/_", target + "/description"),
                pair(base + "/Item/0/Name/0/_", target + "/name"),
                pair(base + "/Price/0/PriceAmount/0/_", target + "/price"),
                coded(base + "/Item/0/ClassifiedTaxCategory/0/ID/0/_", target + "/taxCategory",
                        BusinessCodeList.TAX_CATEGORY),
                pair(base + "/Item/0/ClassifiedTaxCategory/0/Percent/0/_", target + "/taxPercent"));
    }

    private static PathPair pair(String source, String target) { return new PathPair(source, target); }
    private static PathPair coded(String source, String target, BusinessCodeList codeList) {
        return new PathPair(source, target, codeList);
    }
    private static UUID id(String value) { return UUID.fromString(value); }
    private static BusinessCodeSyntax syntax(UUID schemaId) {
        if (Set.of(UBL_ORDER, UBL_INVOICE, UBL_ORDER_RESPONSE, UBL_DESPATCH_ADVICE,
                UBL_REMITTANCE_ADVICE, UBL_RECEIPT_ADVICE).contains(schemaId)) return BusinessCodeSyntax.UBL_2_1;
        if (Set.of(EDIFACT_ORDER, EDIFACT_INVOICE, EDIFACT_ORDER_RESPONSE, EDIFACT_DESPATCH_ADVICE,
                EDIFACT_REMITTANCE_ADVICE, EDIFACT_RECEIPT_ADVICE).contains(schemaId)) return BusinessCodeSyntax.EDIFACT_D96A;
        if (Set.of(TRADACOMS_ORDER, TRADACOMS_INVOICE, TRADACOMS_ORDER_RESPONSE, TRADACOMS_DESPATCH_ADVICE,
                TRADACOMS_REMITTANCE_ADVICE, TRADACOMS_RECEIPT_ADVICE).contains(schemaId)) return BusinessCodeSyntax.TRADACOMS_9;
        throw new IllegalArgumentException("No business code syntax is registered for schema " + schemaId);
    }
    private static String resource(String name) {
        try {
            return new ClassPathResource("global-mappings/" + name).getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("Global mapping resource is missing: " + name, exception);
        }
    }
    private record PathPair(String source, String target, BusinessCodeList codeList) {
        private PathPair(String source, String target) { this(source, target, null); }
    }
    private record OperationalDocument(String label, String messageType, UUID ubl, UUID edifact,
                                       UUID tradacoms) { }
}
