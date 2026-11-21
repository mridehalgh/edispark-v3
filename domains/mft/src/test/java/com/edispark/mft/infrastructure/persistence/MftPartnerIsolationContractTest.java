package com.edispark.mft.infrastructure.persistence;

import com.edispark.testing.ScopedRepositoryContract;
import com.edispark.mft.domain.model.*;
import java.time.Instant;
import java.net.URI;
import java.util.*;

class MftPartnerIsolationContractTest extends ScopedRepositoryContract<PartnerProfile> {
    @Override protected Operations<PartnerProfile> operations() {
        var repository = new InMemoryMftRepository();
        return new Operations<>(repository::savePartner, repository::findPartner, repository::listPartners,
                Optional.of(repository::deletePartner));
    }

    @Override protected PartnerProfile fixture(String owner, UUID id) {
        return new PartnerProfile(id, owner, "Isolation fixture", "local-" + owner, "remote-" + owner,
                URI.create("https://partner.example/as2"), id, id, MdnMode.SYNCHRONOUS,
                true, true, false, false, Instant.EPOCH);
    }
}
