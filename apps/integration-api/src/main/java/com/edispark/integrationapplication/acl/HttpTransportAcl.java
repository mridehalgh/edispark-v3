package com.edispark.integrationapplication.acl;

import com.edispark.exchange.application.TransportAcl;
import com.edispark.exchange.domain.DocumentReference;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Component
@Profile("distributed")
public final class HttpTransportAcl implements TransportAcl {
    private final RestClient mft;
    public HttpTransportAcl(@Qualifier("mftRestClient") RestClient mft) { this.mft = mft; }

    @Override public UUID submit(UUID connectionId, String idempotencyKey, String filename, String contentType,
            byte[] content, UUID exchangeId, UUID agreementId, int agreementRevision, UUID capabilityId,
            DocumentReference sourceDocument, UUID mappingRevisionId) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new NamedBytes(content, filename), MediaType.parseMediaType(contentType));
        body.part("exchangeId", exchangeId.toString());
        body.part("agreementId", agreementId.toString());
        body.part("agreementRevision", Integer.toString(agreementRevision));
        body.part("capabilityId", capabilityId.toString());
        body.part("documentSetId", sourceDocument.documentSetId().toString());
        body.part("documentId", sourceDocument.documentId().toString());
        body.part("mappingRevisionId", mappingRevisionId.toString());
        Transfer response = mft.post().uri(builder -> builder.path("/api/mft/transfers")
                        .queryParam("partnerId", connectionId).build())
                .header("Idempotency-Key", idempotencyKey).contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build()).retrieve().body(Transfer.class);
        if (response == null || response.id() == null) throw new IllegalArgumentException("MFT did not return a transfer");
        return response.id();
    }
    private record Transfer(UUID id) { }
    private static final class NamedBytes extends ByteArrayResource {
        private final String filename;
        private NamedBytes(byte[] bytes, String filename) { super(bytes); this.filename = filename; }
        @Override public String getFilename() { return filename; }
    }
}
