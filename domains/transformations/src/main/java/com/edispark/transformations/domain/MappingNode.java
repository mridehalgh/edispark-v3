package com.edispark.transformations.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MappingNode(UUID id, MappingOperation operation, List<String> sourcePointers,
                          String targetPointer, String constant, String separator) {
    public MappingNode {
        Objects.requireNonNull(id);
        Objects.requireNonNull(operation);
        sourcePointers = List.copyOf(sourcePointers == null ? List.of() : sourcePointers);
        requirePointer(targetPointer, "targetPointer");
        sourcePointers.forEach(pointer -> requirePointer(pointer, "sourcePointer"));
        if (operation == MappingOperation.CONSTANT && constant == null) {
            throw new IllegalArgumentException("constant is required for CONSTANT");
        }
        if (operation != MappingOperation.CONSTANT && sourcePointers.isEmpty()) {
            throw new IllegalArgumentException("At least one source pointer is required");
        }
    }

    private static void requirePointer(String pointer, String field) {
        if (pointer == null || !pointer.startsWith("/") || pointer.length() > 500) {
            throw new IllegalArgumentException(field + " must be a bounded JSON pointer");
        }
    }
}
