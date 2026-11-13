package com.edispark.tradacoms.ack;

import com.edispark.tradacoms.ack.BqAcknowledgementValidation.Issue;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Independent reader for the emitted, full-acceptance B&Q subset and its three control layers. */
final class BqAcknowledgementWireValidator {
    private static final Set<String> UNITS = Set.of("EA", "C62", "H87", "KGM", "GRM", "LTR", "MLT", "MTR", "CMT", "MTK", "MTQ", "PR", "BX", "PK", "SET");

    BqAcknowledgementValidation validate(String edi) {
        try { check(parse(edi)); return new BqAcknowledgementValidation(List.of()); }
        catch (IllegalArgumentException | java.time.DateTimeException exception) {
            return new BqAcknowledgementValidation(List.of(new Issue("BQ_ACK_WIRE_INVALID", "/",
                    "The final ACK does not satisfy the B&Q segment, field or control rules.")));
        }
    }

    private void check(List<Segment> segments) {
        require(segments.size() >= 20);
        var cursor = new Cursor(segments);
        var envelope = cursor.take("STX"); envelope(envelope);
        header(cursor, envelope);
        int start = cursor.position;
        require(cursor.take("MHD").values().equals(List.of("2", "ACKMNT:4")));
        var location = cursor.take("CLO"); var locationId = location.components(0);
        require(location.values().size() >= 1 && location.values().size() <= 3 && locationId.size() == 2 && locationId.getFirst().isEmpty()); bounded(locationId.get(1), 17);
        if (location.values().size() >= 2 && !location.value(1).isEmpty()) bounded(location.value(1), 40);
        if (location.values().size() == 3) {
            var address = location.components(2); require(address.size() == 5);
            for (int index = 0; index < address.size(); index++) if (!address.get(index).isEmpty()) bounded(address.get(index), index == 4 ? 8 : 35);
        }
        var order = cursor.take("AOR"); require(order.values().size() == 1); bounded(order.value(0), 17);
        String delivery = narrative(cursor.take("DNA"));
        int count = lines(cursor, delivery);
        require(cursor.take("KTR").values().equals(List.of("" + count)));
        var trailer = cursor.take("MTR"); require(trailer.values().equals(List.of("" + (cursor.position - start))));
        require(cursor.take("MHD").values().equals(List.of("3", "ACKTLR:4")));
        require(cursor.take("KFT").values().equals(List.of("1")));
        require(cursor.take("MTR").values().equals(List.of("3")));
        require(cursor.take("END").values().equals(List.of("3"))); require(cursor.position == segments.size());
    }

    private void envelope(Segment envelope) {
        require(envelope.values().size() == 7 && envelope.value(0).equals("ANA:1") && envelope.value(5).isEmpty() && envelope.value(6).equals("ACKHDR"));
        var sender = envelope.components(1); var recipient = envelope.components(2); var at = envelope.components(3);
        require(sender.size() == 2 && BqAcknowledgementV14.gln(sender.getFirst())); bounded(sender.get(1), 35);
        require(recipient.equals(List.of(BqAcknowledgementV14.BUYER_ANA, "ACKNOWLEDGEMENT")));
        require(at.size() == 2); date(at.getFirst());
        require(at.get(1).matches("[0-9]{6}")); LocalTime.parse(at.get(1), DateTimeFormatter.ofPattern("HHmmss").withResolverStyle(ResolverStyle.STRICT));
        require(envelope.value(4).matches("[0-9]{1,14}") && new BigInteger(envelope.value(4)).signum() > 0);
    }

    private void header(Cursor cursor, Segment envelope) {
        require(cursor.take("MHD").values().equals(List.of("1", "ACKHDR:4")));
        require(cursor.take("TYP").values().equals(List.of("3120")));
        var supplier = cursor.take("SDT"); var identity = supplier.components(0);
        require(supplier.values().size() == 2 && identity.size() == 2 && identity.getFirst().equals(envelope.components(1).getFirst()));
        require(identity.get(1).matches("[0-9]{1,7}")); bounded(supplier.value(1), 40);
        var customer = cursor.take("CDT"); require(customer.values().size() == 2 && customer.value(0).equals(BqAcknowledgementV14.BUYER_ANA));
        require(Set.of("B&Q LTD", "B&Q (IRELAND) LTD").contains(decoded(customer.value(1))));
        var file = cursor.take("FIL"); require(file.values().size() == 3 && file.value(0).matches("[0-9]{1,4}") && Integer.parseInt(file.value(0)) > 0);
        require(file.value(1).equals("1")); date(file.value(2));
        require(file.value(2).equals(envelope.components(3).getFirst()));
        require(cursor.take("MTR").values().equals(List.of("6")));
    }

    private String narrative(Segment narrative) {
        require(narrative.values().size() == 3 && narrative.value(0).equals("1") && narrative.value(1).isEmpty());
        var value = narrative.components(2); require(value.size() == 4 && value.get(0).equals("186") && value.get(2).equals("194"));
        date(value.get(1)); bounded(value.get(3), 40); return value.get(1);
    }

    private int lines(Cursor cursor, String delivery) {
        int count = 0; Set<String> orderLines = new HashSet<>();
        while (cursor.is("ALD")) {
            require(++count <= BqAcknowledgementV14.MAX_LINES);
            var line = cursor.take("ALD"); require(line.value(0).equals("" + count));
            line(line);
            var expected = cursor.take("AQD");
            require(expected.values().equals(List.of("" + count, "1", "", "", delivery, line.value(5))));
            var reference = cursor.take("DNB"); require(reference.values().size() == 4 && reference.value(0).equals("" + count)
                    && reference.value(1).equals("1") && reference.value(2).isEmpty());
            var text = reference.components(3); require(text.size() == 2 && text.getFirst().equals("043")); bounded(text.get(1), 40);
            require(orderLines.add(decoded(text.get(1))));
        }
        require(count > 0); return count;
    }

    private void line(Segment line) {
        require(line.values().size() == 6 || line.values().size() == 9);
        require(BqAcknowledgementV14.gln(line.value(1)) && line.value(2).isEmpty() && line.value(3).isEmpty());
        var unit = line.components(4); require(unit.size() == 3 && unit.get(0).isEmpty() && unit.get(1).isEmpty() && UNITS.contains(unit.get(2)));
        require(line.value(5).matches("[0-9]{1,15}") && new BigInteger(line.value(5)).signum() > 0);
        if (line.values().size() == 9) {
            require(line.value(6).isEmpty() && line.value(7).isEmpty()); var description = line.components(8);
            require(description.size() <= 2); description.forEach(part -> { if (!part.isEmpty()) bounded(part, 40); });
        }
    }

    private static void date(String value) {
        require(value.matches("[0-9]{6}")); LocalDate.parse("20" + value, DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static void bounded(String value, int limit) { require(!value.isBlank() && decoded(value).length() <= limit); }
    private static List<Segment> parse(String edi) {
        require(edi != null && edi.length() <= 25 * 1024 * 1024 && edi.matches("[\\x20-\\x7E]*") && edi.endsWith("'"));
        var raw = split(edi, '\''); require(raw.getLast().isEmpty()); var result = new ArrayList<Segment>();
        for (String value : raw.subList(0, raw.size() - 1)) {
            var element = split(value, '='); require(element.size() == 2);
            result.add(new Segment(element.getFirst(), split(element.get(1), '+')));
        }
        return result;
    }

    private static List<String> split(String value, char delimiter) {
        var result = new ArrayList<String>(); var current = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '?') { require(index + 1 < value.length()); current.append(c).append(value.charAt(++index)); }
            else if (c == delimiter) { result.add(current.toString()); current.setLength(0); }
            else current.append(c);
        }
        result.add(current.toString()); return result;
    }

    private static String decoded(String value) {
        var result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '?') index++;
            require(index < value.length()); result.append(value.charAt(index));
        }
        return result.toString();
    }
    private static void require(boolean condition) { if (!condition) throw new IllegalArgumentException("Invalid B&Q ACK wire representation"); }
    private record Segment(String tag, List<String> values) {
        String value(int index) { return index < values.size() ? values.get(index) : ""; }
        List<String> components(int index) { return split(value(index), ':'); }
    }
    private static final class Cursor {
        private final List<Segment> segments; private int position;
        Cursor(List<Segment> segments) { this.segments = segments; }
        boolean is(String tag) { return position < segments.size() && segments.get(position).tag().equals(tag); }
        Segment take(String tag) { require(is(tag)); return segments.get(position++); }
    }
}
