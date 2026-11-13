package com.edispark.tradacoms.ack;

import com.edispark.tradacoms.ack.BqAcknowledgementValidation.Issue;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Partner-specific encoding. TRADACOMS v9 does not imply an ACKHDR message version of 9. */
public final class BqAcknowledgementV14 {
    public static final String RULES = "BQ-TRADACOMS-ACK-1.4/FULL-ACCEPTANCE/1";
    public static final String BUYER_ANA = "5013546018962";
    public static final int MAX_LINES = 10000;
    private static final Set<String> UNITS = Set.of("EA", "C62", "H87", "KGM", "GRM", "LTR", "MLT", "MTR", "CMT", "MTK", "MTQ", "PR", "BX", "PK", "SET");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC);

    public BqAcknowledgementValidation validate(BqAcknowledgement model) {
        var issues = new ArrayList<Issue>();
        if (model == null || model.supplier() == null || model.customer() == null || model.order() == null) {
            return new BqAcknowledgementValidation(List.of(new Issue("TARGET_REQUIRED", "/", "Add the B&Q supplier, customer and order details.")));
        }
        if (!BqAcknowledgement.CONTRACT.equals(model.contract())) issue(issues, "TARGET_CONTRACT_UNAVAILABLE", "/contract", "Select the B&Q ACK v1.4 contract.");
        if (!"2.1".equals(model.sourceVersion())) issue(issues, "SOURCE_VERSION_UNSUPPORTED", "/sourceVersion", "This mapping supports UBL 2.1 only.");
        if (!"AP".equals(model.decision())) issue(issues, "BQ_ACK_DECISION_UNSUPPORTED", "/decision", "This B&Q profile supports full acceptance only. Changes or rejection require a separately qualified flow.");
        parties(model, issues); order(model.order(), issues); lines(model.lines(), issues);
        return new BqAcknowledgementValidation(issues);
    }

    private void parties(BqAcknowledgement model, List<Issue> issues) {
        var supplier = model.supplier(); var customer = model.customer();
        if (!gln(supplier.gln())) issue(issues, "SOURCE_IDENTIFIER_INVALID", "/supplier/gln", "Enter the supplier's 13-digit ANA/GLN with its valid check digit.");
        if (!BUYER_ANA.equals(customer.gln())) issue(issues, "BQ_BUYER_IDENTITY", "/customer/gln", "Use the B&Q ANA identifier required by this profile.");
        if (!Set.of("GLN", "0088").contains(value(supplier.scheme()))) issue(issues, "BQ_IDENTIFIER_SCHEME", "/supplier/scheme", "B&Q requires the supplier ANA/GLN, rather than a placeholder or supplier-assigned identifier.");
        if (!Set.of("GLN", "0088").contains(value(customer.scheme()))) issue(issues, "BQ_IDENTIFIER_SCHEME", "/customer/scheme", "B&Q requires its ANA/GLN identifier scheme.");
        if (supplier.vendorNumber() == null || !supplier.vendorNumber().matches("[0-9]{1,7}")) issue(issues, "BQ_VENDOR_NUMBER_REQUIRED", "/supplier/vendorNumber", "Enter the B&Q-assigned vendor number, up to seven digits.");
        text(supplier.tradingName(), "/supplier/tradingName", 35, true, issues);
        text(supplier.legalName(), "/supplier/legalName", 40, true, issues);
        if (!Set.of("B&Q LTD", "B&Q (IRELAND) LTD").contains(value(customer.name()))) issue(issues, "BQ_CUSTOMER_NAME", "/customer/name", "Use the B&Q legal entity named in the purchase order.");
        if (present(customer.legalName()) && !customer.legalName().equals(customer.name())) issue(issues, "BQ_CUSTOMER_NAME", "/customer/legalName", "The buyer legal and trading names must identify the same B&Q entity for this profile.");
    }

    private void order(BqAcknowledgement.Order order, List<Issue> issues) {
        text(order.reference(), "/order/reference", 17, true, issues);
        text(order.locationReference(), "/order/locationReference", 17, true, issues);
        text(order.version(), "/order/version", 40, true, issues);
        if (!java.util.Objects.equals(order.reference(), order.versionReference())) issue(issues, "BQ_ORDER_VERSION_REFERENCE", "/order/versionReference", "The purchase-order version must refer to the same order being acknowledged.");
        text(order.deliveryName(), "/order/deliveryName", 40, false, issues);
        if (order.address() != null) {
            text(order.address().line1(), "/order/address/line1", 35, false, issues);
            text(order.address().line2(), "/order/address/line2", 35, false, issues);
            text(order.address().line3(), "/order/address/line3", 35, false, issues);
            text(order.address().line4(), "/order/address/line4", 35, false, issues);
            text(order.address().postcode(), "/order/address/postcode", 8, false, issues);
        }
        try {
            int year = LocalDate.parse(order.deliveryDate()).getYear();
            if (year < 2000 || year > 2099) throw new IllegalArgumentException("Date is outside the supported two-digit-year window");
        }
        catch (Exception exception) { issue(issues, "BQ_DELIVERY_DATE_REQUIRED", "/order/deliveryDate", "Enter the promised delivery date confirmed for this purchase order."); }
    }

    private void lines(List<BqAcknowledgement.Line> lines, List<Issue> issues) {
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES) {
            issue(issues, "TARGET_LINE_COUNT", "/lines", "Include between 1 and 10000 acknowledged lines."); return;
        }
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index); String path = "/lines/" + index;
            if (line == null) { issue(issues, "TARGET_REQUIRED", path, "Complete this acknowledgement line."); continue; }
            text(line.id(), path + "/id", 40, true, issues);
            if (!ids.add(value(line.id()))) issue(issues, "BQ_LINE_REFERENCE_DUPLICATE", path + "/id", "Each purchase-order line must be acknowledged once.");
            if (!Set.of("Accepted", "ACCEPTED").contains(value(line.decision()))) issue(issues, "RESPONSE_STATUS_CONFLICT", path + "/decision", "Every line must be accepted without changes in this full-acceptance profile.");
            if (!gln(line.gtin())) issue(issues, "BQ_ITEM_EAN_REQUIRED", path + "/gtin", "Enter the ordered product's 13-digit EAN with its valid check digit.");
            if (present(line.gtinScheme()) && !Set.of("GTIN", "EAN", "EN", "0160").contains(line.gtinScheme())) issue(issues, "BQ_ITEM_SCHEME", path + "/gtinScheme", "Use the EAN/GTIN item identification scheme.");
            if (line.quantity() == null || line.quantity().signum() <= 0 || line.quantity().stripTrailingZeros().scale() > 0
                    || line.quantity().toPlainString().replaceFirst("\\.0+$", "").length() > 15) issue(issues, "BQ_WHOLE_QUANTITY_REQUIRED", path + "/quantity", "B&Q's traded-unit quantity must be a positive whole number of at most 15 digits.");
            if (!UNITS.contains(value(line.unitCode()))) issue(issues, "BQ_ORDER_UNIT_REQUIRED", path + "/unitCode", "Use the supported unit of measure from the purchase order.");
            text(line.name(), path + "/name", 40, false, issues);
            text(line.description(), path + "/description", 40, false, issues);
        }
    }

    public String write(BqAcknowledgement model, BqAcknowledgement.Control control, Instant at) {
        validate(model).requireValid();
        int year = at.atZone(ZoneOffset.UTC).getYear();
        if (year < 2000 || year > 2099) throw new IllegalArgumentException("Transmission date is outside the supported two-digit-year window");
        if (control == null || control.transmission() < 1 || control.transmission() > 99999999999999L
                || control.fileGeneration() < 1 || control.fileGeneration() > 9999) throw new IllegalArgumentException("A reserved sequential B&Q transmission and file reference is required.");
        var supplier = model.supplier(); var customer = model.customer(); var order = model.order();
        var segments = new ArrayList<String>();
        segments.add("STX=ANA:1+" + supplier.gln() + ":" + escape(supplier.tradingName())
                + "+" + BUYER_ANA + ":ACKNOWLEDGEMENT+" + DATE.format(at) + ":" + TIME.format(at)
                + "+" + control.transmission() + "++ACKHDR");
        segments.addAll(List.of("MHD=1+ACKHDR:4", "TYP=3120", "SDT=" + supplier.gln() + ":" + supplier.vendorNumber() + "+" + escape(supplier.legalName()),
                "CDT=" + BUYER_ANA + "+" + escape(customer.name()), "FIL=" + control.fileGeneration() + "+1+" + DATE.format(at), "MTR=6"));
        var detail = new ArrayList<String>(List.of("MHD=2+ACKMNT:4", location(order),
                "AOR=" + escape(order.reference()), "DNA=1++186:" + shortDate(order.deliveryDate()) + ":194:" + escape(order.version())));
        for (int index = 0; index < model.lines().size(); index++) line(detail, model.lines().get(index), index + 1, order.deliveryDate());
        detail.add("KTR=" + model.lines().size()); detail.add("MTR=" + (detail.size() + 1)); segments.addAll(detail);
        // ACKTLR follows the guide's complete file example; the trailer table transposes its last letters.
        segments.addAll(List.of("MHD=3+ACKTLR:4", "KFT=1", "MTR=3", "END=3"));
        String encoded = String.join("'", segments) + "'";
        validateBytes(encoded).requireValid(); return encoded;
    }

    private String location(BqAcknowledgement.Order order) {
        String result = "CLO=:" + escape(order.locationReference());
        if (present(order.deliveryName()) || order.address() != null) result += "+" + escape(order.deliveryName());
        if (order.address() != null) {
            var address = order.address();
            result += "+" + String.join(":", escape(address.line1()), escape(address.line2()), escape(address.line3()), escape(address.line4()), escape(address.postcode()));
        }
        return result;
    }

    private void line(List<String> result, BqAcknowledgement.Line line, int sequence, String deliveryDate) {
        String quantity = line.quantity().toBigIntegerExact().toString();
        String description = present(line.name()) || present(line.description())
                ? "+++" + escape(line.name()) + (present(line.description()) ? ":" + escape(line.description()) : "") : "";
        result.add("ALD=" + sequence + "+" + line.gtin() + "+++::" + line.unitCode() + "+" + quantity + description);
        result.add("AQD=" + sequence + "+1+++" + shortDate(deliveryDate) + "+" + quantity);
        result.add("DNB=" + sequence + "+1++043:" + escape(line.id()));
    }

    public BqAcknowledgementValidation validateBytes(String bytes) { return new BqAcknowledgementWireValidator().validate(bytes); }
    public static String fingerprint() {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var types = java.util.stream.Stream.of(BqAcknowledgement.class, BqAcknowledgementV14.class, BqAcknowledgementWireValidator.class)
                    .flatMap(type -> java.util.stream.Stream.concat(java.util.stream.Stream.of(type), java.util.Arrays.stream(type.getDeclaredClasses())))
                    .sorted(java.util.Comparator.comparing(Class::getName)).toList();
            for (Class<?> type : types) {
                try (var bytes = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                    if (bytes == null) throw new IllegalStateException("B&Q rules are unavailable");
                    digest.update(bytes.readAllBytes());
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("B&Q rules are unavailable", exception);
        }
    }
    private static String shortDate(String value) { return LocalDate.parse(value).format(DateTimeFormatter.ofPattern("yyMMdd")); }
    static String escape(String value) { return value(value).replace("?", "??").replace("+", "?+").replace(":", "?:").replace("=", "?=").replace("'", "?'"); }
    static boolean gln(String value) {
        if (value == null || !value.matches("[0-9]{13}")) return false;
        int sum = 0; for (int index = 0; index < 13; index++) sum += (value.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        return sum % 10 == 0;
    }
    private static void text(String value, String path, int limit, boolean required, List<Issue> issues) {
        if (!present(value)) { if (required) issue(issues, "TARGET_REQUIRED", path, "Complete this required B&Q field."); return; }
        if (value.length() > limit || !value.matches("[\\x20-\\x7E]*")) issue(issues, "TARGET_REPRESENTATION", path, "This value cannot be represented in B&Q ACK without shortening or changing it.");
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String value(String value) { return value == null ? "" : value; }
    private static void issue(List<Issue> issues, String code, String path, String message) { issues.add(new Issue(code, path, message)); }
}
