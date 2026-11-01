package caro.protocol;

import java.util.ArrayList;
import java.util.List;

/** CAP/1.0 comma-separated plain-text codec with backslash escaping. */
public final class TextProtocol {
    private TextProtocol() {}

    public static String encode(Object... fields) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) output.append(',');
            escape(output, fields[i] == null ? "" : fields[i].toString());
        }
        return output.toString();
    }

    public static List<String> decode(String frame) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < frame.length(); i++) {
            char character = frame.charAt(i);
            if (escaped) {
                switch (character) {
                    case '\\' -> field.append('\\');
                    case ',' -> field.append(',');
                    case 'n' -> field.append('\n');
                    case 'r' -> field.append('\r');
                    default -> throw new IllegalArgumentException("Escape khong hop le tai vi tri " + i);
                }
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        if (escaped) throw new IllegalArgumentException("Dau escape o cuoi frame");
        fields.add(field.toString());
        return List.copyOf(fields);
    }

    private static void escape(StringBuilder output, String value) {
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case '\\' -> output.append("\\\\");
                case ',' -> output.append("\\,");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                default -> output.append(value.charAt(i));
            }
        }
    }
}
