package com.edispark.integrationapplication.acl;

import com.edispark.tradacoms.ack.BqAcknowledgement;
import com.edispark.tradacoms.ack.BqAcknowledgementV14;
import com.edispark.tradacoms.ack.BqAcknowledgementValidation;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class BqAcknowledgementTargetContract {
    private final ObjectMapper json;
    private final BqAcknowledgementV14 codec = new BqAcknowledgementV14();
    public BqAcknowledgementTargetContract(ObjectMapper json) { this.json = json; }

    public BqAcknowledgementValidation validate(byte[] bytes) {
        try {
            var model = read(bytes); var validation = codec.validate(model);
            if (validation.valid()) codec.write(model, new BqAcknowledgement.Control(1, 1), Instant.parse("2026-01-01T00:00:00Z"));
            return validation;
        } catch (BqAcknowledgementValidation.InvalidAcknowledgement exception) { return exception.validation(); }
        catch (Exception exception) { return new BqAcknowledgementValidation(List.of(new BqAcknowledgementValidation.Issue("BQ_ACK_CONTRACT_INVALID", "/", "Map the document to the B&Q ACK v1.4 contract without unrepresented fields."))); }
    }

    public String write(byte[] bytes, BqAcknowledgement.Control control, Instant at) {
        try { return codec.write(read(bytes), control, at); }
        catch (BqAcknowledgementValidation.InvalidAcknowledgement exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("The mapped B&Q ACK contract is invalid.", exception); }
    }

    private BqAcknowledgement read(byte[] bytes) throws java.io.IOException {
        return json.readerFor(BqAcknowledgement.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(bytes);
    }
}
