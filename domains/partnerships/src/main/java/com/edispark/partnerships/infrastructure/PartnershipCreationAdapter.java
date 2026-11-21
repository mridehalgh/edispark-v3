package com.edispark.partnerships.infrastructure;

import com.edispark.partnerships.domain.model.*;
import java.util.*;
import java.time.Instant;
import com.edispark.partnerships.domain.repository.PartnershipCreation;
import io.cottn.core.tenancy.TenantAccess;

public interface PartnershipCreationAdapter extends PartnershipCreation {
    default TradingPartner createPartner(String name, List<BusinessIdentity> identities, InboundFileFailurePolicy policy, Instant now) { return TradingPartner.create(TenantAccess.currentId(), name, identities, policy, now); }
    default TradingAgreement draftAgreement(UUID partnerId, String name, Instant now) { return TradingAgreement.draft(TenantAccess.currentId(), partnerId, name, now); }
    default DocumentProfile draftProfile(UUID partnerId, String name, ExchangeDirection direction, BusinessDocumentType type, List<DocumentFieldRule> rules, SsccGenerationMode generation, UUID issuerId, Instant now) { return DocumentProfile.draft(TenantAccess.currentId(), partnerId, name, direction, type, rules, generation, issuerId, now); }
}
