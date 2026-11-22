package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;
import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class MigIntakeTest {
    private final MigExtractor extractor = new MigExtractor();

    @Test
    void preservesLegacyExcelInvoiceSheetRowsAndCells() throws Exception {
        try (var book = new HSSFWorkbook(); var buffer = new ByteArrayOutputStream()) {
            var row = book.createSheet("Invoice detail").createRow(5);
            row.createCell(1).setCellValue("ILD");
            row.createCell(2).setCellValue("Mandatory line number");
            book.write(buffer);

            var result = extract("legacy-invoice.xls", buffer.toByteArray(), null);

            assertThat(result.findings()).isEmpty();
            assertThat(result.sections()).singleElement().satisfies(section -> {
                assertThat(section.anchor()).endsWith("/sheet Invoice detail, row 6");
                assertThat(section.excerpt()).isEqualTo("B6 ILD | C6 Mandatory line number");
            });
        }
    }

    @Test
    void preservesWordTableRows() throws Exception {
        try (var guide = new XWPFDocument(); var buffer = new ByteArrayOutputStream()) {
            guide.createTable(1, 2).getRow(0).getCell(0).setText("ACK");
            guide.getTables().getFirst().getRow(0).getCell(1).setText("Accepted status");
            guide.write(buffer);

            assertThat(extract("ack-guide.docx", buffer.toByteArray(), null).sections()).singleElement()
                    .satisfies(section -> {
                        assertThat(section.anchor()).endsWith("/table 1, row 1");
                        assertThat(section.excerpt()).isEqualTo("ACK | Accepted status");
                    });
        }
    }

    @Test
    void scannedPagesAreGapsUntilBoundedOcrRecoversATableRow() throws Exception {
        byte[] blank;
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(bytes);
            blank = bytes.toByteArray();
        }
        assertThat(extract("scan.pdf", blank, null).findings()).extracting(MigFinding::code)
                .containsExactly("UNREADABLE_GUIDE_SECTION");
        var recovered = extract("scan.pdf", blank, (image, anchor) ->
                List.of(new Section(anchor + ", OCR table 1, row 2", "BGM | mandatory reference")));
        assertThat(recovered.findings()).isEmpty();
        assertThat(recovered.sections()).extracting(Section::anchor).singleElement().asString().endsWith("/page 1, OCR table 1, row 2");
    }

    @Test
    void rejectsArchivesAndOfficePayloadsDisguisedAsGuides() throws Exception {
        try (var bytes = new ByteArrayOutputStream()) {
            try (var zip = new ZipOutputStream(bytes)) {
                for (String name : List.of("[Content_Types].xml", "word/document.xml", "word/embeddings/payload.exe")) {
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write("test".getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            assertThatIllegalArgumentException().isThrownBy(() -> extract("instructions.docx", bytes.toByteArray(), null))
                    .withMessageContaining("active or embedded content");
            assertThatIllegalArgumentException().isThrownBy(() -> extract("instructions.zip", bytes.toByteArray(), null));
        }
    }

    @Test
    void rejectsPdfActionsBeforeAnyOcrRequest() throws Exception {
        try (var guide = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            guide.addPage(new PDPage());
            guide.getDocumentCatalog().getCOSObject().setString(COSName.getPDFName("JS"), "test action");
            guide.save(bytes);
            assertThatIllegalArgumentException().isThrownBy(() -> extract("active.pdf", bytes.toByteArray(), (image, page) -> {
                throw new AssertionError("Active content reached OCR");
            }));
        }
    }

    private MigExtractor.Extraction extract(String name, byte[] bytes, MigExtractor.Ocr ocr) {
        var upload = new Upload(UUID.randomUUID(), name, FileRole.PRIMARY, "fixture-v1", MigEvidence.digest(bytes));
        return extractor.extract(upload, bytes, ocr, new int[]{1});
    }
}
