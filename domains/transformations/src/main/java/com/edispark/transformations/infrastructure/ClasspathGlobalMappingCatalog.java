package com.edispark.transformations.infrastructure;

import com.edispark.transformations.domain.GlobalMappingCatalog;
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

    private final List<MappingRevision> mappings;

    public ClasspathGlobalMappingCatalog() {
        String ublOrder = resource("ubl-order-2.1.json");
        String ublInvoice = resource("ubl-invoice-2.1.json");
        String orderModel = resource("order-edi-model.json");
        String invoiceModel = resource("invoice-edi-model.json");
        mappings = List.of(
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
                                "UBL Invoice 2.1 JSON", ublInvoice, null)));
    }

    @Override public List<MappingRevision> list() { return mappings; }
    @Override public Optional<MappingRevision> find(UUID id) {
        return mappings.stream().filter(mapping -> mapping.id().equals(id)).findFirst();
    }

    private MappingRevision mapping(String value, String name, UUID source, UUID target,
            List<PathPair> paths, boolean reverse, MappingSample sample) {
        UUID mappingId = id(value);
        List<MappingNode> nodes = paths.stream().map(path -> node(mappingId,
                reverse ? path.target() : path.source(), reverse ? path.source() : path.target())).toList();
        return new MappingRevision(mappingId, GLOBAL_OWNER, name, 1, source, target, MappingStatus.PUBLISHED,
                nodes, "edispark-global-v1-" + mappingId, RELEASED_AT, RELEASED_AT, mappingId, null, null, 1,
                MappingScope.GLOBAL, VENDOR, sample, null);
    }

    private MappingNode node(UUID mappingId, String source, String target) {
        UUID nodeId = UUID.nameUUIDFromBytes((mappingId + ":" + source + ":" + target)
                .getBytes(StandardCharsets.UTF_8));
        return new MappingNode(nodeId, MappingOperation.COPY, List.of(source), target, null, null);
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
        paths.add(pair("/Order/0/DocumentCurrencyCode/0/_", "/document/currency"));
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
        paths.add(pair("/Order/0/Delivery/0/DeliveryLocation/0/Address/0/Country/0/IdentificationCode/0/_", "/delivery/country"));
        paths.add(pair("/Order/0/DeliveryTerms/0/ID/0/_", "/delivery/terms"));
        paths.add(pair("/Order/0/PaymentTerms/0/ID/0/_", "/payment/termsId"));
        paths.add(pair("/Order/0/PaymentTerms/0/PaymentDueDate/0/_", "/payment/dueDate"));
        paths.add(pair("/Order/0/AnticipatedMonetaryTotal/0/LineExtensionAmount/0/_", "/totals/lineExtension"));
        paths.add(pair("/Order/0/AnticipatedMonetaryTotal/0/AllowanceTotalAmount/0/_", "/totals/allowance"));
        paths.add(pair("/Order/0/AnticipatedMonetaryTotal/0/TaxExclusiveAmount/0/_", "/totals/taxExclusive"));
        paths.add(pair("/Order/0/AnticipatedMonetaryTotal/0/TaxInclusiveAmount/0/_", "/totals/taxInclusive"));
        paths.add(pair("/Order/0/AnticipatedMonetaryTotal/0/PayableAmount/0/_", "/totals/payable"));
        paths.addAll(linePaths("/Order/0/OrderLine/0/LineItem/0", false));
        return List.copyOf(paths);
    }

    private static List<PathPair> invoicePaths() {
        List<PathPair> paths = new ArrayList<>();
        paths.add(pair("/Invoice/0/ID/0/_", "/document/id"));
        paths.add(pair("/Invoice/0/IssueDate/0/_", "/document/issueDate"));
        paths.add(pair("/Invoice/0/DueDate/0/_", "/document/dueDate"));
        paths.add(pair("/Invoice/0/InvoiceTypeCode/0/_", "/document/typeCode"));
        paths.add(pair("/Invoice/0/DocumentCurrencyCode/0/_", "/document/currency"));
        paths.add(pair("/Invoice/0/TaxCurrencyCode/0/_", "/document/taxCurrency"));
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
        paths.add(pair("/Invoice/0/Delivery/0/DeliveryLocation/0/Address/0/Country/0/IdentificationCode/0/_", "/delivery/country"));
        paths.add(pair("/Invoice/0/PaymentMeans/0/PaymentMeansCode/0/_", "/payment/meansCode"));
        paths.add(pair("/Invoice/0/PaymentMeans/0/PaymentDueDate/0/_", "/payment/dueDate"));
        paths.add(pair("/Invoice/0/PaymentMeans/0/PaymentID/0/_", "/payment/reference"));
        paths.add(pair("/Invoice/0/PaymentTerms/0/ID/0/_", "/payment/termsId"));
        paths.add(pair("/Invoice/0/TaxTotal/0/TaxAmount/0/_", "/tax/amount"));
        paths.add(pair("/Invoice/0/TaxTotal/0/TaxSubtotal/0/TaxableAmount/0/_", "/tax/taxableAmount"));
        paths.add(pair("/Invoice/0/TaxTotal/0/TaxSubtotal/0/TaxCategory/0/ID/0/_", "/tax/category"));
        paths.add(pair("/Invoice/0/TaxTotal/0/TaxSubtotal/0/TaxCategory/0/Percent/0/_", "/tax/percent"));
        paths.add(pair("/Invoice/0/TaxTotal/0/TaxSubtotal/0/TaxCategory/0/TaxScheme/0/ID/0/_", "/tax/scheme"));
        paths.add(pair("/Invoice/0/LegalMonetaryTotal/0/LineExtensionAmount/0/_", "/totals/lineExtension"));
        paths.add(pair("/Invoice/0/LegalMonetaryTotal/0/AllowanceTotalAmount/0/_", "/totals/allowance"));
        paths.add(pair("/Invoice/0/LegalMonetaryTotal/0/TaxExclusiveAmount/0/_", "/totals/taxExclusive"));
        paths.add(pair("/Invoice/0/LegalMonetaryTotal/0/TaxInclusiveAmount/0/_", "/totals/taxInclusive"));
        paths.add(pair("/Invoice/0/LegalMonetaryTotal/0/PayableAmount/0/_", "/totals/payable"));
        paths.addAll(linePaths("/Invoice/0/InvoiceLine/0", true));
        paths.add(pair("/Invoice/0/InvoiceLine/0/OrderLineReference/0/LineID/0/_", "/lines/0/orderLineId"));
        return List.copyOf(paths);
    }

    private static List<PathPair> linePaths(String base, boolean invoice) {
        String quantity = invoice ? "InvoicedQuantity" : "Quantity";
        return List.of(pair(base + "/ID/0/_", "/lines/0/id"),
                pair(base + "/" + quantity + "/0/_", "/lines/0/quantity"),
                pair(base + "/" + quantity + "/0/unitCode", "/lines/0/unitCode"),
                pair(base + "/LineExtensionAmount/0/_", "/lines/0/lineExtension"),
                pair(base + "/Item/0/BuyersItemIdentification/0/ID/0/_", "/lines/0/buyerItemId"),
                pair(base + "/Item/0/SellersItemIdentification/0/ID/0/_", "/lines/0/sellerItemId"),
                pair(base + "/Item/0/StandardItemIdentification/0/ID/0/_", "/lines/0/gtin"),
                pair(base + "/Item/0/Description/0/_", "/lines/0/description"),
                pair(base + "/Item/0/Name/0/_", "/lines/0/name"),
                pair(base + "/Price/0/PriceAmount/0/_", "/lines/0/price"),
                pair(base + "/Item/0/ClassifiedTaxCategory/0/ID/0/_", "/lines/0/taxCategory"),
                pair(base + "/Item/0/ClassifiedTaxCategory/0/Percent/0/_", "/lines/0/taxPercent"));
    }

    private static PathPair pair(String source, String target) { return new PathPair(source, target); }
    private static UUID id(String value) { return UUID.fromString(value); }
    private static String resource(String name) {
        try {
            return new ClassPathResource("global-mappings/" + name).getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("Global mapping resource is missing: " + name, exception);
        }
    }
    private record PathPair(String source, String target) { }
}
