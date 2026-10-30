package com.edispark.mftapplication.as2;

import com.edispark.mft.application.CertificateMaterial;
import com.edispark.mft.application.CertificateService;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.repository.MftRepository;
import com.helger.phase2.cert.CertificateFactory;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.partner.Partnership;
import com.helger.phase2.partner.SelfFillingPartnershipFactory;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

final class MftAs2PartnershipFactory extends SelfFillingPartnershipFactory {
    private static final char[] RUNTIME_PASSWORD = "runtime-only".toCharArray();
    private final MftRepository repository;
    private final CertificateService certificateService;
    private final CertificateFactory certificateFactory;

    MftAs2PartnershipFactory(MftRepository repository, CertificateService certificateService,
                             CertificateFactory certificateFactory) {
        this.repository = repository;
        this.certificateService = certificateService;
        this.certificateFactory = certificateFactory;
    }

    @Override
    protected void onBeforeAddPartnership(Partnership partnership) throws AS2Exception {
        super.onBeforeAddPartnership(partnership);
        PartnerProfile partner = repository.findInboundPartner(partnership.getReceiverAS2ID(),
                        partnership.getSenderAS2ID())
                .orElseThrow(() -> new AS2Exception("Unknown inbound AS2 partnership"));
        try {
            loadIdentity(partner);
            loadPartnerCertificate(partner);
            partnership.setReceiverX509Alias(partner.localAs2Id());
            partnership.setSenderX509Alias(partner.remoteAs2Id());
        } catch (Exception exception) {
            throw new AS2Exception("Unable to load partnership certificate material", exception);
        }
    }

    private void loadIdentity(PartnerProfile partner) throws Exception {
        CertificateMaterial material = certificateService.load(partner.tenantId(), partner.localCertificateId());
        KeyStore source = KeyStore.getInstance("PKCS12");
        source.load(new ByteArrayInputStream(material.bytes()), material.password().toCharArray());
        PrivateKey key = (PrivateKey) source.getKey(material.alias(), material.password().toCharArray());
        Certificate[] chain = source.getCertificateChain(material.alias());
        synchronized (certificateFactory) {
            certificateFactory.getKeyStore().setKeyEntry(partner.localAs2Id(), key, RUNTIME_PASSWORD, chain);
        }
    }

    private void loadPartnerCertificate(PartnerProfile partner) throws Exception {
        CertificateMaterial material = certificateService.load(partner.tenantId(), partner.partnerCertificateId());
        java.security.cert.CertificateFactory parser = java.security.cert.CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) parser.generateCertificate(new ByteArrayInputStream(material.bytes()));
        synchronized (certificateFactory) {
            certificateFactory.getKeyStore().setCertificateEntry(partner.remoteAs2Id(), certificate);
        }
    }
}
