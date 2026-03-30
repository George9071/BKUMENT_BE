package vn.edu.hcmut.lms.utils;

import java.text.Normalizer;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public final class VietnameseTextUtils {
    private static final Pattern DIACRITICAL_MARKS_PATTERN =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private VietnameseTextUtils() {}

    public static String toLikePattern(String keyword) {
        String standardized = standardize(keyword);
        return standardized == null ? null : "%" + standardized + "%";
    }

    public static String standardize(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;

        String normalized = Normalizer.normalize(keyword.trim(), Normalizer.Form.NFD);

        return DIACRITICAL_MARKS_PATTERN.matcher(normalized).replaceAll("")
                .replace("Đ", "D")
                .replace("đ", "d")
                .toLowerCase();
    }
}
