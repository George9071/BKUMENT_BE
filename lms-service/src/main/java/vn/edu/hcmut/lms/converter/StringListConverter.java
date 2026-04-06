package vn.edu.hcmut.lms.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String>{
    private static final String SPLIT_CHAR = ",";

    @Override
    public String convertToDatabaseColumn(List<String> list) {
        return (list != null && !list.isEmpty())
                ? String.join(SPLIT_CHAR, list)
                : "";
    }

    @Override
    public List<String> convertToEntityAttribute(String string) {
        if (string == null || string.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(string.split(SPLIT_CHAR)));
    }
}