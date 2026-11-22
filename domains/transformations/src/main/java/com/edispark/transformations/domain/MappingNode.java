package com.edispark.transformations.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record MappingNode(UUID id, MappingOperation operation, List<String> sourcePointers,
                          String targetPointer, String constant, String separator, Map<String, String> codeMap,
                          BusinessCodeList codeList, BusinessCodeSyntax sourceSyntax,
                          BusinessCodeSyntax targetSyntax,
                          @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                          TableLookup tableLookup) {
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
        if (operation == MappingOperation.OMIT && (constant == null || constant.isBlank())) {
            throw new IllegalArgumentException("An intentional omission requires a recorded business reason");
        }
        if (operation != MappingOperation.CONSTANT && sourcePointers.isEmpty()) {
            throw new IllegalArgumentException("At least one source pointer is required");
        }
        if ((operation == MappingOperation.CODE_LOOKUP || operation == MappingOperation.OPTIONAL_CODE_LOOKUP) && codeMap.isEmpty()) {
            throw new IllegalArgumentException("codeMap is required for CODE_LOOKUP");
        }
        if ((operation == MappingOperation.LOOKUP_TABLE) != (tableLookup != null)
                || (tableLookup != null && sourcePointers.size() != 1)) {
            throw new IllegalArgumentException("A table lookup requires one source field, a table alias and a result column.");
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

    public MappingNode(UUID id, MappingOperation operation, List<String> sourcePointers, String targetPointer,
            String constant, String separator, Map<String, String> codeMap, BusinessCodeList codeList,
            BusinessCodeSyntax sourceSyntax, BusinessCodeSyntax targetSyntax) {
        this(id, operation, sourcePointers, targetPointer, constant, separator, codeMap, codeList, sourceSyntax,
                targetSyntax, null);
    }

    public record TableLookup(String alias, String resultColumn) {
        public TableLookup {
            if (!validName(alias) || !validName(resultColumn)) {
                throw new IllegalArgumentException("Use bounded names for the table alias and result column.");
            }
        }

        private static boolean validName(String name) {
            return name != null && name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}");
        }
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
