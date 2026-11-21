package com.edispark.partnerships.domain.repository;

import com.edispark.partnerships.domain.model.*;
import java.util.*;
import java.time.Instant;

/** Creates tenant-owned records without exposing tenant selection to business services. */
public interface PartnershipCreation {
    TradingPartner createPartner(String name, List<BusinessIdentity> identities, InboundFileFailurePolicy policy, Instant now);
    TradingAgreement draftAgreement(UUID partnerId, String name, Instant now);
    DocumentProfile draftProfile(UUID partnerId, String name, ExchangeDirection direction, BusinessDocumentType type, List<DocumentFieldRule> rules, SsccGenerationMode generation, UUID issuerId, Instant now);
}
