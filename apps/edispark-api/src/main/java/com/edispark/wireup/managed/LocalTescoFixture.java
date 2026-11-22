package com.edispark.wireup.managed;

import com.edispark.documents.application.command.CreateDocumentSetCommand;
import com.edispark.documents.application.handler.DocumentSetCommandHandler;
import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.DocumentSet;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.documents.domain.model.Format;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import com.edispark.identifiers.tenant.TenantId;
import com.edispark.integrationapplication.seed.LocalIntegrationDemoSeeder;
import com.edispark.mft.application.MftService;
import com.edispark.partnerships.application.PartnershipService;
import com.edispark.partnerships.domain.model.AgreementStatus;
import com.edispark.partnerships.domain.model.BusinessIdentity;
import com.edispark.partnerships.domain.model.PartnerStatus;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@Profile("local & !openapi-export")
@ConditionalOnProperty(name = "integration.local-demo.enabled", havingValue = "true", matchIfMissing = true)
final class LocalTescoFixture {
    static final String KEY = "tesco-uk-depot-v1";
    private static final UUID REQUEST = UUID.fromString("c59a1bca-edd3-4b73-8b3e-c216984feb05");
    private final ManagedSetup setup;
    private final MftService mft;
    private final PartnershipService partnerships;
    private final DocumentSetRepository sets;
    private final DocumentSetCommandHandler documents;

    LocalTescoFixture(ManagedSetup setup, MftService mft, PartnershipService partnerships,
                      DocumentSetRepository sets, DocumentSetCommandHandler documents) {
        this.setup = setup;
        this.mft = mft;
        this.partnerships = partnerships;
        this.sets = sets;
        this.documents = documents;
    }

    @EventListener
    void reconcile(LocalIntegrationDemoSeeder.Ready ready) {
        if (ready.edifactConnectionId() == null) return;
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(ready.tenantId())),
                () -> reconcile(ready.edifactConnectionId()));
    }

    private void reconcile(UUID sourceConnection) {
        var source = mft.persistedConnection(sourceConnection);
        var request = new ManagedSetup.Request(REQUEST, new BusinessIdentity("GLN", "0501000000015"),
                "EDISPARK", "SIMULATOR_TESCO", source.endpoint(), null, source.localCertificateId(),
                source.partnerSigningCertificateId(), source.partnerEncryptionCertificateId());
        var result = setup.apply(ManagedCatalogueSeed.TESCO, 1, request);
        var connection = mft.persistedConnection(result.connectionId());
        if (connection.status() == com.edispark.mft.domain.model.PartnerStatus.DRAFT && connection.version() == 1) {
            mft.activatePartner(connection.id());
        }
        var partner = partnerships.getPartner(result.partnerId());
        if (partner.status() == PartnerStatus.DRAFT && partner.version() == 1) {
            partnerships.activatePartner(partner.id(), null);
        }
        var agreement = partnerships.getAgreement(result.agreementId());
        if (agreement.status() == AgreementStatus.DRAFT && agreement.version() == 1) {
            var qualified = partnerships.qualify(agreement.id());
            if (!qualified.qualification().passed()) throw new IllegalStateException("The Tesco local agreement failed qualification.");
            partnerships.activate(agreement.id());
        }
        ensureDocument(DocumentType.INVOICE, "Invoice", "42901", partner.id());
        ensureDocument(DocumentType.DESPATCH_ADVICE, "DespatchAdvice", "42900", partner.id());
    }

    private void ensureDocument(DocumentType type, String root, String number, UUID partnerId) {
        var existing = sets.findAll();
        if (existing.stream().anyMatch(set -> KEY.equals(set.metadata().get("fixtureVersion"))
                && number.equals(set.metadata().get("businessDocumentNumber")))) return;
        var reference = existing.stream().map(DocumentSet::getAllDocuments).flatMap(java.util.Collection::stream)
                .filter(document -> document.type() == type).findFirst().orElseThrow().schemaRef();
        byte[] content;
        try (var input = new ClassPathResource("managed-demo/" + root + ".json").getInputStream()) {
            content = input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("The Tesco local document sample could not be loaded.", exception);
        }
        var validation = documents.validateCanonical(type, content);
        if (!validation.valid()) {
            throw new IllegalStateException("The Tesco local UBL sample did not validate: " + validation.errors());
        }
        documents.handle(new CreateDocumentSetCommand(type, reference, Content.of(content, Format.JSON), "local-seeder",
                Map.of("fixtureVersion", KEY, "businessDocumentNumber", number, "name", "Tesco UK · " + root + " " + number,
                        "partnerId", partnerId.toString(), "direction", "OUTBOUND", "standard", "UBL", "ublVersion", "2.1")));
    }
}
