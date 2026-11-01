package com.edispark.partnerships.domain.repository;

import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.model.TradingPartner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnershipRepository {
    TradingPartner savePartner(TradingPartner partner);
    Optional<TradingPartner> findPartner(String tenantId, UUID id);
    List<TradingPartner> listPartners(String tenantId);
    TradingAgreement saveAgreement(TradingAgreement agreement);
    Optional<TradingAgreement> findAgreement(String tenantId, UUID id);
    List<TradingAgreement> listAgreements(String tenantId);
}
