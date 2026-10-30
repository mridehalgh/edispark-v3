package com.edispark.mft.domain.repository;

import com.edispark.mft.domain.model.PayloadRef;
import com.edispark.mft.domain.model.TransferId;
import java.io.IOException;
import java.io.InputStream;

public interface PayloadStore {
    PayloadRef put(String tenantId, TransferId transferId, String filename, InputStream content) throws IOException;
    InputStream get(String tenantId, PayloadRef payload) throws IOException;
}
