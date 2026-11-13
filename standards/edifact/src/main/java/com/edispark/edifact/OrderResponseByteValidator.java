package com.edispark.edifact;

import com.edispark.edifact.OrderResponseValidation.Issue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads final wire bytes independently of model serialization, without repairing invalid content. */
final class OrderResponseByteValidator {
    private static final Set<String> UNITS = Set.of("EA", "C62", "H87", "KGM", "GRM", "TNE", "LBR", "ONZ",
            "LTR", "MLT", "MTR", "CMT", "MTK", "MTQ", "DAY", "HUR", "MIN", "SET", "PR", "BX", "PK");

    OrderResponseValidation validate(String edi) {
        try { check(parse(edi)); return new OrderResponseValidation(List.of()); }
        catch (IllegalArgumentException | java.time.DateTimeException exception) {
            return new OrderResponseValidation(List.of(new Issue("TARGET_EDI_INVALID", "/",
                    "The encoded EDI does not satisfy its message grammar, field or control rules.")));
        }
    }

    private void check(List<Segment> segments) {
        require(segments.size() >= 11);
        envelope(segments);
        var cursor = new Cursor(segments, 2);
        var bgm = cursor.take("BGM"); require(bgm.fields().size() == 4 && bgm.element(1).equals("231"));
        bounded(bgm.element(2), 35);
        String function = bgm.element(3); require(Set.of("12", "27", "29", "30").contains(function));
        date(cursor.take("DTM"), "137");
        if (cursor.is("FTX")) text(cursor.take("FTX"));
        var reference = cursor.take("RFF"); require(reference.fields().size() == 2);
        var order = reference.components(1); require(order.size() == 2 && order.getFirst().equals("ON")); bounded(order.get(1), 35);
        parties(cursor);
        if (cursor.is("CUX")) currency(cursor.take("CUX"));
        lines(cursor, function);
        var unt = cursor.take("UNT"); require(Integer.parseInt(unt.element(1)) == segments.size() - 2);
        require(unt.fields().size() == 3 && unt.element(2).equals(segments.get(1).element(1)));
        cursor.take("UNZ"); require(cursor.finished());
    }

    private void envelope(List<Segment> segments) {
        var header = segments.getFirst(); var trailer = segments.getLast(); var message = segments.get(1);
        require(header.tag().equals("UNB") && header.fields().size() == 6 && header.element(1).equals("UNOC:3"));
        bounded(header.element(2), 35); bounded(header.element(3), 35);
        require(header.element(4).matches("[0-9]{6}:[0-9]{4}") && header.element(5).matches("[A-Za-z0-9]{1,14}"));
        LocalDate.parse("20" + header.element(4).substring(0, 6), DateTimeFormatter.BASIC_ISO_DATE);
        java.time.LocalTime.parse(header.element(4).substring(7), DateTimeFormatter.ofPattern("HHmm").withResolverStyle(java.time.format.ResolverStyle.STRICT));
        require(trailer.tag().equals("UNZ") && trailer.fields().size() == 3 && trailer.element(1).equals("1") && header.element(5).equals(trailer.element(2)));
        require(message.tag().equals("UNH") && message.fields().size() == 3 && message.element(1).matches("[A-Za-z0-9]{1,14}")
                && message.element(2).equals("ORDRSP:D:96A:UN"));
    }

    private void parties(Cursor cursor) {
        Set<String> roles = new HashSet<>();
        while (cursor.is("NAD")) {
            var party = cursor.take("NAD"); String role = party.element(1);
            require(Set.of("SU", "BY", "DP").contains(role) && roles.add(role));
            if (role.equals("DP")) { require(party.fields().size() == 10); require(Set.of(java.util.Locale.getISOCountries()).contains(party.element(9))); }
            else party(party);
        }
        require(roles.containsAll(Set.of("SU", "BY")));
    }

    private void party(Segment party) {
        require(party.fields().size() == 3 || party.fields().size() == 5);
        var identifier = party.components(2);
        require(identifier.size() == 3 && identifier.get(1).isEmpty() && Set.of("9", "91", "92").contains(identifier.get(2)));
        bounded(identifier.getFirst(), 35);
        if (identifier.get(2).equals("9")) require(OrderResponseD96a.gln(identifier.getFirst()));
        if (party.fields().size() == 5) {
            require(party.element(3).isEmpty()); var names = party.components(4);
            require(names.size() <= 5); names.forEach(name -> bounded(name, 35));
        }
    }

    private void currency(Segment currency) {
        require(currency.fields().size() == 2);
        var value = currency.components(1); require(value.size() == 3 && value.getFirst().equals("2") && value.get(2).equals("9"));
        java.util.Currency.getInstance(value.get(1));
    }

    private void lines(Cursor cursor, String function) {
        Set<String> ids = new HashSet<>(); boolean amended = false;
        while (cursor.is("LIN")) {
            var line = cursor.take("LIN");
            require(line.fields().size() == 3 || line.fields().size() == 4);
            require(line.element(1).matches("[0-9]{1,6}") && ids.add(line.element(1)) && ids.size() <= OrderResponseD96a.MAX_LINES);
            action(line.element(2), function); amended |= Set.of("6", "7").contains(line.element(2));
            if (line.fields().size() == 4) require(line.element(3).matches("[0-9]{8,14}:EN"));
            lineDetails(cursor);
        }
        require(!ids.isEmpty() && (!function.equals("30") || amended));
        require(cursor.take("UNS").fields().equals(List.of("UNS", "S")));
        require(cursor.take("CNT").fields().equals(List.of("CNT", "2:" + ids.size())));
    }

    private void action(String action, String function) {
        require(Set.of("4", "5", "6", "7").contains(action));
        String expected = switch (function) { case "12" -> "4"; case "27" -> "7"; case "29" -> "5"; default -> action; };
        require(expected.equals(action));
    }

    private void lineDetails(Cursor cursor) {
        int items = 0; Set<String> types = new HashSet<>();
        while (cursor.is("PIA")) {
            var item = cursor.take("PIA"); require(++items <= 2 && item.fields().size() == 3 && item.element(1).equals("1"));
            var id = item.components(2); require(id.size() == 2 && Set.of("SA", "IN").contains(id.get(1)) && types.add(id.get(1)));
            bounded(id.getFirst(), 35);
        }
        int descriptions = 0;
        while (cursor.is("IMD")) { require(++descriptions <= 2); description(cursor.take("IMD")); }
        quantity(cursor.take("QTY"));
    }

    private void description(Segment segment) {
        require(segment.fields().size() == 4 && segment.element(1).equals("F") && segment.element(2).isEmpty());
        var description = segment.components(3); require(description.size() == 4 || description.size() == 5);
        require(description.subList(0, 3).stream().allMatch(String::isEmpty));
        description.subList(3, description.size()).forEach(part -> bounded(part, 35));
    }

    private void quantity(Segment quantity) {
        require(quantity.fields().size() == 2); var value = quantity.components(1);
        require(value.size() == 3 && value.getFirst().equals("21") && UNITS.contains(value.get(2)));
        require(value.get(1).matches("[0-9]+(?:\\.[0-9]+)?") && value.get(1).length() <= 15);
        require(new BigDecimal(value.get(1)).signum() >= 0);
    }

    private void date(Segment date, String qualifier) {
        require(date.fields().size() == 2); var value = date.components(1);
        require(value.size() == 3 && value.getFirst().equals(qualifier) && value.get(2).equals("102") && value.get(1).matches("[0-9]{8}"));
        LocalDate.parse(value.get(1), DateTimeFormatter.BASIC_ISO_DATE);
    }

    private void text(Segment text) {
        require(text.fields().size() == 5 && text.element(1).equals("AAI") && text.element(2).isEmpty() && text.element(3).isEmpty());
        require(text.components(4).size() == 1); bounded(text.element(4), 70);
    }

    private void bounded(String value, int limit) { require(!value.isBlank() && decoded(value).length() <= limit); }

    private List<Segment> parse(String edi) {
        require(edi != null && edi.length() <= 25 * 1024 * 1024 && edi.endsWith("'") && edi.matches("[\\x20-\\x7E]*"));
        var raw = split(edi, '\''); require(raw.getLast().isEmpty());
        List<Segment> result = new ArrayList<>();
        for (String item : raw.subList(0, raw.size() - 1)) {
            var fields = split(item, '+'); require(!fields.getFirst().isEmpty()); result.add(new Segment(fields.getFirst(), fields));
        }
        return result;
    }

    private static List<String> split(String value, char delimiter) {
        List<String> parts = new ArrayList<>(); StringBuilder current = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '?') { require(index + 1 < value.length()); current.append(c).append(value.charAt(++index)); }
            else if (c == delimiter) { parts.add(current.toString()); current.setLength(0); }
            else current.append(c);
        }
        parts.add(current.toString()); return parts;
    }

    private static String decoded(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '?') index++;
            require(index < value.length()); result.append(value.charAt(index));
        }
        return result.toString();
    }

    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException("Invalid D96A core payload"); }
    private record Segment(String tag, List<String> fields) {
        String element(int index) { return index < fields.size() ? fields.get(index) : ""; }
        List<String> components(int index) { return split(element(index), ':'); }
    }

    private static final class Cursor {
        private final List<Segment> segments;
        private int position;
        Cursor(List<Segment> segments, int position) { this.segments = segments; this.position = position; }
        boolean is(String tag) { return position < segments.size() && segments.get(position).tag().equals(tag); }
        Segment take(String tag) { require(is(tag)); return segments.get(position++); }
        boolean finished() { return position == segments.size(); }
    }
}
