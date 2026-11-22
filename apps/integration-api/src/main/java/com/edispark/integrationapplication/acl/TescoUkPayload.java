package com.edispark.integrationapplication.acl;

import com.edispark.partnerships.domain.model.SsccNumber;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tesco UK direct-to-depot profiles for standard case quantities. */
final class TescoUkPayload {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter INTERCHANGE = DateTimeFormatter.ofPattern("yyMMdd:HHmm").withZone(ZoneOffset.UTC);

    private TescoUkPayload() {}

    static String invoice(JsonNode document, String control, Instant now) {
        String supplier = gln(document, "/parties/supplier");
        String buyer = gln(document, "/parties/buyer");
        String depot = gln(document, "/delivery/depot");
        String order = order(document);
        String currency = value(document, "/document/currency");
        if (!"GBP".equals(currency) || !"380".equals(value(document, "/document/type"))
                || !"VAT".equals(value(document, "/parties/taxScheme"))) {
            throw new IllegalArgumentException("The Tesco UK invoice pilot supports GBP commercial invoices.");
        }
        var message = new ArrayList<>(List.of("UNH+1+INVOIC:D:96A:UN:EAN008",
                "BGM+380+" + edi(value(document, "/document/id")) + "+9",
                "DTM+137:" + date(document, "/document/issueDate").format(DATE) + ":102",
                "ALI+GB", "RFF+ON:" + order, "NAD+BY+" + buyer + "::9", "NAD+SN+" + depot + "::9",
                "NAD+SE+" + supplier + "::9", "RFF+VA:" + edi(value(document, "/parties/vatNumber")),
                "DTM+134:" + date(document, "/document/taxPointDate").format(DATE) + ":102", "CUX+2:GBP:4"));
        String deliveryDate = document.at("/document/deliveryDate").asText("");
        if (!deliveryDate.isBlank()) message.add("DTM+35:" + LocalDate.parse(deliveryDate).format(DATE) + ":102");

        var lines = array(document, "/lines");
        Map<BigDecimal, BigDecimal> netByRate = new LinkedHashMap<>();
        int position = 0;
        for (JsonNode line : lines) {
            var net = amount(line, "/net");
            var rate = rate(line, "/vatRate");
            if (!"VAT".equals(value(line, "/taxScheme"))
                    || !value(line, "/vatCategory").equals(rate.signum() == 0 ? "Z" : "S")) {
                throw new IllegalArgumentException("The Tesco invoice pilot supports standard and zero-rated VAT.");
            }
            String quantity = cases(line);
            var price = amount(line, "/price");
            equal(net, price.multiply(new BigDecimal(quantity)), "invoice line net");
            netByRate.merge(rate, net, BigDecimal::add);
            message.add("LIN+" + ++position + "++" + gtin(line) + ":EN");
            message.add("QTY+47:" + quantity);
            message.add("MOA+203:" + money(net));
            message.add("PRI+AAA:" + money(price) + ":CA");
            message.add(taxSegment(rate));
        }

        BigDecimal net = netByRate.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        equal(net, amount(document, "/totals/net"), "invoice net");
        equal(net, amount(document, "/totals/taxExclusive"), "invoice taxable total");
        message.addAll(List.of("UNS+S", "CNT+2:" + position, "MOA+9:" + money(net), "MOA+79:" + money(net)));
        var taxable = new LinkedHashMap<>(netByRate);
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (JsonNode vat : array(document, "/vat")) {
            var rate = rate(vat, "/rate");
            var expectedNet = taxable.remove(rate);
            if (expectedNet == null) throw new IllegalArgumentException("Tesco invoice has a duplicate or unmatched VAT rate.");
            if (!"VAT".equals(value(vat, "/taxScheme"))
                    || !value(vat, "/category").equals(rate.signum() == 0 ? "Z" : "S")) {
                throw new IllegalArgumentException("The Tesco invoice pilot supports standard and zero-rated VAT.");
            }
            var taxableAmount = amount(vat, "/net");
            var tax = amount(vat, "/tax");
            equal(expectedNet, taxableAmount, "VAT taxable amount");
            equal(taxableAmount.multiply(rate).movePointLeft(2).setScale(2, java.math.RoundingMode.HALF_UP), tax, "VAT amount");
            taxTotal = taxTotal.add(tax);
            message.addAll(List.of(taxSegment(rate), "MOA+124:" + money(tax), "MOA+125:" + money(taxableAmount)));
        }
        if (!taxable.isEmpty()) throw new IllegalArgumentException("Tesco invoice is missing a VAT breakdown.");
        equal(taxTotal, amount(document, "/totals/tax"), "invoice VAT total");
        equal(net.add(taxTotal), amount(document, "/totals/taxInclusive"), "invoice gross");
        equal(net.add(taxTotal), amount(document, "/totals/payable"), "invoice payable");
        return envelope(message, supplier, buyer, control, now);
    }

    static String despatch(JsonNode document, String control, Instant now) {
        String supplier = gln(document, "/parties/supplier");
        String buyer = gln(document, "/parties/buyer");
        String order = order(document);
        String asn = digits(document, "/document/id", "[0-9]{1,9}");
        if (!value(document, "/document/shipmentId").equals(asn)) {
            throw new IllegalArgumentException("The Tesco depot pilot requires the shipment identifier to match the ASN number.");
        }
        LocalDate booked = date(document, "/delivery/date");
        var carrier = document.at("/parties/carrier").asText("");
        String carrierId = carrier.isBlank() ? supplier : gln(document, "/parties/carrier");
        String carrierName = document.at("/parties/carrierName").asText("");
        if (carrierName.isBlank()) carrierName = value(document, "/parties/supplierName");
        var lines = new LinkedHashMap<String, JsonNode>();
        var remaining = new LinkedHashMap<String, Integer>();
        for (JsonNode line : array(document, "/lines")) {
            String id = value(line, "/orderLineId");
            if (lines.putIfAbsent(id, line) != null) throw new IllegalArgumentException("Duplicate Tesco despatch order line ID.");
            remaining.put(id, Integer.parseInt(cases(line)));
        }
        var pallets = array(document, "/pallets");
        var message = new ArrayList<>(List.of("UNH+1+DESADV:D:96A:UN:DESAD3", "BGM+351::9+" + asn + "+9",
                "DTM+137:" + date(document, "/document/issueDate").format(DATE) + time(document, "/document/issueTime") + ":203",
                "DTM+191:" + booked.format(DATE) + time(document, "/delivery/time") + ":203",
                "RFF+PK:" + order, "NAD+BY+" + buyer + "::9", "NAD+SE+" + supplier + "::9",
                "TDT+20++30++" + carrierId + ":::" + edi(carrierName), "LOC+7+" + gln(document, "/delivery/depot"),
                "CPS+1", "PAC+" + pallets.size()));
        var labels = new HashSet<String>();
        int palletNumber = 0;
        int lineNumber = 0;
        for (JsonNode pallet : pallets) {
            String sscc = value(pallet, "/sscc");
            if (!SsccNumber.isValid(sscc) || sscc.equals("0".repeat(18)) || !labels.add(sscc)) {
                throw new IllegalArgumentException("Each Tesco ASN pallet requires a distinct, valid SSCC.");
            }
            String type = value(pallet, "/type");
            if (!List.of("200", "201", "202", "PE").contains(type)) {
                throw new IllegalArgumentException("Select a supported Tesco pallet type.");
            }
            message.addAll(List.of("CPS+2+" + ++palletNumber, "PAC+1++" + type, "PCI+33E", "GIN+BJ+" + sscc));
            for (JsonNode allocation : array(pallet, "/allocations")) {
                String id = value(allocation, "/OrderLineReference/0/LineID/0/_");
                var line = lines.get(id);
                String allocationGtin = allocation.at("/Item/0/StandardItemIdentification/0/ID/0/_").asText("");
                if (line == null || !allocationGtin.isBlank() && !gtin(line).equals(allocationGtin)) {
                    throw new IllegalArgumentException("A Tesco pallet allocation must match a despatch line, order line and GTIN.");
                }
                String unit = value(allocation, "/DeliveredQuantity/0/unitCode");
                if (!unit.equals(value(line, "/unitCode"))) throw new IllegalArgumentException("The pallet and despatch units must match.");
                String count = digits(allocation, "/DeliveredQuantity/0/_", "[1-9][0-9]{0,3}");
                remaining.compute(id, (key, quantity) -> quantity - Integer.parseInt(count));
                String expiry = line.at("/bestBefore").asText("");
                if (expiry.isBlank() || LocalDate.parse(expiry).isBefore(booked)) {
                    throw new IllegalArgumentException("Each Tesco ASN line needs a best-before date on or after the booked date.");
                }
                String packaging = line.at("/packaging").asText("");
                if (!"Tesco packaging".equals(line.at("/packagingProperty").asText())) {
                    throw new IllegalArgumentException("Set the Tesco packaging property on each ASN line.");
                }
                if (!List.of("BOX", "TRAY").contains(packaging)) {
                    throw new IllegalArgumentException("Each Tesco ASN line needs BOX or TRAY packaging.");
                }
                message.addAll(List.of("LIN+" + ++lineNumber + "++" + gtin(line) + ":EN", "PIA+1+" + order + ":ORD",
                        "PIA+1+" + asn + ":DEL", "QTY+12:" + count,
                        "DTM+36:" + LocalDate.parse(expiry).format(DATE) + ":102", "FTX+AAA+++" + packaging));
            }
        }
        if (remaining.values().stream().anyMatch(quantity -> quantity != 0)) {
            throw new IllegalArgumentException("Tesco ASN pallet allocations must account for every despatched case.");
        }
        message.add("CNT+2:" + lineNumber);
        return envelope(message, supplier, buyer, control, now);
    }

    private static String envelope(List<String> message, String supplier, String buyer, String control, Instant now) {
        message.add("UNT+" + (message.size() + 1) + "+1");
        return "UNB+UNOA:3+" + supplier + ":14+" + buyer + ":14+" + INTERCHANGE.format(now)
                + "+" + control + "'" + String.join("'", message) + "'UNZ+1+" + control + "'";
    }

    private static String order(JsonNode document) {
        return digits(document, "/document/orderReference", "LM[0-9]{11}");
    }

    private static String gln(JsonNode node, String path) { return digits(node, path, "[0-9]{13}"); }
    private static String gtin(JsonNode node) { return digits(node, "/gtin", "[0-9]{13,14}"); }

    private static String digits(JsonNode node, String path, String pattern) {
        String value = value(node, path);
        if (!value.matches(pattern)) throw new IllegalArgumentException("Tesco target format is invalid at " + path);
        return value;
    }

    private static String cases(JsonNode line) {
        if (!List.of("EA", "CS", "XCT").contains(value(line, "/unitCode"))) {
            throw new IllegalArgumentException("The Tesco depot pilot supports whole case quantities.");
        }
        return digits(line, "/quantity", "[1-9][0-9]{0,3}");
    }

    private static BigDecimal amount(JsonNode node, String path) {
        var currency = node.at(path + "Currency").asText("GBP");
        if (!"GBP".equals(currency)) throw new IllegalArgumentException("Tesco invoice amounts must use GBP.");
        var amount = new BigDecimal(value(node, path));
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative amounts are outside the Tesco invoice pilot.");
        return amount;
    }

    private static BigDecimal rate(JsonNode node, String path) {
        var rate = amount(node, path).stripTrailingZeros();
        if (rate.compareTo(BigDecimal.valueOf(100)) > 0) throw new IllegalArgumentException("VAT rate is out of range.");
        return rate;
    }

    private static String taxSegment(BigDecimal rate) {
        return "TAX+7+VAT+++:::" + money(rate) + "+" + (rate.signum() == 0 ? "Z" : "S");
    }

    private static void equal(BigDecimal expected, BigDecimal actual, String field) {
        if (expected.compareTo(actual) != 0) throw new IllegalArgumentException("Tesco " + field + " does not reconcile.");
    }

    private static String money(BigDecimal value) { return value.toPlainString(); }
    private static LocalDate date(JsonNode node, String path) { return LocalDate.parse(value(node, path)); }

    private static String time(JsonNode node, String path) {
        if (node.at(path).isMissingNode()) return "0000";
        return java.time.OffsetTime.parse(value(node, path)).toLocalTime().format(TIME);
    }

    private static JsonNode array(JsonNode node, String path) {
        var array = node.at(path);
        if (!array.isArray() || array.isEmpty()) throw new IllegalArgumentException("Missing Tesco target collection " + path);
        return array;
    }

    private static String value(JsonNode node, String path) {
        String value = node.at(path).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("Missing Tesco target field " + path);
        return value;
    }

    private static String edi(String value) {
        String upper = value.toUpperCase(java.util.Locale.ROOT);
        if (!upper.matches("[A-Z0-9 .,+:/=?'()\\-]+")) {
            throw new IllegalArgumentException("Tesco target value contains characters outside UNOA.");
        }
        return upper.replace("?", "??").replace("+", "?+").replace(":", "?:").replace("'", "?'");
    }
}
