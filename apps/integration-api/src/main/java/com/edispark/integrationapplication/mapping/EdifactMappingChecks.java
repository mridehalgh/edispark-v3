package com.edispark.integrationapplication.mapping;

import com.edispark.integrationapplication.mapping.MappingValidationReport.Finding;
import com.edispark.transformations.domain.MappingCompatibility;
import java.util.ArrayList;
import java.util.List;

final class EdifactMappingChecks {
    List<Finding> validate(MappingCompatibility format, String content) {
        var findings = new ArrayList<Finding>();
        String edi = content.strip();
        boolean advice = edi.startsWith("UNA") && edi.length() >= 9;
        char separator = advice ? edi.charAt(4) : '+';
        char component = advice ? edi.charAt(3) : ':';
        char release = advice ? edi.charAt(6) : '?';
        char terminator = advice ? edi.charAt(8) : '\'';
        var raw = split(advice ? edi.substring(9) : edi, terminator, release);
        if (!raw.getLast().isBlank()) {
            findings.add(Finding.error("EDI_TERMINATOR_MISSING", "Interchange", "The final segment is missing its terminator."));
        }
        var segments = raw.stream().map(String::strip).filter(segment -> !segment.isEmpty())
                .map(segment -> split(segment, separator, release)).toList();
        if (segments.size() < 2 || !segments.getFirst().getFirst().equals("UNB") || !segments.getLast().getFirst().equals("UNZ")) {
            findings.add(Finding.error("EDI_ENVELOPE_MISSING", "UNB / UNZ", "Supply a complete interchange with a UNB and a UNZ segment."));
            return findings;
        }
        int counted = checkMessages(format, segments, component, release, findings);
        var header = segments.getFirst();
        var trailer = segments.getLast();
        if (field(header, 5).isBlank() || !field(header, 5).equals(field(trailer, 2))) {
            findings.add(Finding.error("EDI_INTERCHANGE_REFERENCE", "UNZ", "The UNZ control reference must match UNB."));
        }
        if (!countMatches(field(trailer, 1), counted)) {
            findings.add(Finding.error("EDI_INTERCHANGE_COUNT", "UNZ", "The declared interchange count must match its messages or functional groups."));
        }
        return findings;
    }

    private int checkMessages(MappingCompatibility format, List<List<String>> segments, char component, char release,
            List<Finding> findings) {
        int messages = 0, groups = 0, start = -1, groupStart = -1;
        String groupReference = "";
        for (int index = 1; index < segments.size() - 1 && findings.size() < 100; index++) {
            var segment = segments.get(index);
            String location = segment.getFirst() + " · segment " + (index + 1);
            switch (segment.getFirst()) {
                case "UNG" -> {
                    if (groupStart >= 0 || start >= 0) findings.add(Finding.error("EDI_GROUP_BOUNDARY", location, "Finish the open group or message before starting another group."));
                    groupStart = messages;
                    groupReference = field(segment, 5);
                }
                case "UNE" -> {
                    if (groupStart < 0 || start >= 0 || !countMatches(field(segment, 1), messages - groupStart)) {
                        findings.add(Finding.error("EDI_GROUP_COUNT", location, "The group trailer count must match the messages in the group."));
                    }
                    if (groupReference.isBlank() || !groupReference.equals(field(segment, 2))) {
                        findings.add(Finding.error("EDI_GROUP_REFERENCE", location, "The UNE control reference must match UNG."));
                    }
                    groupStart = -1;
                    groups++;
                }
                case "UNH" -> {
                    if (start >= 0) findings.add(Finding.error("EDI_MESSAGE_BOUNDARY", location, "Close the previous message with UNT."));
                    start = index;
                    var identity = split(field(segment, 2), component, release);
                    if (!format.messageType().equals(field(identity, 0)) || !format.release().equals(field(identity, 1) + field(identity, 2))) {
                        findings.add(Finding.error("EDI_MESSAGE_IDENTITY", location, "Expected " + format.messageType() + " release " + format.release() + "."));
                    }
                }
                case "UNT" -> {
                    if (start < 0) {
                        findings.add(Finding.error("EDI_MESSAGE_BOUNDARY", location, "UNT requires a preceding UNH."));
                        break;
                    }
                    String reference = field(segments.get(start), 1);
                    if (reference.isBlank() || !reference.equals(field(segment, 2))) {
                        findings.add(Finding.error("EDI_MESSAGE_REFERENCE", location, "The UNT control reference must match UNH."));
                    }
                    if (!countMatches(field(segment, 1), index - start + 1)) {
                        findings.add(Finding.error("EDI_SEGMENT_COUNT", location, "The UNT segment count must include UNH and UNT."));
                    }
                    messages++;
                    start = -1;
                }
                default -> {
                    if (start < 0 || !segment.getFirst().matches("[A-Z][A-Z0-9]{2}")) {
                        findings.add(Finding.error("EDI_SEGMENT_SYNTAX", "Segment " + (index + 1), "A business segment requires a valid tag and an open message."));
                    }
                }
            }
        }
        if (start >= 0 || groupStart >= 0 || messages == 0) {
            findings.add(Finding.error("EDI_MESSAGE_INCOMPLETE", "UNH / UNT", "The interchange must contain complete messages and functional groups."));
        }
        return groups == 0 ? messages : groups;
    }

    private List<String> split(String value, char separator, char release) {
        var result = new ArrayList<String>();
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == release) index++;
            else if (value.charAt(index) == separator) {
                result.add(value.substring(start, index));
                start = index + 1;
            }
        }
        result.add(value.substring(start));
        return result;
    }

    private String field(List<String> fields, int index) {
        return fields.size() > index ? fields.get(index) : "";
    }

    private boolean countMatches(String value, int expected) {
        return value.matches("[0-9]{1,10}") && Long.parseLong(value) == expected;
    }
}
