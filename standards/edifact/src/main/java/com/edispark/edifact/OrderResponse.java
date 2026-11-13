package com.edispark.edifact;

import java.math.BigDecimal;
import java.util.List;

/** Intermediate contract for the explicitly supported D96A Order Response subset. */
public record OrderResponse(String contract, Header document, Parties parties, Delivery delivery, List<Line> lines, String sourceVersion) {
    public static final String CONTRACT = "edispark.ordrsp.d96a.core.v1";
    public OrderResponse(String contract, Header document, Parties parties, Delivery delivery, List<Line> lines) {
        this(contract, document, parties, delivery, lines, null);
    }
    public record Header(String id, String issueDate, String orderReference, String function, String currency, String note) {}
    public record Parties(String sender, String receiver, String senderAgency, String receiverAgency,
            String senderName, String senderLegalName, String receiverName, String receiverLegalName) {}
    public record Delivery(String country) {}
    public record Line(String id, String action, BigDecimal quantity, String unitCode, String name,
            String description, String sellerItemId, String buyerItemId, String gtin) {}
}
