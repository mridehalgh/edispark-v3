package com.edispark.mft.application;

import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;

public interface As2ExchangePort {
    As2DeliveryResult send(PartnerProfile partner, Transfer transfer, byte[] payload,
                           CertificateMaterial localIdentity, CertificateMaterial partnerCertificate);
}
