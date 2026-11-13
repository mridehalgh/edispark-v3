package com.edispark.edifact;

import com.edispark.edifact.OrderResponseValidation.Issue;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** D96A core subset. Partner-specific certification is a separate qualification. */
public final class OrderResponseD96a {
    public static final String RULES = "UN-D96A-ORDRSP-CORE/1";
    public static final int MAX_LINES = 10000;

    /** Invalidates retained qualification when either model or byte validation changes. */
    public static String fingerprint() {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var types = java.util.stream.Stream.of(OrderResponse.class, OrderResponseD96a.class, OrderResponseByteValidator.class)
                    .flatMap(type -> java.util.stream.Stream.concat(java.util.stream.Stream.of(type), java.util.Arrays.stream(type.getDeclaredClasses())))
                    .sorted(java.util.Comparator.comparing(Class::getName)).toList();
            for (Class<?> type : types) {
                try (var bytes = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                    if (bytes == null) throw new IllegalStateException("Target rules are unavailable");
                    digest.update(bytes.readAllBytes());
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Target rules are unavailable", exception);
        }
    }
    private static final Set<String> ACTIONS = Set.of("4", "5", "6", "7");
    private static final Set<String> UNITS = Set.of("EA", "C62", "H87", "KGM", "GRM", "TNE", "LBR", "ONZ", "LTR", "MLT", "MTR", "CMT", "MTK", "MTQ", "DAY", "HUR", "MIN", "SET", "PR", "BX", "PK");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd:HHmm").withZone(ZoneOffset.UTC);

    public OrderResponseValidation validate(OrderResponse model) {
        List<Issue> issues = new ArrayList<>();
        if (model == null || model.document() == null || model.parties() == null) {
            return new OrderResponseValidation(List.of(new Issue("TARGET_REQUIRED", "/", "Add the target document and party details.")));
        }
        if (!OrderResponse.CONTRACT.equals(model.contract())) issue(issues, "TARGET_CONTRACT_UNAVAILABLE", "/contract", "Select the supported Order Response contract.");
        if (present(model.sourceVersion()) && !model.sourceVersion().equals("2.1")) issue(issues, "SOURCE_VERSION_UNSUPPORTED", "/sourceVersion", "This mapping supports UBL 2.1 only.");
        validateHeader(model.document(), issues);
        validateParties(model.parties(), issues);
        validateLines(model, issues);
        if (model.delivery() != null && model.delivery().country() != null
                && !Set.of(Locale.getISOCountries()).contains(model.delivery().country())) {
            issue(issues, "TARGET_COUNTRY", "/delivery/country", "Use a supported ISO country code.");
        }
        return new OrderResponseValidation(issues);
    }

    public String write(OrderResponse model, String control, Instant at) {
        validate(model).requireValid();
        if (control == null || !control.matches("[A-Za-z0-9]{1,14}")) throw new IllegalArgumentException("Invalid interchange control reference");
        var document = model.document(); var parties = model.parties();
        List<String> message = new ArrayList<>();
        message.add("UNH+1+ORDRSP:D:96A:UN");
        message.add("BGM+231+" + escape(document.id()) + "+" + document.function());
        message.add("DTM+137:" + document.issueDate().replace("-", "") + ":102");
        if (present(document.note())) message.add("FTX+AAI+++" + escape(document.note()));
        message.add("RFF+ON:" + escape(document.orderReference()));
        message.add(party("SU", parties.sender(), parties.senderAgency(), parties.senderLegalName(), parties.senderName()));
        message.add(party("BY", parties.receiver(), parties.receiverAgency(), parties.receiverLegalName(), parties.receiverName()));
        if (model.delivery() != null && present(model.delivery().country())) message.add("NAD+DP++++++++" + model.delivery().country());
        if (present(document.currency())) message.add("CUX+2:" + document.currency() + ":9");
        model.lines().forEach(line -> writeLine(message, line));
        message.add("UNS+S");
        message.add("CNT+2:" + model.lines().size());
        message.add("UNT+" + (message.size() + 1) + "+1");
        String bytes = "UNB+UNOC:3+" + escape(parties.sender()) + "+" + escape(parties.receiver()) + "+" + DATE.format(at) + "+" + control + "'"
                + String.join("'", message) + "'UNZ+1+" + control + "'";
        validateBytes(bytes).requireValid();
        return bytes;
    }

    public OrderResponseValidation validateBytes(String edi) { return new OrderResponseByteValidator().validate(edi); }

    private void validateHeader(OrderResponse.Header header, List<Issue> issues) {
        text(header.id(), "/document/id", 35, true, issues);
        text(header.orderReference(), "/document/orderReference", 35, true, issues);
        text(header.note(), "/document/note", 70, false, issues);
        if (!Set.of("12", "27", "29", "30").contains(header.function() == null ? "" : header.function())) issue(issues, "TARGET_RESPONSE_CODE", "/document/function", "Use a supported response meaning: receipt, acceptance, rejection or detail amendment.");
        try { LocalDate.parse(header.issueDate()); }
        catch (Exception exception) { issue(issues, "TARGET_DATE", "/document/issueDate", "Enter a valid issue date."); }
        if (present(header.currency())) {
            try { Currency.getInstance(header.currency()); }
            catch (IllegalArgumentException exception) { issue(issues, "TARGET_CURRENCY", "/document/currency", "Use a supported ISO currency code."); }
        }
    }

    private void validateParties(OrderResponse.Parties parties, List<Issue> issues) {
        text(parties.sender(), "/parties/sender", 35, true, issues);
        text(parties.receiver(), "/parties/receiver", 35, true, issues);
        if ("9".equals(parties.senderAgency()) && !gln(parties.sender())) issue(issues, "SOURCE_IDENTIFIER_INVALID", "/parties/sender", "A GS1 party identifier must be a 13-digit GLN with a valid check digit. Use the agreed identifier scheme for other identifiers.");
        if ("9".equals(parties.receiverAgency()) && !gln(parties.receiver())) issue(issues, "SOURCE_IDENTIFIER_INVALID", "/parties/receiver", "A GS1 party identifier must be a 13-digit GLN with a valid check digit. Use the agreed identifier scheme for other identifiers.");
        for (var field : List.of(new String[]{"senderAgency", value(parties.senderAgency())}, new String[]{"receiverAgency", value(parties.receiverAgency())})) {
            if (!Set.of("9", "91", "92").contains(field[1])) issue(issues, "TARGET_IDENTIFIER_SCHEME", "/parties/" + field[0], "Select a supported identifier scheme.");
        }
        text(parties.senderName(), "/parties/senderName", 35, false, issues);
        text(parties.senderLegalName(), "/parties/senderLegalName", 35, false, issues);
        text(parties.receiverName(), "/parties/receiverName", 35, false, issues);
        text(parties.receiverLegalName(), "/parties/receiverLegalName", 35, false, issues);
    }

    private void validateLines(OrderResponse model, List<Issue> issues) {
        if (model.lines() == null || model.lines().isEmpty() || model.lines().size() > MAX_LINES) {
            issue(issues, "TARGET_LINE_COUNT", "/lines", "Include between 1 and 10000 document lines."); return;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < model.lines().size(); index++) {
            var line = model.lines().get(index); String path = "/lines/" + index;
            if (line == null) { issue(issues, "TARGET_REQUIRED", path, "Supply this document line."); continue; }
            if (line.id() == null || !line.id().matches("[0-9]{1,6}") || !seen.add(line.id())) issue(issues, "TARGET_LINE_ID", path + "/id", "Line numbers must be unique numeric values of at most six digits.");
            if (!ACTIONS.contains(value(line.action()))) issue(issues, "TARGET_LINE_STATUS", path + "/action", "Select a supported line response status.");
            if (line.quantity() == null || line.quantity().signum() < 0 || line.quantity().stripTrailingZeros().toPlainString().length() > 15) issue(issues, "TARGET_QUANTITY", path + "/quantity", "Use a non-negative quantity with at most 15 characters.");
            if (!UNITS.contains(value(line.unitCode()))) issue(issues, "TARGET_UNIT", path + "/unitCode", "Select a unit supported by this target profile.");
            text(line.name(), path + "/name", 70, false, issues);
            text(line.description(), path + "/description", 70, false, issues);
            text(line.sellerItemId(), path + "/sellerItemId", 35, false, issues);
            text(line.buyerItemId(), path + "/buyerItemId", 35, false, issues);
            if (present(line.gtin()) && !line.gtin().matches("[0-9]{8,14}")) issue(issues, "TARGET_ITEM_ID", path + "/gtin", "Use a supported numeric standard item identifier.");
        }
        String expected = switch (value(model.document().function())) { case "12" -> "4"; case "27" -> "7"; case "29" -> "5"; default -> null; };
        if (expected != null && model.lines().stream().filter(java.util.Objects::nonNull).anyMatch(line -> !expected.equals(line.action()))) {
            issue(issues, "RESPONSE_STATUS_CONFLICT", "/document/function", "The overall response and line statuses disagree. Choose acknowledgement, acceptance or rejection consistently.");
        }
        if ("30".equals(model.document().function()) && model.lines().stream().filter(java.util.Objects::nonNull).noneMatch(line -> Set.of("6", "7").contains(value(line.action())))) {
            issue(issues, "RESPONSE_STATUS_CONFLICT", "/document/function", "A detail amendment must identify an amended or rejected line.");
        }
    }

    private void writeLine(List<String> output, OrderResponse.Line line) {
        output.add("LIN+" + line.id() + "+" + line.action() + (present(line.gtin()) ? "+" + line.gtin() + ":EN" : ""));
        if (present(line.sellerItemId())) output.add("PIA+1+" + escape(line.sellerItemId()) + ":SA");
        if (present(line.buyerItemId())) output.add("PIA+1+" + escape(line.buyerItemId()) + ":IN");
        description(output, line.name()); description(output, line.description());
        output.add("QTY+21:" + line.quantity().stripTrailingZeros().toPlainString() + ":" + line.unitCode());
    }

    private void description(List<String> output, String text) {
        if (!present(text)) return;
        output.add("IMD+F++:::" + escape(text.substring(0, Math.min(35, text.length())))
                + (text.length() > 35 ? ":" + escape(text.substring(35)) : ""));
    }

    private String party(String role, String id, String agency, String legal, String trading) {
        String names = present(legal) ? escape(legal) + (present(trading) ? ":" + escape(trading) : "") : escape(trading);
        return "NAD+" + role + "+" + escape(id) + "::" + agency + (names.isEmpty() ? "" : "++" + names);
    }

    private void text(String value, String path, int limit, boolean required, List<Issue> issues) {
        if (!present(value)) { if (required) issue(issues, "TARGET_REQUIRED", path, "Complete this required field."); return; }
        if (value.length() > limit || !value.matches("[\\x20-\\x7E]*")) issue(issues, "TARGET_REPRESENTATION", path, "This value exceeds the target length or character repertoire. It cannot be shortened or changed automatically.");
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    static boolean gln(String value) {
        if (value == null || !value.matches("[0-9]{13}")) return false;
        int sum = 0;
        for (int index = 0; index < value.length(); index++) sum += (value.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        return sum % 10 == 0;
    }
    private static String value(String value) { return value == null ? "" : value; }
    static String escape(String value) { return value(value).replace("?", "??").replace("+", "?+").replace(":", "?:").replace("'", "?'"); }
    private static void issue(List<Issue> target, String code, String path, String message) { target.add(new Issue(code, path, message)); }
}
