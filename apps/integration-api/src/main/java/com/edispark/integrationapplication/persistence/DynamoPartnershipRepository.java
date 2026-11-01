package com.edispark.integrationapplication.persistence;

import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.model.TradingPartner;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local & !openapi-export")
final class DynamoPartnershipRepository implements PartnershipRepository {
    private final IntegrationDynamoStore store;
    private final ObjectMapper json;
    DynamoPartnershipRepository(IntegrationDynamoStore store, ObjectMapper json) { this.store = store; this.json = json; }

    @Override public TradingPartner savePartner(TradingPartner partner) {
        store.put(partner.tenantId(), "PARTNER#" + partner.id(), "PARTNER", write(partner)); return partner;
    }
    @Override public Optional<TradingPartner> findPartner(String tenantId, UUID id) {
        return store.get(tenantId, "PARTNER#" + id).map(value -> read(value, TradingPartner.class));
    }
    @Override public List<TradingPartner> listPartners(String tenantId) {
        return store.list(tenantId, "PARTNER#").stream().map(value -> read(value, TradingPartner.class)).toList();
    }
    @Override public TradingAgreement saveAgreement(TradingAgreement agreement) {
        store.put(agreement.tenantId(), "AGREEMENT#" + agreement.id(), "AGREEMENT", write(agreement)); return agreement;
    }
    @Override public Optional<TradingAgreement> findAgreement(String tenantId, UUID id) {
        return store.get(tenantId, "AGREEMENT#" + id).map(value -> read(value, TradingAgreement.class));
    }
    @Override public List<TradingAgreement> listAgreements(String tenantId) {
        return store.list(tenantId, "AGREEMENT#").stream().map(value -> read(value, TradingAgreement.class)).toList();
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Could not serialize partnership", e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception e) { throw new IllegalStateException("Could not read partnership", e); } }
}
