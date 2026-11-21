package com.edispark.mft.domain.repository;

import java.io.IOException;

public interface SecretStore {
    String put(String name, byte[] plaintext) throws IOException;
    byte[] get(String reference) throws IOException;
    default void delete(String reference) throws IOException {
        throw new IOException("Secret deletion is not supported by this store");
    }
}
