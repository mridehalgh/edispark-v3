package com.edispark.transformations.infrastructure;

import com.edispark.transformations.domain.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** New immutable core mapping; v1 remains available to explain historical exchanges. */
final class OrderResponseCoreMapping {
    static final UUID ID = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac60");
    private static final String ROOT = "/OrderResponse/0";

    static MappingRevision create(MappingSample sample) {
        var nodes = new ArrayList<MappingNode>();
        nodes.add(new MappingNode(nodeId("contract"), MappingOperation.CONSTANT, List.of(), "/contract", "edispark.ordrsp.d96a.core.v1", null));
        copy(nodes, "/UBLVersionID/0/_", "/sourceVersion", false);
        copy(nodes, "/ID/0/_", "/document/id", true);
        copy(nodes, "/IssueDate/0/_", "/document/issueDate", true);
        copy(nodes, "/OrderReference/0/ID/0/_", "/document/orderReference", true);
        copy(nodes, "/DocumentCurrencyCode/0/_", "/document/currency", false);
        copy(nodes, "/Note/0/_", "/document/note", false);
        lookup(nodes, "/OrderResponseCode/0/_", "/document/function", Map.of("AB", "12", "AP", "29", "RE", "27", "CA", "30"));
        party(nodes, "SellerSupplierParty", "sender");
        party(nodes, "BuyerCustomerParty", "receiver");
        copy(nodes, "/Delivery/0/DeliveryAddress/0/Country/0/IdentificationCode/0/_", "/delivery/country", false);
        String line = "/OrderLine/*/LineItem/0";
        copy(nodes, line + "/ID/0/_", "/lines/*/id", true);
        copy(nodes, line + "/Quantity/0/_", "/lines/*/quantity", true);
        copy(nodes, line + "/Quantity/0/unitCode", "/lines/*/unitCode", true);
        lookup(nodes, line + "/LineStatusCode/0/_", "/lines/*/action", Map.of("Accepted", "5", "Rejected", "7", "Acknowledged", "4", "Amended", "6",
                "ACCEPTED", "5", "REJECTED", "7", "ACKNOWLEDGED", "4", "AMENDED", "6"));
        copy(nodes, line + "/Item/0/Name/0/_", "/lines/*/name", false);
        copy(nodes, line + "/Item/0/Description/0/_", "/lines/*/description", false);
        copy(nodes, line + "/Item/0/SellersItemIdentification/0/ID/0/_", "/lines/*/sellerItemId", false);
        copy(nodes, line + "/Item/0/BuyersItemIdentification/0/ID/0/_", "/lines/*/buyerItemId", false);
        copy(nodes, line + "/Item/0/StandardItemIdentification/0/ID/0/_", "/lines/*/gtin", false);
        Instant released = Instant.parse("2026-11-09T00:00:00Z");
        return new MappingRevision(ID, ClasspathGlobalMappingCatalog.GLOBAL_OWNER,
                "UBL Order Response 2.1 → EDIFACT ORDRSP D96A · core demo v2", 2,
                UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac16"), UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac30"),
                MappingStatus.PUBLISHED, nodes, hash(nodes), released, released,
                UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac40"), null, null, 1,
                MappingScope.GLOBAL, ClasspathGlobalMappingCatalog.VENDOR, sample, null);
    }

    private static void party(List<MappingNode> nodes, String role, String target) {
        String base = "/" + role + "/0/Party/0";
        copy(nodes, base + "/EndpointID/0/_", "/parties/" + target, true);
        lookup(nodes, base + "/EndpointID/0/schemeID", "/parties/" + target + "Agency", Map.of("GLN", "9", "0088", "9", "ZZZ", "91", "SELLER", "91", "BUYER", "92"));
        copy(nodes, base + "/PartyName/0/Name/0/_", "/parties/" + target + "Name", false);
        copy(nodes, base + "/PartyLegalEntity/0/RegistrationName/0/_", "/parties/" + target + "LegalName", false);
    }

    private static void copy(List<MappingNode> nodes, String source, String target, boolean required) {
        nodes.add(new MappingNode(nodeId(target), required ? MappingOperation.COPY : MappingOperation.OPTIONAL_COPY,
                List.of(ROOT + source), target, null, null));
    }

    private static void lookup(List<MappingNode> nodes, String source, String target, Map<String, String> codes) {
        nodes.add(new MappingNode(nodeId(target), MappingOperation.CODE_LOOKUP, List.of(ROOT + source), target, null, null, codes));
    }

    private static UUID nodeId(String path) { return UUID.nameUUIDFromBytes((ID + path).getBytes(StandardCharsets.UTF_8)); }
    private static String hash(List<MappingNode> nodes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(new com.fasterxml.jackson.databind.ObjectMapper().writer()
                        .with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(nodes))); }
        catch (Exception exception) { throw new IllegalStateException("Could not hash core mapping", exception); }
    }
}
