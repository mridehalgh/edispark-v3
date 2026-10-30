package com.tradacoms.parser.schema;

/**
 * Sealed interface for schema elements - either a segment or a group.
 * Enables type-safe traversal of schema structure.
 */
public sealed interface SegmentOrGroupSchema permits SegmentSchema, GroupSchema {
    
    /**
     * Returns the identifier for this schema element.
     */
    String getId();
    
    /**
     * Returns the human-readable name.
     */
    String getName();
    
    /**
     * Returns the usage indicator ("M" for mandatory, "O" for optional).
     */
    String getUsage();
    
    /**
     * Returns true if this element is mandatory.
     */
    boolean isMandatory();
}
