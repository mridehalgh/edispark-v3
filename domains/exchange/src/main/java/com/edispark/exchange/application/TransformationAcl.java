package com.edispark.exchange.application;

import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.exchange.domain.ExchangeWarning;
import java.util.List;

public interface TransformationAcl {
    Result executePublished(ExchangeCapability capability, byte[] canonicalContent);
    default Result prepareForDispatch(ExchangeCapability capability, byte[] canonicalContent, java.util.UUID exchangeId, Result checked) { return checked; }

    default Qualification qualification(ExchangeCapability capability) { return new Qualification(true, ""); }
    record Qualification(boolean valid, String identity) { }

    record Result(byte[] payload, byte[] report, List<ExchangeWarning> warnings) {
        public Result {
            payload = payload.clone();
            report = report.clone();
            warnings = List.copyOf(warnings);
        }
        @Override public byte[] payload() { return payload.clone(); }
        @Override public byte[] report() { return report.clone(); }
    }
}
