package com.edispark.tradacoms.ack;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BqAcknowledgementContractTest {
    private final BqAcknowledgementV14 codec = new BqAcknowledgementV14();
    private static final Instant AT = Instant.parse("2026-11-09T12:34:56Z");

    @ParameterizedTest @ValueSource(ints = {1, 8, 12, 10000})
    void carriesEveryAcknowledgedLineInTheGuidesThreeMessageLayout(int count) {
        String edi = codec.write(model(count), new BqAcknowledgement.Control(42, 7), AT);
        assertTrue(edi.startsWith("STX=ANA:1+0000000000000:Demo Supplier+5013546018962:ACKNOWLEDGEMENT+261109:123456+42++ACKHDR'"));
        assertTrue(edi.contains("MHD=1+ACKHDR:4'TYP=3120'SDT=0000000000000:0000001+Demo Supplier Ltd'CDT=5013546018962+B&Q LTD'FIL=7+1+261109'MTR=6'"));
        assertTrue(edi.contains("MHD=2+ACKMNT:4'CLO=:SO-42'AOR=PO-42'DNA=1++186:261114:194:0001'"));
        assertTrue(edi.endsWith("MHD=3+ACKTLR:4'KFT=1'MTR=3'END=3'"));
        var segments = new java.util.HashSet<>(List.of(edi.split("'")));
        for (int index = 1; index <= count; index++) {
            assertTrue(segments.contains("ALD=" + index + "+0000000000000+++::EA+" + index + "+++Product " + index));
            assertTrue(segments.contains("AQD=" + index + "+1+++261114+" + index));
            assertTrue(segments.contains("DNB=" + index + "+1++043:" + index));
        }
        assertTrue(codec.validateBytes(edi).valid());
    }

    @Test void rejectsMissingPartnerFactsAndReceiptOnlyDecisionsBeforeEncoding() {
        var good = model(8);
        var invalid = new BqAcknowledgement(good.contract(), good.sourceVersion(), "AB",
                new BqAcknowledgement.Supplier("[vendor_identifier]", "ZZZ", null, "Demo Supplier", "Demo Supplier Ltd"),
                good.customer(), new BqAcknowledgement.Order("PO-42", null, null, null), good.lines());
        var report = codec.validate(invalid);
        assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("BQ_VENDOR_NUMBER_REQUIRED")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("BQ_ACK_DECISION_UNSUPPORTED")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.path().equals("/order/deliveryDate")));
        assertThrows(BqAcknowledgementValidation.InvalidAcknowledgement.class, () -> codec.write(invalid, new BqAcknowledgement.Control(1, 1), AT));
    }

    @Test void finalBytesRejectIncorrectVersionsCountsQuantitiesDatesAndIdentifiers() {
        var edi = codec.write(model(8), new BqAcknowledgement.Control(1, 1), AT);
        assertFalse(codec.validateBytes(edi.replace("ACKHDR:4", "ACKHDR:9")).valid());
        assertFalse(codec.validateBytes(edi.replace("KTR=8", "KTR=1")).valid());
        assertFalse(codec.validateBytes(edi.replace("END=3", "END=2")).valid());
        assertFalse(codec.validateBytes(edi.replace("AQD=8+1+++261114+8", "AQD=8+1+++261114+7")).valid());
        assertFalse(codec.validateBytes(edi.replace("261114", "260231")).valid());
        assertFalse(codec.validateBytes(edi.replace("ALD=1+0000000000000", "ALD=1+0000000000001")).valid());
    }

    @Test void releasesServiceCharactersWithoutChangingBusinessReferences() {
        var good = model(1);
        var escaped = new BqAcknowledgement(good.contract(), good.sourceVersion(), good.decision(),
                new BqAcknowledgement.Supplier("0000000000000", "GLN", "0000001", "Demo+Supplier", "Demo'Supplier"),
                good.customer(), new BqAcknowledgement.Order("PO:42", "SO=42", "1?", "2026-11-14"), good.lines());
        var edi = codec.write(escaped, new BqAcknowledgement.Control(1, 1), AT);
        assertTrue(edi.contains("Demo?+Supplier")); assertTrue(edi.contains("Demo?'Supplier"));
        assertTrue(edi.contains("CLO=:SO?=42'")); assertTrue(edi.contains("AOR=PO?:42'"));
        assertTrue(codec.validateBytes(edi).valid());
    }

    private BqAcknowledgement model(int count) {
        return new BqAcknowledgement(BqAcknowledgement.CONTRACT, "2.1", "AP",
                new BqAcknowledgement.Supplier("0000000000000", "GLN", "0000001", "Demo Supplier", "Demo Supplier Ltd"),
                new BqAcknowledgement.Customer(BqAcknowledgementV14.BUYER_ANA, "GLN", "B&Q LTD", "B&Q LTD"),
                new BqAcknowledgement.Order("PO-42", "SO-42", "0001", "2026-11-14"),
                IntStream.rangeClosed(1, count).mapToObj(i -> new BqAcknowledgement.Line("" + i, "Accepted", "0000000000000", "GTIN", BigDecimal.valueOf(i), "EA", "Product " + i, null)).toList());
    }
}
