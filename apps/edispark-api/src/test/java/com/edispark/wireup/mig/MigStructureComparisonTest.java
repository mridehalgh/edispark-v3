package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MigStructureComparisonTest {
    private static final Coordinates ORDERS = new Coordinates("OUTBOUND", "ORDER", "EDIFACT", "D96A", "ORDERS");

    @Test
    void comparesGuidePositionsAndNestedOccurrencesAgainstThePinnedDirectory() {
        var review = compare("Heading:", "0030 BGM Beginning of message M 1", "0040 DTM Date M 1",
                "0100 Segment Group 1 C 10", "0110 RFF Reference M 1", "0120 DTM Reference date C 5",
                "Detail:", "0890 Segment Group 25 M 200000", "0900 LIN Line item M 1",
                "Summary:", "2120 UNT Message trailer M 1");

        assertThat(review.digest()).matches("sha-256:[0-9a-f]{64}");
        assertThat(review.rows()).anySatisfy(row -> {
            assertThat(row.guide().tag()).isEqualTo("BGM");
            assertThat(row.guide().position()).isEqualTo("0030");
            assertThat(row.standard().position()).isEqualTo("0020");
            assertThat(row.result()).isEqualTo(StructureResult.MATCH);
        }).anySatisfy(row -> {
            assertThat(row.guide().position()).isEqualTo("0040");
            assertThat(row.standard().position()).isEqualTo("0030");
            assertThat(row.standard().maxRepeats()).isEqualTo(35);
            assertThat(row.result()).isEqualTo(StructureResult.PARTNER_RESTRICTION);
        }).anySatisfy(row -> {
            assertThat(row.guide().position()).isEqualTo("0120");
            assertThat(row.standard().position()).isEqualTo("0100");
            assertThat(row.standard().groupPath()).isEqualTo("SG1");
            assertThat(row.result()).isEqualTo(StructureResult.MATCH);
        }).anySatisfy(row -> {
            assertThat(row.guide().tag()).isEqualTo("LIN");
            assertThat(row.standard().groupPath()).isEqualTo("SG25");
            assertThat(row.standard().position()).isEqualTo("0940");
        });
    }

    @Test
    void distinguishesRestrictionsConflictsAndUnresolvedScope() {
        var rows = compare("0010 | DTM | C | 1", "0010 | UNB | M | 1",
                "STD 0030 | DTM | M | 36", "STD 0020 | BGM | N | 0",
                "STD 0010 | BGM | M | 1", "STD 0040 | PAI | M | 1",
                "DTM+137:20260101:102'", "The issue date is mandatory.").rows();

        assertThat(rows).extracting(StructureComparison::result).containsExactly(
                StructureResult.CONFLICT, StructureResult.CONFLICT, StructureResult.CONFLICT,
                StructureResult.NEEDS_REVIEW, StructureResult.PARTNER_RESTRICTION, StructureResult.ENVELOPE);
        assertThat(rows.get(3).standard()).isNull();
        assertThat(rows.getFirst().guide().anchor()).endsWith("line 3");
    }

    @Test
    void normalizesSpreadsheetAndOcrRowsWithoutBorrowingScopeFromAnotherSheetOrGuide() {
        var review = MigStructureComparison.compare(ORDERS, List.of(
                new Section("PRIMARY/fixture/sheet Order, row 1", "A1 Heading:"),
                new Section("PRIMARY/fixture/sheet Order, row 2", "A2 40 | B2 DTM | C2 Date | D2 M | E2 1"),
                new Section("PRIMARY/fixture/sheet Other, row 1", "A1 40 | B1 DTM | D1 C | E1 1"),
                new Section("SUPPLEMENT/fixture/page 1, OCR line 1", "0020 BGM M 1"),
                new Section("SUPPLEMENT/fixture/page 1, OCR table 1, row 1", "cell 1 0030 | cell 2 BGM | cell 3 M | cell 4 1")), false);

        assertThat(review.rows()).extracting(StructureComparison::result)
                .containsExactly(StructureResult.PARTNER_RESTRICTION, StructureResult.NEEDS_REVIEW, StructureResult.MATCH);
        assertThat(review.rows().getLast().guide().anchor()).contains("OCR table");
    }

    @Test
    void recognisesAnOfficialPositionColumnAndPreservesTheGroupAfterARepeatConflict() {
        var review = compare("Heading:", "UNECE Position | Tag | Status | Rep",
                "0080 | Segment Group 1 | C | 11", "0100 | DTM | C | 5",
                "Pos | Tag | Status | Rep", "0120 | DTM | C | 5");

        assertThat(review.rows()).extracting(StructureComparison::result)
                .containsExactly(StructureResult.CONFLICT, StructureResult.MATCH, StructureResult.MATCH);
        assertThat(review.rows().getLast().standard().position()).isEqualTo("0100");
    }

    @Test
    void skipsComparisonWhenTheGuideNamesAnotherReleaseOrTheDirectoryIsUnavailable() {
        assertThat(MigStructureComparison.compare(ORDERS, List.of(), true).state())
                .isEqualTo(StructureState.GUIDE_RELEASE_MISMATCH);
        var unsupported = new Coordinates("INBOUND", "ORDER", "EDIFACT", "D01B", "ORDERS");
        assertThat(MigStructureComparison.compare(unsupported, List.of(), false).state())
                .isEqualTo(StructureState.DIRECTORY_UNAVAILABLE);
    }

    private StandardsReview compare(String... lines) {
        var sections = IntStream.range(0, lines.length)
                .mapToObj(i -> new Section("PRIMARY/fixture/line " + (i + 1), lines[i])).toList();
        return MigStructureComparison.compare(ORDERS, sections, false);
    }
}
