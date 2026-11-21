package com.edispark.transformations.infrastructure;

import com.edispark.transformations.domain.*;
import java.util.*;
import java.time.Instant;
import com.edispark.transformations.domain.MappingCreation;
import io.cottn.core.tenancy.TenantAccess;

public interface MappingCreationAdapter extends MappingCreation {
    default MappingRevision draft(String name, UUID sourceSchemaId, UUID targetSchemaId, List<MappingNode> nodes, Instant now) { return MappingRevision.draft(TenantAccess.currentId(), name, sourceSchemaId, targetSchemaId, nodes, now); }
    default MappingRevision fork(MappingRevision source, Instant now) { if (source.scope() != MappingScope.GLOBAL) TenantAccess.requireOwner(source.tenantId()); return source.forkForTenant(TenantAccess.currentId(), now); }
}
