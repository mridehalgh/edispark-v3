package com.edispark.mft.infrastructure.storage;

import io.cottn.core.tenancy.TenantAccess;

import com.edispark.mft.domain.model.PayloadRef;
import com.edispark.mft.domain.model.TransferId;
import java.io.IOException;
import java.io.InputStream;

import com.edispark.mft.domain.repository.PayloadStore;
public interface ExplicitPayloadStore extends PayloadStore {
    PayloadRef put(String tenantId, TransferId transferId, String filename, InputStream content) throws IOException;
    InputStream get(String tenantId, PayloadRef payload) throws IOException;
    default PayloadRef put(TransferId transferId, String filename, InputStream content) throws IOException { return put(TenantAccess.currentId(), transferId, filename, content); }
    default InputStream get(PayloadRef payload) throws IOException { return get(TenantAccess.currentId(), payload); }
}
