package com.edispark.transformations.domain;

import com.edispark.transformations.domain.*;
import java.util.*;
import java.time.Instant;

/** Creates tenant-owned records without exposing tenant selection to business services. */
public interface MappingCreation {
    MappingRevision draft(String name, UUID sourceSchemaId, UUID targetSchemaId, List<MappingNode> nodes, Instant now);
    MappingRevision fork(MappingRevision source, Instant now);
}
