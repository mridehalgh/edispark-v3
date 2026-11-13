package com.edispark.tradacoms.ack;

import java.math.BigDecimal;
import java.util.List;

/** Full-acceptance subset of the B&Q published ACK technical specification v1.4. */
public record BqAcknowledgement(String contract, String sourceVersion, String decision, Supplier supplier,
        Customer customer, Order order, List<Line> lines) {
    public static final String CONTRACT = "edispark.bq.tradacoms.ack.v1_4";
    public record Supplier(String gln, String scheme, String vendorNumber, String tradingName, String legalName) { }
    public record Customer(String gln, String scheme, String name, String legalName) { }
    public record Order(String reference, String locationReference, String version, String deliveryDate,
            String versionReference, String deliveryName, Address address) {
        public Order(String reference, String locationReference, String version, String deliveryDate) {
            this(reference, locationReference, version, deliveryDate, reference, null, null);
        }
    }
    public record Address(String line1, String line2, String line3, String line4, String postcode) { }
    public record Line(String id, String decision, String gtin, String gtinScheme, BigDecimal quantity,
            String unitCode, String name, String description) { }
    public record Control(long transmission, int fileGeneration) { }
}
