package com.edispark.integrationapplication.persistence;

import io.cottn.core.tenancy.TenantAccess;
import com.edispark.partnerships.infrastructure.PartnershipCreationAdapter;

import com.edispark.partnerships.domain.model.DocumentProfile;
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
@Profile("!openapi-export")
final class DynamoPartnershipRepository implements PartnershipRepository, PartnershipCreationAdapter {
    private final IntegrationDynamoStore store;
    private final ObjectMapper json;
    DynamoPartnershipRepository(IntegrationDynamoStore store, ObjectMapper json) { this.store = store; this.json = json; }

    @Override public TradingPartner savePartner(TradingPartner partner) { TenantAccess.requireOwner(partner.tenantId());
        store.put(partner.tenantId(), "PARTNER#" + partner.id(), "PARTNER", write(partner)); return partner;
    }
    @Override public Optional<TradingPartner> findPartner(UUID id) { String tenantId = TenantAccess.currentId();
        return store.get(tenantId, "PARTNER#" + id).map(value -> read(value, TradingPartner.class));
    }
    @Override public List<TradingPartner> listPartners() { String tenantId = TenantAccess.currentId();
        return store.list(tenantId, "PARTNER#").stream().map(value -> read(value, TradingPartner.class)).toList();
    }
    @Override public void deletePartner(UUID id) { String tenantId = TenantAccess.currentId(); store.delete(tenantId, "PARTNER#" + id); }
    @Override public TradingAgreement saveAgreement(TradingAgreement agreement) { TenantAccess.requireOwner(agreement.tenantId());
        store.put(agreement.tenantId(), "AGREEMENT#" + agreement.id(), "AGREEMENT", write(agreement)); return agreement;
    }
    @Override public Optional<TradingAgreement> findAgreement(UUID id) { String tenantId = TenantAccess.currentId();
        return store.get(tenantId, "AGREEMENT#" + id).map(value -> read(value, TradingAgreement.class));
    }
    @Override public List<TradingAgreement> listAgreements() { String tenantId = TenantAccess.currentId();
        return store.list(tenantId, "AGREEMENT#").stream().map(value -> read(value, TradingAgreement.class)).toList();
    }
    @Override public void deleteAgreement(UUID id) { String tenantId = TenantAccess.currentId(); store.delete(tenantId, "AGREEMENT#" + id); }
    @Override public DocumentProfile saveDocumentProfile(DocumentProfile profile) { TenantAccess.requireOwner(profile.tenantId());
        store.put(profile.tenantId(), "DOCUMENT_PROFILE#" + profile.id(), "DOCUMENT_PROFILE", write(profile));
        return profile;
    }
    @Override public Optional<DocumentProfile> findDocumentProfile(UUID id) { String tenantId = TenantAccess.currentId();
        return store.get(tenantId, "DOCUMENT_PROFILE#" + id).map(value -> read(value, DocumentProfile.class));
    }
    @Override public List<DocumentProfile> listDocumentProfiles(UUID partnerId) { String tenantId = TenantAccess.currentId();
        return store.list(tenantId, "DOCUMENT_PROFILE#").stream()
                .map(value -> read(value, DocumentProfile.class))
                .filter(item -> item.partnerId().equals(partnerId)).toList();
    }
    @Override public void deleteDocumentProfile(UUID id) { String tenantId = TenantAccess.currentId();
        store.delete(tenantId, "DOCUMENT_PROFILE#" + id);
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Could not serialize partnership", e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception e) { throw new IllegalStateException("Could not read partnership", e); } }
}
