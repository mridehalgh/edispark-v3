package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.poifs.filesystem.*;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.*;
import javax.imageio.ImageIO;

final class MigExtractor {
    static final int MAX_BYTES = 3 * 1024 * 1024;
    static final int MAX_EVIDENCE = 100_000;
    private static final int MAX_PAGES = 180;
    private static final int MAX_ZIP_EXPANDED = 24 * 1024 * 1024;
    private static final Set<String> PDF_KEYS = Set.of("EmbeddedFiles", "EF", "JavaScript", "JS", "AA", "XFA", "RichMedia", "AF");
    private static final Set<String> PDF_ACTIONS = Set.of("Launch", "JavaScript", "ImportData", "SubmitForm", "Rendition", "GoToE");

    interface Ocr {
        List<Section> recover(byte[] image, String page);
    }

    record Extraction(List<Section> sections, List<MigFinding> findings) { }

    static String text(byte[] bytes) throws CharacterCodingException {
        String value = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        if (value.codePoints().anyMatch(c -> c < 32 && c != '\n' && c != '\r' && c != '\t')) {
            throw new IllegalArgumentException("Text files must contain UTF-8 text without binary data.");
        }
        return value;
    }

    Extraction extract(Upload upload, byte[] bytes, Ocr ocr, int[] ocrPages) {
        var result = new Collector(upload.role() + "/" + upload.id());
        String extension = upload.name().substring(upload.name().lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        try {
            switch (extension) {
                case "pdf" -> pdf(bytes, result, ocr, ocrPages);
                case "doc" -> legacyWord(bytes, result);
                case "docx" -> word(bytes, result);
                case "xls", "xlsx" -> excel(bytes, result, extension);
                case "txt" -> plain(bytes, result);
                default -> throw new IOException("Unsupported format");
            }
        } catch (IOException | RuntimeException | StackOverflowError invalid) {
            throw new IllegalArgumentException("The guide is invalid, exceeds extraction limits, or contains active or embedded content.");
        }
        if (result.sections.isEmpty() && result.findings.isEmpty()) result.gap("document", "No readable guide text was found.");
        return new Extraction(List.copyOf(result.sections), List.copyOf(result.findings));
    }

    private void plain(byte[] bytes, Collector result) throws IOException {
        String[] lines = text(bytes).replaceFirst("^\uFEFF", "").split("\\R", -1);
        for (int index = 0; index < lines.length && !result.full; index++) {
            result.add("line " + (index + 1), lines[index]);
        }
    }

    private void pdf(byte[] bytes, Collector result, Ocr ocr, int[] remaining) throws IOException {
        if (!startsWith(bytes, "%PDF-")) throw new IOException("Invalid PDF signature");
        try (var document = Loader.loadPDF(new org.apache.pdfbox.io.RandomAccessReadBuffer(bytes), IOUtils.createTempFileOnlyStreamCache())) {
            var cos = document.getDocument();
            if (document.isEncrypted() || document.getNumberOfPages() > MAX_PAGES || cos.getXrefTable().size() > 30_000) {
                throw new IOException("Unsupported PDF");
            }
            var visited = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
            checkPdf(cos.getTrailer(), visited);
            for (var id : cos.getXrefTable().keySet()) checkPdf(cos.getObjectFromPool(id), visited);
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            var renderer = new PDFRenderer(document);
            for (int page = 1; page <= document.getNumberOfPages() && !result.full; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document).strip();
                String anchor = "page " + page;
                if (pageText.length() < 30 && ocr != null && remaining[0] > 0) {
                    remaining[0]--;
                    var recovered = recoverPage(renderer, document.getPage(page - 1), page, anchor, ocr);
                    if (!recovered.isEmpty()) {
                        recovered.forEach(section -> result.add(section.anchor(), section.excerpt()));
                        continue;
                    }
                }
                if (pageText.length() < 30) result.gap(anchor, "The page has insufficient positioned text and needs image or table recovery.");
                String[] rows = pageText.split("\\R");
                for (int row = 0; row < rows.length && !result.full; row++) result.add(anchor + ", row " + (row + 1), rows[row]);
            }
        }
    }

    private List<Section> recoverPage(PDFRenderer renderer, org.apache.pdfbox.pdmodel.PDPage page,
            int number, String anchor, Ocr ocr) {
        float longest = Math.max(page.getCropBox().getWidth(), page.getCropBox().getHeight());
        if (!Float.isFinite(longest) || longest <= 0 || longest > 5_000) return List.of();
        try (var image = new ByteArrayOutputStream()) {
            ImageIO.write(renderer.renderImage(number - 1, Math.min(2, 2000 / longest)), "png", image);
            return image.size() <= MAX_BYTES ? ocr.recover(image.toByteArray(), anchor) : List.of();
        } catch (IOException | RuntimeException unavailable) {
            return List.of();
        }
    }

    private void checkPdf(COSBase value, Set<COSBase> visited) throws IOException {
        if (value == null || !visited.add(value)) return;
        if (visited.size() > 250_000) throw new IOException("PDF object limit");
        switch (value) {
            case COSObject reference -> checkPdf(reference.getObject(), visited);
            case COSArray array -> { for (var item : array) checkPdf(item, visited); }
            case COSDictionary dictionary -> {
                if (PDF_ACTIONS.contains(dictionary.getNameAsString(COSName.S, ""))
                        || dictionary.keySet().stream().anyMatch(key -> PDF_KEYS.contains(key.getName()))) {
                    throw new IOException("Active PDF content");
                }
                for (var child : dictionary.getValues()) checkPdf(child, visited);
            }
            default -> { }
        }
    }

    private void word(byte[] bytes, Collector result) throws IOException {
        checkZip(bytes, "word/document.xml");
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            int paragraph = 0;
            int table = 0;
            for (var element : document.getBodyElements()) {
                if (result.full) break;
                switch (element) {
                    case XWPFParagraph item -> result.add("paragraph " + ++paragraph, item.getText());
                    case XWPFTable item -> {
                        table++;
                        int row = 0;
                        for (var cells : item.getRows()) {
                            if (result.full) break;
                            result.add("table " + table + ", row " + ++row,
                                    String.join(" | ", cells.getTableCells().stream().map(XWPFTableCell::getText).toList()));
                        }
                    }
                    default -> result.gap("document", "An unsupported Word section could not be extracted.");
                }
            }
        }
    }

    private void legacyWord(byte[] bytes, Collector result) throws IOException {
        checkOle(bytes, "WordDocument");
        try (var document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            var range = document.getRange();
            for (int index = 0; index < range.numParagraphs() && !result.full; index++) {
                var paragraph = range.getParagraph(index);
                result.add((paragraph.isInTable() ? "table paragraph " : "paragraph ") + (index + 1), paragraph.text());
            }
        }
    }

    private void excel(byte[] bytes, Collector result, String extension) throws IOException {
        if ("xlsx".equals(extension)) checkZip(bytes, "xl/workbook.xml");
        else checkOle(bytes, "Workbook", "Book");
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var formatter = new DataFormatter(Locale.ROOT);
            formatter.setUseCachedValuesForFormulaCells(true);
            for (var sheet : workbook) {
                for (var row : sheet) {
                    if (result.full) return;
                    var cells = new ArrayList<String>();
                    for (var cell : row) {
                        String value = formatter.formatCellValue(cell);
                        if (!value.isBlank()) cells.add(cell.getAddress() + " " + value);
                    }
                    result.add("sheet " + sheet.getSheetName() + ", row " + (row.getRowNum() + 1), String.join(" | ", cells));
                }
            }
        }
    }

    private void checkZip(byte[] bytes, String required) throws IOException {
        var names = new HashSet<String>();
        int expanded = 0;
        byte[] buffer = new byte[8192];
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!names.add(name) || names.size() > 1_200 || name.contains("..") || name.indexOf('\\') >= 0
                        || name.contains("vba") || name.contains("macrosheet") || name.contains("/embeddings/")
                        || name.contains("/activex/") || name.contains("/externallinks/") || name.endsWith(".bin")) {
                    throw new IOException("Unsupported Office package part");
                }
                for (int count = zip.read(buffer); count != -1; count = zip.read(buffer)) {
                    expanded += count;
                    if (expanded > MAX_ZIP_EXPANDED) throw new IOException("Expanded Office package too large");
                }
            }
        }
        if (!names.contains(required) || !names.contains("[content_types].xml")) throw new IOException("Invalid Office package");
    }

    private void checkOle(byte[] bytes, String... required) throws IOException {
        try (var file = new POIFSFileSystem(new ByteArrayInputStream(bytes))) {
            var root = file.getRoot();
            if (Arrays.stream(required).noneMatch(root::hasEntry)) throw new IOException("Wrong OLE format");
            for (Entry entry : root) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (entry.isDirectoryEntry() || name.contains("vba") || name.contains("macro") || name.contains("ole")
                        || name.contains("package") || name.contains("encrypted")) throw new IOException("Embedded OLE content");
            }
        }
    }

    private static boolean startsWith(byte[] data, String prefix) {
        return data.length >= prefix.length() && Arrays.equals(data, 0, prefix.length(),
                prefix.getBytes(StandardCharsets.US_ASCII), 0, prefix.length());
    }

    private static final class Collector {
        final String source;
        final List<Section> sections = new ArrayList<>();
        final List<MigFinding> findings = new ArrayList<>();
        int characters;
        boolean full;

        Collector(String source) { this.source = source; }

        void add(String position, String raw) {
            String excerpt = raw.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").strip();
            if (excerpt.isEmpty() || full) return;
            String anchor = source + "/" + position;
            for (int offset = 0; offset < excerpt.length(); offset += 900) {
                String part = excerpt.substring(offset, Math.min(offset + 900, excerpt.length()));
                if (sections.size() >= 1_500 || (characters += anchor.length() + part.length()) > MAX_EVIDENCE) {
                    full = true;
                    gap(position, "The evidence budget was exceeded. Use a narrower guide extract.");
                    return;
                }
                sections.add(new Section(excerpt.length() > 900 ? anchor + ", part " + (offset / 900 + 1) : anchor, part));
            }
        }

        void gap(String position, String detail) {
            findings.add(new MigFinding("UNREADABLE_GUIDE_SECTION", detail, List.of(source + "/" + position)));
        }
    }
}
