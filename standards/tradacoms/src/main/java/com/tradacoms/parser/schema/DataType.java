package com.tradacoms.parser.schema;

/**
 * Represents the data types used in TRADACOMS element definitions.
 */
public enum DataType {
    /**
     * Integer type - "int" in schema
     */
    INTEGER("int"),
    
    /**
     * Character/string type - "char" in schema
     */
    CHARACTER("char"),
    
    /**
     * Numeric with 3 decimal places - "num.3" in schema
     */
    NUMERIC_3("num.3"),
    
    /**
     * Numeric with 4 decimal places - "num.4" in schema
     */
    NUMERIC_4("num.4"),
    
    /**
     * Date type - 6-digit date YYMMDD
     */
    DATE("date"),
    
    /**
     * Time type
     */
    TIME("time");

    private final String schemaValue;

    DataType(String schemaValue) {
        this.schemaValue = schemaValue;
    }

    public String getSchemaValue() {
        return schemaValue;
    }

    /**
     * Parses a schema type string into a DataType.
     * Returns null if the type string is null or unrecognized.
     */
    public static DataType fromSchemaValue(String typeStr) {
        if (typeStr == null) {
            return null;
        }
        return switch (typeStr.toLowerCase()) {
            case "int" -> INTEGER;
            case "char" -> CHARACTER;
            case "num.3" -> NUMERIC_3;
            case "num.4" -> NUMERIC_4;
            case "date" -> DATE;
            case "time" -> TIME;
            default -> null;
        };
    }
}
