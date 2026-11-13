package com.edispark.transformations.infrastructure;

import com.edispark.transformations.domain.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

final class BqAcknowledgementMapping {
    static final UUID ID = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac61");
    private static final String ROOT = "/OrderResponse/0";
    static MappingRevision create(MappingSample sample) {
        var nodes = new ArrayList<MappingNode>();
        nodes.add(new MappingNode(id("contract"), MappingOperation.CONSTANT, List.of(), "/contract", "edispark.bq.tradacoms.ack.v1_4", null));
        copy(nodes, "/UBLVersionID/0/_", "/sourceVersion");
        copy(nodes, "/OrderResponseCode/0/_", "/decision");
        copy(nodes, "/SellerSupplierParty/0/CustomerAssignedAccountID/0/_", "/supplier/vendorNumber");
        copy(nodes, "/OrderReference/0/ID/0/_", "/order/reference");
        copy(nodes, "/OrderReference/0/DocumentReference/0/ID/0/_", "/order/versionReference");
        copy(nodes, "/OrderReference/0/DocumentReference/0/VersionID/0/_", "/order/version");
        copy(nodes, "/Delivery/0/DeliveryLocation/0/ID/0/_", "/order/locationReference");
        copy(nodes, "/Delivery/0/PromisedDeliveryPeriod/0/StartDate/0/_", "/order/deliveryDate");
        copy(nodes, "/Delivery/0/DeliveryParty/0/PartyName/0/Name/0/_", "/order/deliveryName");
        copy(nodes, "/SellerSupplierParty/0/Party/0/EndpointID/0/_", "/supplier/gln");
        copy(nodes, "/SellerSupplierParty/0/Party/0/EndpointID/0/schemeID", "/supplier/scheme");
        copy(nodes, "/SellerSupplierParty/0/Party/0/PartyName/0/Name/0/_", "/supplier/tradingName");
        copy(nodes, "/SellerSupplierParty/0/Party/0/PartyLegalEntity/0/RegistrationName/0/_", "/supplier/legalName");
        copy(nodes, "/BuyerCustomerParty/0/Party/0/EndpointID/0/_", "/customer/gln");
        copy(nodes, "/BuyerCustomerParty/0/Party/0/EndpointID/0/schemeID", "/customer/scheme");
        copy(nodes, "/BuyerCustomerParty/0/Party/0/PartyName/0/Name/0/_", "/customer/name");
        copy(nodes, "/BuyerCustomerParty/0/Party/0/PartyLegalEntity/0/RegistrationName/0/_", "/customer/legalName");
        copy(nodes, "/Delivery/0/DeliveryAddress/0/StreetName/0/_", "/order/address/line1");
        copy(nodes, "/Delivery/0/DeliveryAddress/0/AdditionalStreetName/0/_", "/order/address/line2");
        copy(nodes, "/Delivery/0/DeliveryAddress/0/CityName/0/_", "/order/address/line3");
        copy(nodes, "/Delivery/0/DeliveryAddress/0/CountrySubentity/0/_", "/order/address/line4");
        copy(nodes, "/Delivery/0/DeliveryAddress/0/PostalZone/0/_", "/order/address/postcode");
        copy(nodes, "/OrderLine/*/LineItem/0/ID/0/_", "/lines/*/id");
        copy(nodes, "/OrderLine/*/LineItem/0/LineStatusCode/0/_", "/lines/*/decision");
        copy(nodes, "/OrderLine/*/LineItem/0/Quantity/0/_", "/lines/*/quantity");
        copy(nodes, "/OrderLine/*/LineItem/0/Quantity/0/unitCode", "/lines/*/unitCode");
        copy(nodes, "/OrderLine/*/LineItem/0/Item/0/StandardItemIdentification/0/ID/0/_", "/lines/*/gtin");
        copy(nodes, "/OrderLine/*/LineItem/0/Item/0/StandardItemIdentification/0/ID/0/schemeID", "/lines/*/gtinScheme");
        copy(nodes, "/OrderLine/*/LineItem/0/Item/0/Name/0/_", "/lines/*/name");
        copy(nodes, "/OrderLine/*/LineItem/0/Item/0/Description/0/_", "/lines/*/description");
        nodes.add(new MappingNode(id("omit0"), MappingOperation.OMIT, List.of(ROOT + "/ID/0/_"), "/omitted/0", "ACK identifies the original order and reserved sequential transmission; retain the source acknowledgement ID in the audit record.", null));
        nodes.add(new MappingNode(id("omit1"), MappingOperation.OMIT, List.of(ROOT + "/IssueDate/0/_"), "/omitted/1", "FIL records actual file creation, rather than source issue date; retain the original issue date in the source.", null));
        nodes.add(new MappingNode(id("omit2"), MappingOperation.OMIT, List.of(ROOT + "/DocumentCurrencyCode/0/_"), "/omitted/2", "This unpriced full-acceptance ACK retains the original order currency and terms.", null));
        nodes.add(new MappingNode(id("omit3"), MappingOperation.OMIT, List.of(ROOT + "/Note/0/_"), "/omitted/3", "This full-acceptance subset has no header narrative slot; retain the note with the source.", null));
        nodes.add(new MappingNode(id("omit4"), MappingOperation.OMIT, List.of(ROOT + "/Delivery/0/DeliveryAddress/0/Country/0/IdentificationCode/0/_"), "/omitted/4", "The B&Q CLO address has no country field; preserve country in the retained source.", null));
        nodes.add(new MappingNode(id("omit5"), MappingOperation.OMIT, List.of(ROOT + "/OrderLine/*/LineItem/0/Item/0/SellersItemIdentification/0/ID/0/_"), "/omitted/5", "B&Q requires the EAN in ALD and marks the supplier SKU slot NULL; retain the SKU in the source.", null));
        nodes.add(new MappingNode(id("omit6"), MappingOperation.OMIT, List.of(ROOT + "/OrderLine/*/LineItem/0/Item/0/BuyersItemIdentification/0/ID/0/_"), "/omitted/6", "B&Q requires EAN and marks the customer SKU slot NULL; retain this reference in the source.", null));
        Instant released = Instant.parse("2026-11-09T00:00:00Z");
        return new MappingRevision(ID, ClasspathGlobalMappingCatalog.GLOBAL_OWNER,
                "UBL Order Response 2.1 → B&Q TRADACOMS ACK v1.4 · full acceptance", 2,
                UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac16"), UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac34"),
                MappingStatus.PUBLISHED, nodes, hash(nodes), released, released,
                UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac41"), null, null, 1,
                MappingScope.GLOBAL, ClasspathGlobalMappingCatalog.VENDOR, sample, null);
    }
    private static void copy(List<MappingNode> nodes, String source, String target) {
        nodes.add(new MappingNode(id(target), MappingOperation.OPTIONAL_COPY, List.of(ROOT + source), target, null, null));
    }
    private static UUID id(String path) { return UUID.nameUUIDFromBytes((ID + path).getBytes(StandardCharsets.UTF_8)); }
    private static String hash(List<MappingNode> nodes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(new com.fasterxml.jackson.databind.ObjectMapper().writer()
                        .with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(nodes))); }
        catch (Exception exception) { throw new IllegalStateException("Could not hash B&Q mapping", exception); }
    }
}
