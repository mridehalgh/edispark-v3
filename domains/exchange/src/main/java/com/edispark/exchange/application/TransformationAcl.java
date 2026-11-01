package com.edispark.exchange.application;

import com.edispark.partnerships.domain.model.ExchangeCapability;
import java.util.UUID;

public interface TransformationAcl {
    byte[] executePublished(ExchangeCapability capability, byte[] canonicalContent);
}
