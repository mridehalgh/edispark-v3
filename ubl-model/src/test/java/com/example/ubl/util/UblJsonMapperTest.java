package com.example.ubl.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UblJsonMapper}.
 */
class UblJsonMapperTest {

    @Test
    void getInstance_returnsSameInstance() {
        ObjectMapper first = UblJsonMapper.getInstance();
        ObjectMapper second = UblJsonMapper.getInstance();
        
        assertThat(first).isSameAs(second);
    }

    @Test
    void getInstance_returnsNonNullMapper() {
        ObjectMapper mapper = UblJsonMapper.getInstance();
        
        assertThat(mapper).isNotNull();
    }

    @Test
    void serialization_excludesNullFields() throws JsonProcessingException {
        ObjectMapper mapper = UblJsonMapper.getInstance();
        TestObject obj = new TestObject("value", null);
        
        String json = mapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"name\"");
        assertThat(json).doesNotContain("\"description\"");
    }

    @Test
    void serialization_localDateAsIsoString() throws JsonProcessingException {
        ObjectMapper mapper = UblJsonMapper.getInstance();
        DateTestObject obj = new DateTestObject(LocalDate.of(2024, 1, 15));
        
        String json = mapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"2024-01-15\"");
        assertThat(json).doesNotContain("[2024");
    }

    @Test
    void serialization_localTimeAsIsoString() throws JsonProcessingException {
        ObjectMapper mapper = UblJsonMapper.getInstance();
        TimeTestObject obj = new TimeTestObject(LocalTime.of(14, 30, 0));
        
        String json = mapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"14:30:00\"");
    }

    @Test
    void serialization_offsetDateTimeAsIsoString() throws JsonProcessingException {
        ObjectMapper mapper = UblJsonMapper.getInstance();
        OffsetDateTime dateTime = OffsetDateTime.of(2024, 1, 15, 14, 30, 0, 0, ZoneOffset.UTC);
        OffsetDateTimeTestObject obj = new OffsetDateTimeTestObject(dateTime);
        
        String json = mapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"2024-01-15T14:30:00Z\"");
    }

    @Test
    void deserialization_localDateFromIsoString() throws JsonProcessingException {
        ObjectMapper mapper = UblJsonMapper.getInstance();
        String json = "{\"date\":\"2024-01-15\"}";
        
        DateTestObject obj = mapper.readValue(json, DateTestObject.class);
        
        assertThat(obj.date()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void deserialization_localTimeFromIsoString() throws JsonProcessingException {
        ObjectMapper mapper = UblJsonMapper.getInstance();
        String json = "{\"time\":\"14:30:00\"}";
        
        TimeTestObject obj = mapper.readValue(json, TimeTestObject.class);
        
        assertThat(obj.time()).isEqualTo(LocalTime.of(14, 30, 0));
    }

    // Test helper records
    record TestObject(String name, String description) {}
    record DateTestObject(LocalDate date) {}
    record TimeTestObject(LocalTime time) {}
    record OffsetDateTimeTestObject(OffsetDateTime dateTime) {}
}
