package com.edispark.integrationapplication.acl;

import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class EdiPayloadWriter {
    private static final DateTimeFormatter EDIFACT_DATE = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter EDIFACT_TIME = DateTimeFormatter.ofPattern("HHmm").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TRADACOMS_DATE = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TRADACOMS_TIME = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC);
    private final ObjectMapper mapper;
    private final Clock clock;

    public EdiPayloadWriter(ObjectMapper mapper, Clock clock) { this.mapper = mapper; this.clock = clock; }

    public byte[] write(ExchangeCapability capability, byte[] mappedJson) {
        try {
            JsonNode document = mapper.readTree(mappedJson);
            String payload = capability.standard() == EdiStandard.EDIFACT
                    ? edifact(capability, document) : tradacoms(capability, document);
            return payload.getBytes(StandardCharsets.US_ASCII);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Mapped EDI model is invalid", exception);
        }
    }

    private String edifact(ExchangeCapability capability, JsonNode document) {
        String control = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        String reference = text(document, "/document/id", control);
        String issueDate = text(document, "/document/issueDate", "").replace("-", "");
        String sender = text(document, "/parties/sender", "EDISPARK");
        String receiver = text(document, "/parties/receiver", "PARTNER");
        String bgmCode = capability.documentType() == BusinessDocumentType.INVOICE ? "380" : "220";
        StringBuilder result = new StringBuilder();
        result.append("UNB+UNOC:3+").append(edi(sender)).append('+').append(edi(receiver)).append('+')
                .append(EDIFACT_DATE.format(clock.instant())).append(':').append(EDIFACT_TIME.format(clock.instant()))
                .append('+').append(control).append('\'');
        String directoryRelease = capability.release().startsWith("D")
                ? capability.release().substring(1) : capability.release();
        result.append("UNH+1+").append(edi(capability.messageType())).append(":D:")
                .append(edi(directoryRelease)).append(":UN'");
        result.append("BGM+").append(bgmCode).append('+').append(edi(reference)).append("+9'");
        int segmentCount = 3;
        if (!issueDate.isBlank()) {
            result.append("DTM+137:").append(edi(issueDate)).append(":102'");
            segmentCount++;
        }
        result.append("UNT+").append(segmentCount).append("+1'");
        result.append("UNZ+1+").append(control).append('\'');
        return result.toString();
    }

    private String tradacoms(ExchangeCapability capability, JsonNode document) {
        String control = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        String reference = text(document, "/document/id", control);
        String sender = text(document, "/parties/sender", "EDISPARK");
        String receiver = text(document, "/parties/receiver", "PARTNER");
        String message = capability.documentType() == BusinessDocumentType.INVOICE ? "INVFIL" : "ORDHDR";
        return "STX=ANA:1+" + edi(sender) + '+' + edi(receiver) + '+' + TRADACOMS_DATE.format(clock.instant()) + ':'
                + TRADACOMS_TIME.format(clock.instant()) + '+' + control + "++" + message + "'"
                + "MHD=1+" + message + ':' + edi(capability.release()) + "'"
                + "TYP=" + (capability.documentType() == BusinessDocumentType.INVOICE ? "0700" : "0430") + "'"
                + "FIL=" + edi(reference) + "+1'"
                + "MTR=4'"
                + "END=1'";
    }

    private String text(JsonNode document, String pointer, String fallback) {
        JsonNode value = document.at(pointer);
        return value.isValueNode() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    private String edi(String value) {
        if (value == null) return "";
        String ascii = value.replaceAll("[^\\x20-\\x7E]", "").strip();
        if (ascii.length() > 70) ascii = ascii.substring(0, 70);
        return ascii.replace("?", "??").replace("+", "?+").replace(":", "?:").replace("'", "?'");
    }
}
