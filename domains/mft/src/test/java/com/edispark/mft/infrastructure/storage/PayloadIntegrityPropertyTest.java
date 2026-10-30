package com.edispark.mft.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.mft.domain.model.PayloadRef;
import com.edispark.mft.domain.model.TransferId;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import java.nio.file.Files;
import java.util.UUID;

class PayloadIntegrityPropertyTest {
    /** Feature: mft-as2, Property 5: payload storage preserves every byte. */
    @Property(tries = 100)
    void payloadRoundTripPreservesBytes(@ForAll @Size(max = 32_768) byte[] payload) throws Exception {
        Path directory = Files.createTempDirectory("mft-payload-" + UUID.randomUUID());
        FileSystemPayloadStore store = new FileSystemPayloadStore(directory);
        PayloadRef reference = store.put("tenant-a", TransferId.generate(), "../unsafe name.edi",
                new ByteArrayInputStream(payload));

        assertThat(store.get("tenant-a", reference).readAllBytes()).isEqualTo(payload);
        assertThat(reference.size()).isEqualTo(payload.length);
        assertThat(reference.sha256()).hasSize(64);
        Path encryptedFile = directory.resolve(reference.key());
        if (payload.length > 0) assertThat(Files.readAllBytes(encryptedFile)).isNotEqualTo(payload);
    }
}
