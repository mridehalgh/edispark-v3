package com.edispark.partnerships.domain.repository;

import com.edispark.partnerships.domain.model.DocumentProfile;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.model.TradingPartner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnershipRepository extends PartnershipCreation {
    default TradingPartner insertPartnerDraft(TradingPartner draft) {
        return findPartner(draft.id()).orElseGet(() -> savePartner(draft));
    }
    default TradingAgreement insertAgreementDraft(TradingAgreement draft) {
        return findAgreement(draft.id()).orElseGet(() -> saveAgreement(draft));
    }
    TradingPartner savePartner(TradingPartner partner);
    Optional<TradingPartner> findPartner(UUID id);
    List<TradingPartner> listPartners();
    void deletePartner(UUID id);
    TradingAgreement saveAgreement(TradingAgreement agreement);
    Optional<TradingAgreement> findAgreement(UUID id);
    List<TradingAgreement> listAgreements();
    void deleteAgreement(UUID id);
    DocumentProfile saveDocumentProfile(DocumentProfile profile);
    Optional<DocumentProfile> findDocumentProfile(UUID id);
    List<DocumentProfile> listDocumentProfiles(UUID partnerId);
    void deleteDocumentProfile(UUID id);
}
