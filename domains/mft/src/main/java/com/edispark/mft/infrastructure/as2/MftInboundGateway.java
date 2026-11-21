package com.edispark.mft.infrastructure.as2;

import com.edispark.mft.application.MftNotFoundException;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.infrastructure.persistence.MftDiscovery;
import io.cottn.core.tenancy.TenantExecution;
import java.io.IOException;

/** Trusted AS2 ingress selects ownership before invoking tenant-scoped business behavior. */
public record MftInboundGateway(MftDiscovery partnerships, MftService service) {
    public Transfer receiveInbound(String localAs2Id, String remoteAs2Id, String messageId,
                                   String filename, String contentType, byte[] content) throws IOException {
        var partner = partnerships.findInboundPartner(localAs2Id, remoteAs2Id)
                .orElseThrow(() -> new MftNotFoundException("Inbound AS2 partnership was not found"));
        return TenantExecution.io(partner.tenantId(), () ->
                service.receiveInbound(partner, messageId, filename, contentType, content));
    }
}
