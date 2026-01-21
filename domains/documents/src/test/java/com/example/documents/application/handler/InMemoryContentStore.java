package com.example.documents.application.handler;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.repository.ContentStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of ContentStore for testing.
 */
class InMemoryContentStore implements ContentStore {

    private final Map<ContentHash, byte[]> store = new HashMap<>();

    @Override
    public void store(Content content) {
        store.put(content.hash(), content.data());
    }

    @Override
    public Optional<byte[]> retrieve(ContentHash hash) {
        return Optional.ofNullable(store.get(hash));
    }

    @Override
    public boolean exists(ContentHash hash) {
        return store.containsKey(hash);
    }

    @Override
    public void delete(ContentHash hash) {
        store.remove(hash);
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
