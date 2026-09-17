package com.wardrobe.agent.wardrobe;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashSet;
import java.util.Set;

@Converter
/** 将 Set<String> 与 JSON 字符串互转，供不使用原生 JSON 映射的 JPA 字段复用。 */
public class StringSetConverter implements AttributeConverter<Set<String>, String> {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Set<String> value) {
        try { return JSON.writeValueAsString(value == null ? Set.of() : value); }
        catch (Exception exception) { throw new IllegalArgumentException("Cannot serialize tags", exception); }
    }

    @Override
    public Set<String> convertToEntityAttribute(String value) {
        try { return value == null ? new LinkedHashSet<>() : JSON.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalArgumentException("Cannot parse tags", exception); }
    }
}
