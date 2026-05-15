package com.example.documents.application.tradacoms;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.tradacoms.parser.TradacomsSyntax;

public class TradacomsMessageParser {

    public TradacomsParseResult parse(byte[] payload) {
        if (payload.length == 0) {
            return new TradacomsParseResult(ParseStatus.INVALID_SYNTAX, null, null, List.of("Payload is empty"));
        }

        List<Segment> segments = splitSegments(payload);
        if (segments.isEmpty()) {
            return new TradacomsParseResult(ParseStatus.INVALID_SYNTAX, null, null, List.of("Payload contains no segments"));
        }

        List<String> errors = new ArrayList<>();
        validateEnvelope(segments, errors);

        Segment messageHeader = findFirst(segments, TradacomsSyntax.MHD);
        if (messageHeader == null || messageHeader.elements().size() < 2) {
            errors.add("Missing MHD segment");
            return new TradacomsParseResult(ParseStatus.INVALID_SYNTAX, null, null, errors);
        }

        String messageType = messageHeader.elements().get(1).split(String.valueOf(TradacomsSyntax.COMPONENT_SEPARATOR), -1)[0];
        if (!"CREDIT".equals(messageType)) {
            return new TradacomsParseResult(
                    ParseStatus.UNSUPPORTED_MESSAGE,
                    messageType,
                    null,
                    List.of("Unsupported TRADACOMS message type: " + messageType));
        }

        Segment crf = findFirst(segments, "CRF");
        String businessDocumentNumber = crf != null && !crf.elements().isEmpty() ? crf.elements().getFirst() : null;

        validateCreditMessage(segments, errors);
        if (!errors.isEmpty()) {
            return new TradacomsParseResult(ParseStatus.INVALID_SYNTAX, messageType, businessDocumentNumber, errors);
        }

        return new TradacomsParseResult(ParseStatus.SUCCESS, messageType, businessDocumentNumber, List.of());
    }

    private List<Segment> splitSegments(byte[] payload) {
        String value = new String(payload, StandardCharsets.UTF_8);
        String[] rawSegments = value.split(Pattern.quote(String.valueOf(TradacomsSyntax.SEGMENT_TERMINATOR)));
        List<Segment> segments = new ArrayList<>();
        for (String rawSegment : rawSegments) {
            if (rawSegment.isBlank()) {
                continue;
            }
            int separatorIndex = rawSegment.indexOf(TradacomsSyntax.TAG_SEPARATOR);
            if (separatorIndex < 1) {
                return List.of();
            }
            String tag = rawSegment.substring(0, separatorIndex);
            String segmentData = rawSegment.substring(separatorIndex + 1);
            List<String> elements = List.of(segmentData.split(
                    Pattern.quote(String.valueOf(TradacomsSyntax.ELEMENT_SEPARATOR)),
                    -1));
            segments.add(new Segment(tag, elements));
        }
        return segments;
    }

    private void validateEnvelope(List<Segment> segments, List<String> errors) {
        if (!TradacomsSyntax.STX.equals(segments.getFirst().tag())) {
            errors.add("Missing STX segment");
        }
        if (!TradacomsSyntax.END.equals(segments.getLast().tag())) {
            errors.add("Missing END segment");
        }
    }

    private void validateCreditMessage(List<Segment> segments, List<String> errors) {
        if (findFirst(segments, TradacomsSyntax.CLO) == null) {
            errors.add("Missing CLO segment");
        }
        if (findFirst(segments, "CRF") == null) {
            errors.add("Missing CRF segment");
        }
        if (findFirst(segments, TradacomsSyntax.CLD) == null) {
            errors.add("Missing CLD segment");
        }

        int mhdIndex = indexOf(segments, TradacomsSyntax.MHD);
        int mtrIndex = indexOf(segments, TradacomsSyntax.MTR);
        if (mtrIndex < 0) {
            errors.add("Missing MTR segment");
            return;
        }

        Segment messageTrailer = segments.get(mtrIndex);
        if (messageTrailer.elements().isEmpty()) {
            errors.add("MTR segment is missing the segment count");
            return;
        }

        try {
            int expectedCount = Integer.parseInt(messageTrailer.elements().getFirst());
            int actualCount = mtrIndex - mhdIndex;
            if (expectedCount != actualCount) {
                errors.add("MTR segment count does not match message body");
            }
        } catch (NumberFormatException exception) {
            errors.add("MTR segment count is invalid");
        }
    }

    private Segment findFirst(List<Segment> segments, String tag) {
        return segments.stream().filter(segment -> tag.equals(segment.tag())).findFirst().orElse(null);
    }

    private int indexOf(List<Segment> segments, String tag) {
        for (int index = 0; index < segments.size(); index++) {
            if (tag.equals(segments.get(index).tag())) {
                return index;
            }
        }
        return -1;
    }

    private record Segment(String tag, List<String> elements) {
    }
}
