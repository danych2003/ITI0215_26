package util;

import java.math.BigDecimal;

public final class CanonicalJsonUtils {
    public static String quote(String value) {
        if (value == null) {
            return "null";
        }

        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    public static String decimal(BigDecimal value) {
        if (value == null) {
            return "null";
        }

        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    private CanonicalJsonUtils() {
    }
}
