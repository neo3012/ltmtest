package caro.client;

import java.util.ArrayList;
import java.util.List;

final class TextProtocol {
    private TextProtocol() {}

    static String encode(Object... fields) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) output.append(',');
            String value = fields[i] == null ? "" : fields[i].toString();
            for (int j = 0; j < value.length(); j++) {
                switch (value.charAt(j)) {
                    case '\\' -> output.append("\\\\");
                    case ',' -> output.append("\\,");
                    case '\n' -> output.append("\\n");
                    case '\r' -> output.append("\\r");
                    default -> output.append(value.charAt(j));
                }
            }
        }
        return output.toString();
    }

    static List<String> decode(String frame) {
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
                    default -> throw new IllegalArgumentException("Escape khong hop le");
                }
                escaped = false;
            } else if (character == '\\') escaped = true;
            else if (character == ',') { fields.add(field.toString()); field.setLength(0); }
            else field.append(character);
        }
        if (escaped) throw new IllegalArgumentException("Dau escape o cuoi frame");
        fields.add(field.toString());
        return List.copyOf(fields);
    }
}
