package com.edispark.partnerships.infrastructure;

import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.model.TradingPartner;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "openapi-export"})
public final class InMemoryPartnershipRepository implements PartnershipRepository {
    private final Map<UUID, TradingPartner> partners = new ConcurrentHashMap<>();
    private final Map<UUID, TradingAgreement> agreements = new ConcurrentHashMap<>();

    @Override public TradingPartner savePartner(TradingPartner partner) { partners.put(partner.id(), partner); return partner; }
    @Override public Optional<TradingPartner> findPartner(String tenantId, UUID id) { return Optional.ofNullable(partners.get(id)).filter(item -> item.tenantId().equals(tenantId)); }
    @Override public List<TradingPartner> listPartners(String tenantId) { return partners.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList(); }
    @Override public void deletePartner(String tenantId, UUID id) { partners.computeIfPresent(id, (key, value) -> value.tenantId().equals(tenantId) ? null : value); }
    @Override public TradingAgreement saveAgreement(TradingAgreement agreement) { agreements.put(agreement.id(), agreement); return agreement; }
    @Override public Optional<TradingAgreement> findAgreement(String tenantId, UUID id) { return Optional.ofNullable(agreements.get(id)).filter(item -> item.tenantId().equals(tenantId)); }
    @Override public List<TradingAgreement> listAgreements(String tenantId) { return agreements.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList(); }
    @Override public void deleteAgreement(String tenantId, UUID id) { agreements.computeIfPresent(id, (key, value) -> value.tenantId().equals(tenantId) ? null : value); }
}
