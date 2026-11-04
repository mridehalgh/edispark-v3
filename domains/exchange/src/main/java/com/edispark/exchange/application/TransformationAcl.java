package com.edispark.exchange.application;

import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.exchange.domain.ExchangeWarning;
import java.util.List;

public interface TransformationAcl {
    Result executePublished(ExchangeCapability capability, byte[] canonicalContent);

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
