package com.edispark.transformations.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record MappingNode(UUID id, MappingOperation operation, List<String> sourcePointers,
                          String targetPointer, String constant, String separator, Map<String, String> codeMap,
                          BusinessCodeList codeList, BusinessCodeSyntax sourceSyntax,
                          BusinessCodeSyntax targetSyntax) {
    public MappingNode {
        Objects.requireNonNull(id);
        Objects.requireNonNull(operation);
        sourcePointers = List.copyOf(sourcePointers == null ? List.of() : sourcePointers);
        codeMap = codeMap == null ? Map.of() : codeMap;
        requirePointer(targetPointer, "targetPointer");
        sourcePointers.forEach(pointer -> requirePointer(pointer, "sourcePointer"));
        if (operation == MappingOperation.CONSTANT && constant == null) {
            throw new IllegalArgumentException("constant is required for CONSTANT");
        }
        if (operation != MappingOperation.CONSTANT && sourcePointers.isEmpty()) {
            throw new IllegalArgumentException("At least one source pointer is required");
        }
        if (operation == MappingOperation.CODE_LOOKUP && codeMap.isEmpty()) {
            throw new IllegalArgumentException("codeMap is required for CODE_LOOKUP");
        }
        boolean hasCodeMetadata = codeList != null || sourceSyntax != null || targetSyntax != null;
        if (hasCodeMetadata && (codeList == null || sourceSyntax == null || targetSyntax == null)) {
            throw new IllegalArgumentException("Code list, source syntax and target syntax must be provided together");
        }
        if (codeMap.size() > 2_000 || codeMap.entrySet().stream().anyMatch(entry -> invalidCode(entry.getKey())
                || invalidCode(entry.getValue()))) {
            throw new IllegalArgumentException("codeMap must contain at most 2000 bounded, non-blank codes");
        }
        codeMap = Map.copyOf(codeMap);
    }

    public MappingNode(UUID id, MappingOperation operation, List<String> sourcePointers,
            String targetPointer, String constant, String separator) {
        this(id, operation, sourcePointers, targetPointer, constant, separator, Map.of(), null, null, null);
    }

    public MappingNode(UUID id, MappingOperation operation, List<String> sourcePointers,
            String targetPointer, String constant, String separator, Map<String, String> codeMap) {
        this(id, operation, sourcePointers, targetPointer, constant, separator, codeMap, null, null, null);
    }

    private static boolean invalidCode(String value) {
        return value == null || value.isBlank() || value.length() > 100;
    }

    private static void requirePointer(String pointer, String field) {
        if (pointer == null || !pointer.startsWith("/") || pointer.length() > 500) {
            throw new IllegalArgumentException(field + " must be a bounded JSON pointer");
        }
    }
}
