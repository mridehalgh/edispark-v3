package com.edispark.documents.infrastructure.validation;

import com.edispark.documents.domain.model.ValidationError;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** EdiSpark authoring meanings. Partner-specific restrictions belong to route validation. */
final class OrderResponseConsistency {
    private static final Map<String, String> EXPECTED = Map.of("AB", "ACKNOWLEDGED", "AP", "ACCEPTED", "RE", "REJECTED");

    static List<ValidationError> validate(JsonNode input) {
        List<ValidationError> errors = new ArrayList<>();
        JsonNode response = input.path("OrderResponse").path(0);
        String expected = EXPECTED.get(response.at("/OrderResponseCode/0/_").asText());
        if (expected == null) return errors;
        for (JsonNode line : response.path("OrderLine")) {
            String status = line.at("/LineItem/0/LineStatusCode/0/_").asText().toUpperCase(Locale.ROOT);
            if (!status.isBlank() && !status.equals(expected)) {
                errors.add(ValidationError.of("/OrderResponse/0/OrderResponseCode/0/_",
                        "The overall response and line decisions disagree. Use AP to accept the order and all lines, AB for receipt only, or RE to reject it.",
                        "RESPONSE_STATUS_CONFLICT"));
                return errors;
            }
        }
        return errors;
    }
}
