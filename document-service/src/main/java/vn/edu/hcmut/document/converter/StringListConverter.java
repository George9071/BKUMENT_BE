package vn.edu.hcmut.document.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }

        return attribute.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase().replace(" ", "-"))
                .collect(Collectors.joining(" "));
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(Arrays.asList(dbData.split("\\s+")));
    }
}
