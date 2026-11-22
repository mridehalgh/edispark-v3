package com.edispark.transformations.infrastructure;

import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingOperation;
import com.edispark.transformations.domain.MappingRevision;
import com.edispark.transformations.domain.MappingScope;
import com.edispark.transformations.domain.MappingStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tesco UK depot pilot coordinates and UBL projection. */
public final class TescoUkMappings {
    public static final UUID ORDER = id("62");
    public static final UUID INVOICE = id("63");
    public static final UUID DESPATCH = id("64");

    private TescoUkMappings() {}

    static List<MappingRevision> revisions() {
        return List.of(order(), invoice(), despatch());
    }

    private static MappingRevision order() {
        var mapping = new Builder(ORDER, "Tesco UK EAN008 ORDERS → UBL Order", "12", "10");
        String root = "/Order/0";
        mapping.constant("/_D", "urn:oasis:names:specification:ubl:schema:xsd:Order-2");
        mapping.constant("/_A", "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2");
        mapping.constant("/_B", "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2");
        mapping.constant(root + "/UBLVersionID/0/_", "2.1");
        mapping.copy("/document/id", root + "/ID/0/_");
        mapping.copy("/document/issueDate", root + "/IssueDate/0/_");
        mapping.copy("/document/currency", root + "/DocumentCurrencyCode/0/_");
        mapping.copy("/document/contractReference", root + "/CustomerReference/0/_");
        mapping.copy("/parties/buyer", root + "/BuyerCustomerParty/0/Party/0/EndpointID/0/_");
        mapping.copy("/parties/supplier", root + "/SellerSupplierParty/0/Party/0/EndpointID/0/_");
        mapping.copy("/delivery/depot", root + "/Delivery/0/DeliveryLocation/0/ID/0/_");
        mapping.copy("/delivery/date", root + "/Delivery/0/RequestedDeliveryPeriod/0/EndDate/0/_");
        mapping.copy("/delivery/time", root + "/Delivery/0/RequestedDeliveryPeriod/0/EndTime/0/_");
        String line = root + "/OrderLine/*/LineItem/0";
        mapping.copy("/lines/*/id", line + "/ID/0/_");
        mapping.copy("/lines/*/gtin", line + "/Item/0/StandardItemIdentification/0/ID/0/_");
        mapping.copy("/lines/*/buyerItemId", line + "/Item/0/BuyersItemIdentification/0/ID/0/_");
        mapping.copy("/lines/*/name", line + "/Item/0/Name/0/_");
        mapping.copy("/lines/*/quantity", line + "/Quantity/0/_");
        mapping.copy("/lines/*/unitCode", line + "/Quantity/0/unitCode");
        return mapping.build();
    }

    private static MappingRevision invoice() {
        var mapping = new Builder(INVOICE, "Tesco UK UBL Invoice → EAN008 INVOIC", "11", "13");
        String root = "/Invoice/0";
        mapping.outboundHeader(root, "AccountingSupplierParty", "AccountingCustomerParty");
        mapping.copy(root + "/DocumentCurrencyCode/0/_", "/document/currency");
        mapping.copy(root + "/TaxPointDate/0/_", "/document/taxPointDate");
        mapping.copy(root + "/InvoiceTypeCode/0/_", "/document/type");
        mapping.copy(root + "/Delivery/0/ActualDeliveryDate/0/_", "/document/deliveryDate");
        mapping.copy(root + "/Delivery/0/DeliveryLocation/0/ID/0/_", "/delivery/depot");
        mapping.copy(root + "/AccountingSupplierParty/0/Party/0/PartyTaxScheme/0/CompanyID/0/_", "/parties/vatNumber");
        mapping.copy(root + "/AccountingSupplierParty/0/Party/0/PartyTaxScheme/0/TaxScheme/0/ID/0/_",
                "/parties/taxScheme");
        mapping.copy(root + "/TaxTotal/0/TaxSubtotal/*/TaxCategory/0/TaxScheme/0/ID/0/_", "/vat/*/taxScheme");
        mapping.copy(root + "/TaxTotal/0/TaxSubtotal/*/TaxCategory/0/ID/0/_", "/vat/*/category");
        mapping.copy(root + "/LegalMonetaryTotal/0/LineExtensionAmount/0/currencyID", "/totals/netCurrency");
        mapping.copy(root + "/LegalMonetaryTotal/0/TaxExclusiveAmount/0/currencyID", "/totals/taxExclusiveCurrency");
        mapping.copy(root + "/LegalMonetaryTotal/0/TaxInclusiveAmount/0/currencyID", "/totals/taxInclusiveCurrency");
        mapping.copy(root + "/LegalMonetaryTotal/0/PayableAmount/0/currencyID", "/totals/payableCurrency");
        mapping.copy(root + "/TaxTotal/0/TaxAmount/0/currencyID", "/totals/taxCurrency");
        mapping.copy(root + "/TaxTotal/0/TaxSubtotal/*/TaxableAmount/0/currencyID", "/vat/*/netCurrency");
        mapping.copy(root + "/TaxTotal/0/TaxSubtotal/*/TaxAmount/0/currencyID", "/vat/*/taxCurrency");
        String line = root + "/InvoiceLine/*";
        mapping.line(line, "/lines/*", "InvoicedQuantity");
        mapping.copy(line + "/Price/0/PriceAmount/0/_", "/lines/*/price");
        mapping.copy(line + "/Price/0/PriceAmount/0/currencyID", "/lines/*/priceCurrency");
        mapping.copy(line + "/LineExtensionAmount/0/currencyID", "/lines/*/netCurrency");
        mapping.copy(line + "/LineExtensionAmount/0/_", "/lines/*/net");
        mapping.copy(line + "/Item/0/ClassifiedTaxCategory/0/Percent/0/_", "/lines/*/vatRate");
        mapping.copy(line + "/Item/0/ClassifiedTaxCategory/0/ID/0/_", "/lines/*/vatCategory");
        mapping.copy(line + "/Item/0/ClassifiedTaxCategory/0/TaxScheme/0/ID/0/_", "/lines/*/taxScheme");
        String tax = root + "/TaxTotal/0/TaxSubtotal/*";
        mapping.copy(tax + "/TaxableAmount/0/_", "/vat/*/net");
        mapping.copy(tax + "/TaxAmount/0/_", "/vat/*/tax");
        mapping.copy(tax + "/TaxCategory/0/Percent/0/_", "/vat/*/rate");
        mapping.copy(root + "/TaxTotal/0/TaxAmount/0/_", "/totals/tax");
        mapping.copy(root + "/LegalMonetaryTotal/0/LineExtensionAmount/0/_", "/totals/net");
        mapping.copy(root + "/LegalMonetaryTotal/0/TaxExclusiveAmount/0/_", "/totals/taxExclusive");
        mapping.copy(root + "/LegalMonetaryTotal/0/TaxInclusiveAmount/0/_", "/totals/taxInclusive");
        mapping.copy(root + "/LegalMonetaryTotal/0/PayableAmount/0/_", "/totals/payable");
        return mapping.build();
    }

    private static MappingRevision despatch() {
        var mapping = new Builder(DESPATCH, "Tesco UK UBL Despatch Advice → DESAD3 depot ASN", "17", "31");
        String root = "/DespatchAdvice/0";
        String delivery = root + "/Shipment/0/Delivery/0";
        mapping.outboundHeader(root, "DespatchSupplierParty", "DeliveryCustomerParty");
        mapping.copy(root + "/IssueTime/0/_", "/document/issueTime");
        mapping.copy(root + "/Shipment/0/ID/0/_", "/document/shipmentId");
        mapping.copy(delivery + "/DeliveryLocation/0/ID/0/_", "/delivery/depot");
        mapping.copy(delivery + "/EstimatedDeliveryPeriod/0/StartDate/0/_", "/delivery/date");
        mapping.copy(delivery + "/EstimatedDeliveryPeriod/0/StartTime/0/_", "/delivery/time");
        mapping.copy(root + "/Shipment/0/ShipmentStage/0/CarrierParty/0/EndpointID/0/_", "/parties/carrier");
        mapping.copy(root + "/Shipment/0/ShipmentStage/0/CarrierParty/0/PartyName/0/Name/0/_", "/parties/carrierName");
        mapping.copy(root + "/DespatchSupplierParty/0/Party/0/PartyName/0/Name/0/_", "/parties/supplierName");
        String pallet = root + "/Shipment/0/TransportHandlingUnit/*";
        mapping.copy(pallet + "/TraceID/0/_", "/pallets/*/sscc");
        mapping.omit(pallet + "/ID", "The DESAD3 pallet identity comes from the SSCC in TraceID.");
        mapping.copy(pallet + "/TransportHandlingUnitTypeCode/0/_", "/pallets/*/type");
        mapping.copy(pallet + "/HandlingUnitDespatchLine", "/pallets/*/allocations");
        String line = root + "/DespatchLine/*";
        mapping.line(line, "/lines/*", "DeliveredQuantity");
        mapping.copy(line + "/Item/0/ItemInstance/0/BestBeforeDate/0/_", "/lines/*/bestBefore");
        mapping.copy(line + "/Item/0/AdditionalItemProperty/0/Value/0/_", "/lines/*/packaging");
        mapping.copy(line + "/Item/0/AdditionalItemProperty/0/Name/0/_", "/lines/*/packagingProperty");
        return mapping.build();
    }

    private static UUID id(String suffix) {
        return UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac" + suffix);
    }

    private static final class Builder {
        private final UUID id;
        private final String name;
        private final UUID source;
        private final UUID target;
        private final List<MappingNode> nodes = new ArrayList<>();

        private Builder(UUID id, String name, String source, String target) {
            this.id = id;
            this.name = name;
            this.source = TescoUkMappings.id(source);
            this.target = TescoUkMappings.id(target);
        }

        private void outboundHeader(String root, String supplier, String buyer) {
            omit(root + "/UBLVersionID", "UBL serialization metadata is outside the EDIFACT business document.");
            omit(root + "/" + supplier + "/0/Party/0/EndpointID/0/schemeID", "The profile uses GLN agency 9.");
            omit(root + "/" + buyer + "/0/Party/0/EndpointID/0/schemeID", "The profile uses GLN agency 9.");
            copy(root + "/ID/0/_", "/document/id");
            copy(root + "/IssueDate/0/_", "/document/issueDate");
            copy(root + "/OrderReference/0/ID/0/_", "/document/orderReference");
            copy(root + "/" + supplier + "/0/Party/0/EndpointID/0/_", "/parties/supplier");
            copy(root + "/" + buyer + "/0/Party/0/EndpointID/0/_", "/parties/buyer");
        }

        private void line(String source, String target, String quantity) {
            copy(source + "/ID/0/_", target + "/id");
            copy(source + "/OrderLineReference/0/LineID/0/_", target + "/orderLineId");
            copy(source + "/Item/0/StandardItemIdentification/0/ID/0/_", target + "/gtin");
            copy(source + "/" + quantity + "/0/_", target + "/quantity");
            copy(source + "/" + quantity + "/0/unitCode", target + "/unitCode");
        }

        private void copy(String source, String target) {
            nodes.add(new MappingNode(nodeId(target), MappingOperation.OPTIONAL_COPY, List.of(source), target, null, null));
        }

        private void omit(String source, String reason) {
            nodes.add(new MappingNode(nodeId(source), MappingOperation.OMIT, List.of(source), "/omitted/" + nodeId(source), reason, null));
        }

        private void constant(String target, String value) {
            nodes.add(new MappingNode(nodeId(target), MappingOperation.CONSTANT, List.of(), target, value, null));
        }

        private UUID nodeId(String path) {
            return UUID.nameUUIDFromBytes((id + path).getBytes(StandardCharsets.UTF_8));
        }

        private MappingRevision build() {
            Instant release = Instant.parse("2026-11-22T00:00:00Z");
            return new MappingRevision(id, ClasspathGlobalMappingCatalog.GLOBAL_OWNER, name, 1, source, target,
                    MappingStatus.PUBLISHED, nodes, "edispark-tesco-uk-v1-" + id, release, release, id, null, null,
                    1, MappingScope.GLOBAL, ClasspathGlobalMappingCatalog.VENDOR, null, null);
        }
    }
}
