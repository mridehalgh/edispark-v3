package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import com.edispark.edifact.EdifactMessageStructure;
import com.edispark.edifact.EdifactMessageStructure.Entry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class MigStructureComparison {
    private static final int MAX_ROWS = 300;
    private static final Pattern EXCEL_ADDRESS = Pattern.compile("^[A-Z]{1,3}[1-9][0-9]*\\s+");
    private static final Pattern OCR_CELL = Pattern.compile("^cell [1-9][0-9]*\\s*");
    private static final Pattern ROW = Pattern.compile("(?i)^(?:(STD) )?([0-9]{1,4}) "
            + "(?:SEGMENT GROUP ?([1-9][0-9]?)|(SG[1-9][0-9]?|[A-Z]{3})) "
            + "(?:.*? )?(M|C|R|O|D|N|MANDATORY|CONDITIONAL|REQUIRED|OPTIONAL|DEPENDENT|NOT USED) "
            + "([0-9]{1,9}|[0-9]{1,3}(?:,[0-9]{3}){1,2})(?: (.*))?$");
    private static final Pattern POSITION_HEADING = Pattern.compile(
            "^(?:(UNECE |DIRECTORY ))?(?:POS|POSITION) (?:TAG|SEGMENT)(?: |$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern USAGE_NOTE = Pattern.compile("(?i)(must use|not used|dependent|used)$");
    private static final Set<String> ENVELOPE = Set.of("UNA", "UNB", "UNZ", "UNG", "UNE");

    static StandardsReview compare(Coordinates target, List<Section> sections, boolean releaseMismatch) {
        var directory = "EDIFACT".equals(target.standard())
                ? EdifactMessageStructure.find(target.release(), target.messageType()).orElse(null) : null;
        if (directory == null) return new StandardsReview(StructureState.DIRECTORY_UNAVAILABLE, null, null, 0, List.of());
        if (releaseMismatch) return new StandardsReview(StructureState.GUIDE_RELEASE_MISMATCH,
                directory.source(), directory.digest(), 0, List.of());
        var rows = compareRows(directory.entries(), sections);
        // Keep differences ahead of confirmations when a large guide reaches the report limit.
        var retained = rows.stream().sorted(Comparator.comparingInt(MigStructureComparison::priority)).limit(MAX_ROWS).toList();
        return new StandardsReview(StructureState.COMPARED, directory.source(), directory.digest(), rows.size() - retained.size(), retained);
    }

    private static int priority(StructureComparison row) {
        return switch (row.result()) {
            case CONFLICT -> 0;
            case NEEDS_REVIEW, PARTNER_RESTRICTION -> 1;
            case MATCH, ENVELOPE -> 2;
        };
    }

    private static List<StructureComparison> compareRows(List<Entry> entries, List<Section> sections) {
        var rows = new ArrayList<StructureComparison>();
        var contexts = new HashMap<String, Context>();
        var ocrTables = new HashSet<String>();
        sections.stream().map(Section::anchor).filter(anchor -> anchor.contains(", OCR table "))
                .map(anchor -> anchor.substring(0, anchor.indexOf(", OCR table "))).forEach(ocrTables::add);
        for (var section : sections) {
            String anchor = section.anchor();
            if (anchor.contains(", OCR line ") && ocrTables.contains(anchor.substring(0, anchor.indexOf(", OCR line ")))) continue;
            var context = contexts.computeIfAbsent(contextKey(anchor), ignored -> new Context());
            compareRow(entries, section, context, rows);
        }
        return rows;
    }

    private static String contextKey(String anchor) {
        String[] coordinates = anchor.split("/", 3);
        if (coordinates.length < 3) return anchor;
        String prefix = coordinates[0] + "/" + coordinates[1];
        String location = coordinates[2];
        if (location.startsWith("sheet ") || location.contains(", OCR table ")) {
            return prefix + "/" + location.split(", row [0-9]+", 2)[0];
        }
        return prefix;
    }

    private static void compareRow(List<Entry> entries, Section section, Context context, List<StructureComparison> rows) {
        String line = normalized(section);
        String area = switch (line.toUpperCase(Locale.ROOT).replaceFirst(":$", "")) {
            case "HEADER", "HEADING" -> "HEADER";
            case "DETAIL" -> "DETAIL";
            case "SUMMARY" -> "SUMMARY";
            case "NOT DEFINED", "ENVELOPE" -> "ENVELOPE";
            default -> null;
        };
        if (area != null) {
            context.area = area;
            context.groupPath = "";
            return;
        }
        var heading = POSITION_HEADING.matcher(line);
        if (heading.find()) {
            context.officialPositions = heading.group(1) != null;
            return;
        }
        var match = ROW.matcher(line);
        if (!match.matches()) return;
        boolean officialPosition = match.group(1) != null || context.officialPositions;
        var guide = guide(section, context.area, match);
        if (ENVELOPE.contains(guide.tag()) && !officialPosition) {
            rows.add(result(guide, null, StructureResult.ENVELOPE, "Interchange envelope outside the ORDERS message table."));
            return;
        }
        var candidates = candidates(entries, context, guide, officialPosition);
        var row = resolve(guide, candidates, officialPosition);
        rows.add(row);
        if (guide.tag().startsWith("SG")) {
            var standard = row.standard();
            context.groupPath = standard != null && standard.tag().equals(guide.tag())
                    && (context.area == null || context.area.equals(standard.area()))
                    ? path(standard.groupPath(), guide.tag()) : null;
        }
    }

    private static String normalized(Section section) {
        String location = section.anchor();
        var prefixes = location.matches("^[^/]+/[^/]+/sheet .*") ? EXCEL_ADDRESS : location.contains(", OCR table ") ? OCR_CELL : null;
        if (prefixes == null) return section.excerpt().strip().replaceAll("[\\s\\p{Z}|]+", " ");
        return String.join(" ", java.util.Arrays.stream(section.excerpt().split(" \\| ", -1))
                .map(cell -> prefixes.matcher(cell).replaceFirst("")).toList()).strip().replaceAll("[\\s\\p{Z}]+", " ");
    }

    private static GuideStructure guide(Section section, String area, java.util.regex.Matcher match) {
        String tag = match.group(3) == null ? match.group(4).toUpperCase(Locale.ROOT) : "SG" + match.group(3);
        String max = match.group(6).replace(",", "");
        String position = match.group(2);
        return new GuideStructure(area, "0".repeat(4 - position.length()) + position, tag,
                usage(match.group(5)), Integer.parseInt(max), usageNote(match.group(7)), section.anchor());
    }

    private static String usage(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "M", "MANDATORY", "R", "REQUIRED" -> "M";
            case "N", "NOT USED" -> "N";
            default -> "C";
        };
    }

    private static String usageNote(String trailing) {
        if (trailing == null) return null;
        var note = USAGE_NOTE.matcher(trailing.strip());
        if (!note.find()) return null;
        return switch (note.group().toUpperCase(Locale.ROOT)) {
            case "MUST USE" -> "Must use";
            case "NOT USED" -> "Not used";
            case "DEPENDENT" -> "Dependent";
            default -> "Used";
        };
    }

    private static List<Entry> candidates(List<Entry> entries, Context context, GuideStructure guide, boolean officialPosition) {
        if (officialPosition) return entries.stream().filter(entry -> entry.position().equals(guide.position())).toList();
        return entries.stream().filter(entry -> entry.tag().equals(guide.tag()))
                .filter(entry -> context.area == null || entry.area().equals(context.area))
                .filter(entry -> guide.tag().startsWith("SG") || matchesGroup(entry, context)).toList();
    }

    private static boolean matchesGroup(Entry entry, Context context) {
        if (context.groupPath == null) return context.area == null;
        return entry.groupPath().equals(context.groupPath);
    }

    private static StructureComparison resolve(GuideStructure guide, List<Entry> candidates, boolean officialPosition) {
        if (candidates.isEmpty()) {
            return result(guide, null, officialPosition ? StructureResult.CONFLICT : StructureResult.NEEDS_REVIEW,
                    officialPosition ? "The stated directory position does not exist." : "No directory occurrence resolves in this area or group.");
        }
        if (candidates.size() != 1) {
            return result(guide, null, StructureResult.NEEDS_REVIEW, "This tag occurs in several groups. Confirm the directory position or group.");
        }
        var entry = candidates.getFirst();
        if (!entry.tag().equals(guide.tag()) || guide.area() != null && !entry.area().equals(guide.area())) {
            return result(guide, entry, StructureResult.CONFLICT, "The stated directory position identifies a different tag or message area.");
        }
        String effective = switch (guide.usageNote() == null ? "" : guide.usageNote()) {
            case "Must use" -> "M";
            case "Not used" -> "N";
            case "Dependent" -> "C";
            default -> guide.usage();
        };
        if (!"N".equals(effective) && guide.maxRepeats() > entry.maxRepeats()) {
            return result(guide, entry, StructureResult.CONFLICT, "The guide allows more repeats than the directory.");
        }
        boolean excluded = "N".equals(effective) || guide.maxRepeats() == 0;
        if ("M".equals(entry.usage()) && (excluded || !"M".equals(effective))) {
            return result(guide, entry, officialPosition ? StructureResult.CONFLICT : StructureResult.NEEDS_REVIEW,
                    "The directory requires this occurrence. Check whether the guide row describes only a qualified case.");
        }
        if (excluded || guide.maxRepeats() < entry.maxRepeats() || !entry.usage().equals(effective)) {
            return result(guide, entry, StructureResult.PARTNER_RESTRICTION, "The guide narrows usage or repeats within the directory limits.");
        }
        return result(guide, entry, StructureResult.MATCH, "Usage and repeat limit agree.");
    }

    private static StructureComparison result(GuideStructure guide, Entry entry, StructureResult result, String detail) {
        var standard = entry == null ? null : new StandardStructure(
                entry.position(), entry.area(), entry.groupPath(), entry.tag(), entry.usage(), entry.maxRepeats());
        return new StructureComparison(guide, standard, result, detail);
    }

    private static String path(String parent, String group) { return parent.isEmpty() ? group : parent + "/" + group; }

    private static final class Context {
        String area;
        String groupPath;
        boolean officialPositions;
    }
}
