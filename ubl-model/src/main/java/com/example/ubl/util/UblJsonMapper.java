package com.example.ubl.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility class providing a pre-configured Jackson ObjectMapper for UBL JSON serialization
 * and deserialization.
 * 
 * <p>The mapper is configured with:
 * <ul>
 *   <li>JavaTimeModule for java.time date/time handling</li>
 *   <li>Dates serialized as ISO-8601 strings (not timestamps)</li>
 *   <li>Null values excluded from serialization output</li>
 *   <li>Custom LocalTime deserializer to handle UBL time formats with timezone suffix</li>
 * </ul>
 * 
 * <p>This class follows the singleton pattern with a thread-safe, lazily-initialized
 * ObjectMapper instance.
 */
public final class UblJsonMapper {
    
    private static final ObjectMapper MAPPER = createMapper();
    
    private UblJsonMapper() {
        // Prevent instantiation
    }
    
    /**
     * Returns the pre-configured ObjectMapper singleton instance.
     * 
     * @return the shared ObjectMapper instance configured for UBL JSON processing
     */
    public static ObjectMapper getInstance() {
        return MAPPER;
    }
    
    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        
        // Register custom LocalTime deserializer to handle UBL time formats with timezone suffix (e.g., "11:32:26.0Z")
        SimpleModule ublTimeModule = new SimpleModule("UBLTimeModule");
        ublTimeModule.addDeserializer(LocalTime.class, new UblLocalTimeDeserializer());
        mapper.registerModule(ublTimeModule);
        
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
    
    /**
     * Custom deserializer for LocalTime that handles UBL time formats.
     * UBL JSON may include timezone suffix (e.g., "11:32:26.0Z") which standard
     * LocalTime parsing doesn't support. This deserializer strips the timezone
     * and parses just the time portion.
     */
    private static class UblLocalTimeDeserializer extends StdDeserializer<LocalTime> {
        
        UblLocalTimeDeserializer() {
            super(LocalTime.class);
        }
        
        @Override
        public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String timeString = p.getText();
            if (timeString == null || timeString.isEmpty()) {
                return null;
            }
            
            // Try standard LocalTime parsing first
            try {
                return LocalTime.parse(timeString);
            } catch (DateTimeParseException e) {
                // If that fails, try parsing as OffsetTime and extract LocalTime
                // This handles formats like "11:32:26.0Z" or "11:32:26+01:00"
                try {
                    OffsetTime offsetTime = OffsetTime.parse(timeString);
                    return offsetTime.toLocalTime();
                } catch (DateTimeParseException e2) {
                    // As a last resort, strip trailing Z and try again
                    if (timeString.endsWith("Z")) {
                        String withoutZ = timeString.substring(0, timeString.length() - 1);
                        return LocalTime.parse(withoutZ);
                    }
                    throw e;
                }
            }
        }
    }
}
