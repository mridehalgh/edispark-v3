package com.edispark.edifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderResponseContractTest {
    @ParameterizedTest @ValueSource(ints = {1, 8, 12, 10000})
    void emitsEverySupportedLineWithoutAnEightLineLimit(int count) {
        var codec = new OrderResponseD96a();
        var edi = codec.write(response("29", "5", count), "CTRL", Instant.parse("2026-11-04T12:34:00Z"));
        assertThat(edi.split("LIN\\+", -1)).hasSize(count + 1);
        var segments = new java.util.HashSet<>(List.of(edi.split("'")));
        for (int line = 1; line <= count; line++) {
            assertThat(segments).contains("LIN+" + line + "+5", "PIA+1+SKU-" + line + ":SA", "IMD+F++:::Product " + line, "QTY+21:" + line + ":EA");
        }
        assertThat(codec.validateBytes(edi).valid()).isTrue();
    }

    @Test void rejectsCorruptFinalDatesIdentifiersAndLengthsIndependentlyOfTheWriter() {
        var codec = new OrderResponseD96a();
        var edi = codec.write(response("29", "5", 1), "CTRL", Instant.parse("2026-11-04T12:34:00Z"));
        assertThat(codec.validateBytes(edi.replace("20261104", "20260231")).valid()).isFalse();
        assertThat(codec.validateBytes(edi.replace("::91", "::XXX")).valid()).isFalse();
        assertThat(codec.validateBytes(edi.replace("SKU-1:SA", "X".repeat(36) + ":SA")).valid()).isFalse();
        assertThat(codec.validateBytes(edi.replace("QTY+21:1:EA", "QTY+21:1:ZZZ")).valid()).isFalse();
    }

    @Test void rejectsPlaceholderPartyIdsClaimingToBeGs1Identifiers() {
        var good = response("29", "5", 1);
        var parties = new OrderResponse.Parties("[supplier_identifier]", "[buyer_identifier]", "9", "9", null, null, null, null);
        assertThat(new OrderResponseD96a().validate(new OrderResponse(good.contract(), good.document(), parties, good.delivery(), good.lines())).issues())
                .hasSize(2).allSatisfy(issue -> assertThat(issue.code()).isEqualTo("SOURCE_IDENTIFIER_INVALID"));
    }
    @Test void preservesEightLinesResponseSemanticsAndPartyDataWithIndependentExpectedSegments() {
        var codec = new OrderResponseD96a();
        String edi = codec.write(response("29", "5", 8), "CTRL001", Instant.parse("2026-11-04T12:34:00Z"));
        assertThat(edi).contains("BGM+231+OR-42+29'", "RFF+ON:PO-42'", "NAD+SU+SUPPLIER::91++Legal Supplier:Trading Supplier'",
                "CUX+2:GBP:9'", "NAD+DP++++++++GB'", "LIN+8+5'", "PIA+1+SKU-8:SA'", "QTY+21:8:EA'", "UNS+S'", "CNT+2:8'");
        assertThat(codec.validateBytes(edi).issues()).isEmpty();
        assertThat(codec.validateBytes(edi.replace("CNT+2:8", "CNT+2:1")).issues()).isNotEmpty();
        assertThat(codec.validateBytes(edi.replace("+29'", "+9'")).issues()).isNotEmpty();
    }

    @Test void rejectsReceiptAcknowledgementWithAcceptedLinesInsteadOfChangingItsMeaning() {
        var report = new OrderResponseD96a().validate(response("12", "5", 8));
        assertThat(report.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("RESPONSE_STATUS_CONFLICT");
            assertThat(issue.path()).isEqualTo("/document/function");
        });
    }

    @Test void supportsReceiptOnlyAndRejectionWithoutInventingAcceptance() {
        var codec = new OrderResponseD96a();
        assertThat(codec.write(response("12", "4", 1), "CONTROL", Instant.now())).contains("+12'", "LIN+1+4'");
        assertThat(codec.write(response("27", "7", 1), "CONTROL", Instant.now())).contains("+27'", "LIN+1+7'");
    }

    @Test void escapesDelimitersAndRejectsUnknownCodesLengthsAndDuplicateLines() {
        var codec = new OrderResponseD96a();
        var good = response("29", "5", 1);
        var header = new OrderResponse.Header("OR+42'", "2026-11-04", "PO:42", "29", "GBP", "Note? O'Brien");
        String edi = codec.write(new OrderResponse(good.contract(), header, good.parties(), good.delivery(), good.lines()), "CTRL", Instant.now());
        assertThat(edi).contains("OR?+42?'", "PO?:42", "Note?? O?'Brien");
        assertThat(codec.validateBytes(edi).valid()).isTrue();
        assertThat(codec.validate(response("99", "5", 1)).valid()).isFalse();
        assertThat(codec.validate(response("29", "5", 0)).valid()).isFalse();
        assertThat(codec.validate(new OrderResponse(good.contract(), good.document(), good.parties(), good.delivery(), List.of(good.lines().getFirst(), good.lines().getFirst()))).valid()).isFalse();
        assertThatThrownBy(() -> codec.write(response("12", "5", 1), "CTRL", Instant.now())).isInstanceOf(OrderResponseValidation.InvalidOrderResponse.class);
    }

    private OrderResponse response(String function, String action, int count) {
        var lines = IntStream.rangeClosed(1, count).mapToObj(index -> new OrderResponse.Line(String.valueOf(index), action,
                BigDecimal.valueOf(index), "EA", "Product " + index, null, "SKU-" + index, null, null)).toList();
        return new OrderResponse(OrderResponse.CONTRACT, new OrderResponse.Header("OR-42", "2026-11-04", "PO-42", function, "GBP", null),
                new OrderResponse.Parties("SUPPLIER", "BUYER", "91", "92", "Trading Supplier", "Legal Supplier", "Trading Buyer", "Legal Buyer"),
                new OrderResponse.Delivery("GB"), lines);
    }
}
