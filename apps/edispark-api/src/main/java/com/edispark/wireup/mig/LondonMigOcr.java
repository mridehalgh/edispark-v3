package com.edispark.wireup.mig;

import com.edispark.integrationapplication.mig.MigGeneration.Section;
import java.util.*;
import java.util.stream.Collectors;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

record LondonMigOcr(TextractClient textract) implements MigExtractor.Ocr {
    @Override
    public List<Section> recover(byte[] image, String page) {
        var analysis = textract.analyzeDocument(r -> r.featureTypes(FeatureType.TABLES)
                .document(d -> d.bytes(SdkBytes.fromByteArray(image))));
        var byId = analysis.blocks().stream().collect(Collectors.toMap(Block::id, block -> block, (first, other) -> first));
        var sections = new ArrayList<Section>();
        int line = 0;
        int table = 0;
        for (var block : analysis.blocks()) {
            if (block.blockType() == BlockType.LINE && confident(block)) {
                sections.add(new Section(page + ", OCR line " + ++line, block.text()));
            }
            if (block.blockType() == BlockType.TABLE) sections.addAll(table(block, byId, page + ", OCR table " + ++table));
        }
        return sections;
    }

    private List<Section> table(Block table, Map<String, Block> byId, String anchor) {
        var rows = new TreeMap<Integer, List<Block>>();
        children(table, byId).stream().filter(cell -> cell.blockType() == BlockType.CELL && confident(cell))
                .forEach(cell -> rows.computeIfAbsent(cell.rowIndex(), row -> new ArrayList<>()).add(cell));
        return rows.entrySet().stream().map(row -> new Section(anchor + ", row " + row.getKey(),
                row.getValue().stream().sorted(Comparator.comparing(Block::columnIndex))
                        .map(cell -> "cell " + cell.columnIndex() + " " + children(cell, byId).stream()
                                .filter(word -> word.blockType() == BlockType.WORD && confident(word))
                                .map(Block::text).collect(Collectors.joining(" ")))
                        .collect(Collectors.joining(" | ")))).toList();
    }

    private List<Block> children(Block block, Map<String, Block> byId) {
        return block.relationships().stream().filter(relation -> relation.type() == RelationshipType.CHILD)
                .flatMap(relation -> relation.ids().stream()).map(byId::get).filter(Objects::nonNull).toList();
    }

    private boolean confident(Block block) { return block.confidence() != null && block.confidence() >= 88; }
}
