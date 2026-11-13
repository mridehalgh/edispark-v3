package com.edispark.integrationapplication.acl;

import com.edispark.edifact.OrderResponse;
import com.edispark.edifact.OrderResponseD96a;
import com.edispark.edifact.OrderResponseValidation;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** JSON is only the boundary representation; the standards module owns every encoding rule. */
@Component
public final class OrderResponseTargetContract {
    private final ObjectMapper mapper;
    private final OrderResponseD96a codec = new OrderResponseD96a();

    public OrderResponseTargetContract(ObjectMapper mapper) { this.mapper = mapper; }

    public OrderResponseValidation validate(byte[] json) {
        try {
            var model = read(json); var validation = codec.validate(model);
            if (validation.valid()) codec.write(model, "QUALIFY", Instant.parse("2026-01-01T00:00:00Z"));
            return validation;
        }
        catch (OrderResponseValidation.InvalidOrderResponse exception) { return exception.report(); }
        catch (Exception exception) {
            return new OrderResponseValidation(List.of(new OrderResponseValidation.Issue("TARGET_CONTRACT_INVALID", "/",
                    "The mapping must produce the supported Order Response contract with no unrepresented fields.")));
        }
    }

    public String write(byte[] json, String control, Instant now) {
        try { return codec.write(read(json), control, now); }
        catch (OrderResponseValidation.InvalidOrderResponse exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("The mapped Order Response contract is invalid.", exception); }
    }

    private OrderResponse read(byte[] json) throws java.io.IOException {
        return mapper.readerFor(OrderResponse.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
    }
}
