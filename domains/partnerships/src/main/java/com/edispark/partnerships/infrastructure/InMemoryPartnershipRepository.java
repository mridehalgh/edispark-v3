package com.edispark.partnerships.infrastructure;

import io.cottn.core.tenancy.TenantAccess;

import com.edispark.partnerships.domain.model.DocumentProfile;
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
@Profile("openapi-export")
public final class InMemoryPartnershipRepository implements PartnershipRepository, PartnershipCreationAdapter {
    private final Map<String, TradingPartner> partners = new ConcurrentHashMap<>();
    private final Map<String, TradingAgreement> agreements = new ConcurrentHashMap<>();
    private final Map<String, DocumentProfile> documentProfiles = new ConcurrentHashMap<>();

    @Override public TradingPartner savePartner(TradingPartner partner) { TenantAccess.requireOwner(partner.tenantId()); partners.put(partner.tenantId() + ":" + partner.id(), partner); return partner; }
    @Override public Optional<TradingPartner> findPartner(UUID id) { String tenantId = TenantAccess.currentId(); return Optional.ofNullable(partners.get(tenantId + ":" + id)).filter(item -> item.tenantId().equals(tenantId)); }
    @Override public List<TradingPartner> listPartners() { String tenantId = TenantAccess.currentId(); return partners.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList(); }
    @Override public void deletePartner(UUID id) { String tenantId = TenantAccess.currentId(); partners.computeIfPresent(tenantId + ":" + id, (key, value) -> value.tenantId().equals(tenantId) ? null : value); }
    @Override public TradingAgreement saveAgreement(TradingAgreement agreement) { TenantAccess.requireOwner(agreement.tenantId()); agreements.put(agreement.tenantId() + ":" + agreement.id(), agreement); return agreement; }
    @Override public Optional<TradingAgreement> findAgreement(UUID id) { String tenantId = TenantAccess.currentId(); return Optional.ofNullable(agreements.get(tenantId + ":" + id)).filter(item -> item.tenantId().equals(tenantId)); }
    @Override public List<TradingAgreement> listAgreements() { String tenantId = TenantAccess.currentId(); return agreements.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList(); }
    @Override public void deleteAgreement(UUID id) { String tenantId = TenantAccess.currentId(); agreements.computeIfPresent(tenantId + ":" + id, (key, value) -> value.tenantId().equals(tenantId) ? null : value); }
    @Override public DocumentProfile saveDocumentProfile(DocumentProfile profile) { TenantAccess.requireOwner(profile.tenantId()); documentProfiles.put(profile.tenantId() + ":" + profile.id(), profile); return profile; }
    @Override public Optional<DocumentProfile> findDocumentProfile(UUID id) { String tenantId = TenantAccess.currentId(); return Optional.ofNullable(documentProfiles.get(tenantId + ":" + id)).filter(item -> item.tenantId().equals(tenantId)); }
    @Override public List<DocumentProfile> listDocumentProfiles(UUID partnerId) { String tenantId = TenantAccess.currentId(); return documentProfiles.values().stream().filter(item -> item.tenantId().equals(tenantId) && item.partnerId().equals(partnerId)).toList(); }
    @Override public void deleteDocumentProfile(UUID id) { String tenantId = TenantAccess.currentId(); documentProfiles.computeIfPresent(tenantId + ":" + id, (key, value) -> value.tenantId().equals(tenantId) ? null : value); }
}
