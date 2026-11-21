package com.edispark.mft.infrastructure.persistence;

import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.MdnDelivery;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.model.TransferStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Privileged infrastructure capability; never inject into ordinary business services. */
public interface MftDiscovery {
    Optional<PartnerProfile> findInboundPartner(String localAs2Id, String remoteAs2Id);
    List<Transfer> listPendingTransfers(Instant dueAt, int limit);
    List<MdnDelivery> listPendingMdnDeliveries(Instant dueAt, int limit);
}
