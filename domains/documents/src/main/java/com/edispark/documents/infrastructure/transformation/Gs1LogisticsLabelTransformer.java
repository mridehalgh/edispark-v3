package com.edispark.documents.infrastructure.transformation;

import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.Format;
import com.edispark.documents.domain.service.DocumentTransformer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/** Renders GS1 logistics labels from UBL TransportHandlingUnit aggregates. */
public final class Gs1LogisticsLabelTransformer implements DocumentTransformer {
    private static final PDRectangle A5 = new PDRectangle(420, 595);
    private static final char CODE_128_FNC1 = '\u00f1';
    private final ObjectMapper json;

    public Gs1LogisticsLabelTransformer(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Content transform(Content source, Format targetFormat) {
        List<Label> labels = labels(parse(source));
        if (labels.isEmpty()) {
            throw new IllegalArgumentException(
                    "Add a valid 18-digit SSCC to at least one handling unit before creating labels");
        }
        try {
            byte[] output = targetFormat == Format.PDF ? pdf(labels) : zpl(labels).getBytes(StandardCharsets.UTF_8);
            return Content.of(output, targetFormat);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not render GS1 logistics labels", exception);
        }
    }

    private JsonNode parse(Content source) {
        try {
            return json.readTree(source.data());
        } catch (Exception exception) {
            throw new IllegalArgumentException("The source document is not readable JSON", exception);
        }
    }

    @Override
    public boolean supports(Format sourceFormat, Format targetFormat) {
        return sourceFormat == Format.JSON && (targetFormat == Format.PDF || targetFormat == Format.ZPL);
    }

    private List<Label> labels(JsonNode root) {
        List<Label> result = new ArrayList<>();
        visit(root, null, result);
        return result;
    }

    private void visit(JsonNode node, String fieldName, List<Label> result) {
        if ("TransportHandlingUnit".equals(fieldName) && node.isArray()) {
            node.forEach(unit -> {
                String sscc = scalar(unit.path("TraceID"));
                if (!sscc.isBlank()) result.add(new Label(scalar(unit.path("ID")), validateSscc(sscc),
                        scalar(unit.path("ShippingMarks"))));
            });
        }
        if (node.isObject()) node.fields().forEachRemaining(entry -> visit(entry.getValue(), entry.getKey(), result));
        else if (node.isArray()) node.forEach(child -> visit(child, fieldName, result));
    }

    private String scalar(JsonNode node) {
        JsonNode first = node.isArray() && !node.isEmpty() ? node.get(0) : node;
        return first.path("_").asText("").trim();
    }

    private String validateSscc(String value) {
        if (!value.matches("\\d{18}")) throw new IllegalArgumentException("SSCC must contain 18 digits");
        int sum = 0;
        for (int index = 0; index < 17; index++) sum += Character.digit(value.charAt(index), 10) * (index % 2 == 0 ? 3 : 1);
        int expected = (10 - sum % 10) % 10;
        if (expected != Character.digit(value.charAt(17), 10)) throw new IllegalArgumentException("SSCC check digit is invalid");
        return value;
    }

    private byte[] pdf(List<Label> labels) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (Label label : labels) {
                PDPage page = new PDPage(A5);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    text(content, 36, 535, 15, "EDI SPARK · GS1 LOGISTICS LABEL");
                    text(content, 36, 490, 11, safe("Handling unit: " + label.id()));
                    if (!label.shippingMarks().isBlank()) text(content, 36, 465, 11, safe("Shipping marks: " + label.shippingMarks()));
                    text(content, 36, 405, 24, "SSCC  " + label.sscc());
                    drawBarcode(content, label.sscc(), 36, 170, 348, 150);
                    text(content, 125, 140, 16, "(00) " + label.sscc());
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private void drawBarcode(PDPageContentStream content, String sscc, float x, float y, float width, float height) throws Exception {
        BitMatrix matrix = gs1SsccBarcode(sscc, (int) width);
        float module = width / matrix.getWidth();
        for (int column = 0; column < matrix.getWidth(); column++) {
            if (matrix.get(column, 50)) content.addRect(x + column * module, y, module + 0.2f, height);
        }
        content.fill();
    }

    static BitMatrix gs1SsccBarcode(String sscc, int width) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.GS1_FORMAT, true);
        hints.put(EncodeHintType.MARGIN, 10);
        // Code 128 is only GS1-128 when FNC1 immediately follows its start character.
        // ZXing's GS1_FORMAT hint does not insert FNC1 for Code128Writer, so include it
        // explicitly. Parentheses belong to the human-readable text, not the symbol.
        String elementString = CODE_128_FNC1 + "00" + sscc;
        return new MultiFormatWriter().encode(elementString, BarcodeFormat.CODE_128,
                (int) width, 100, hints);
    }

    private void text(PDPageContentStream content, float x, float y, float size, String value) throws Exception {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), size);
        content.newLineAtOffset(x, y);
        content.showText(value);
        content.endText();
    }

    private String zpl(List<Label> labels) {
        StringBuilder output = new StringBuilder();
        for (Label label : labels) output.append("^XA\n^CI28\n^PW812\n^LL1218\n")
                .append("^FO50,60^A0N,34,34^FDGS1 LOGISTICS LABEL^FS\n")
                .append("^FO50,130^A0N,28,28^FDHandling unit: ").append(zplSafe(label.id())).append("^FS\n")
                .append("^FO50,190^A0N,42,42^FDSSCC: ").append(label.sscc()).append("^FS\n")
                .append("^FO70,330^BCN,180,Y,N,N,D^FD00").append(label.sscc()).append("^FS\n^XZ\n");
        return output.toString();
    }

    private String safe(String value) { return value.replaceAll("[^\\p{Print}]", " "); }
    private String zplSafe(String value) { return safe(value).replace("^", " ").replace("~", " "); }
    private record Label(String id, String sscc, String shippingMarks) { }
}
