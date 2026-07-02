import java.util.*;

/*
 * MiniJson — a small, dependency-free JSON parser AND serializer.
 *
 * Day 2 added: parse(jsonText) -> Java objects
 * Day 3 adds:  toJson(javaObject) -> JSON text
 *
 * This is needed because our API server needs to send JSON *back* to
 * whoever calls it, not just read JSON from OpenAQ.
 *
 * Supported Java -> JSON conversions:
 *   Map<String, Object>  -> JSON object
 *   List<Object>         -> JSON array
 *   String                -> JSON string (properly escaped)
 *   Number                -> JSON number
 *   Boolean                -> true/false
 *   null                   -> null
 */
public class MiniJson {

    private final String text;
    private int pos = 0;

    private MiniJson(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        MiniJson parser = new MiniJson(text);
        parser.skipWhitespace();
        return parser.parseValue();
    }

    // ---------- SERIALIZATION (Java objects -> JSON text) ----------

    public static String toJson(Object obj) {
        StringBuilder sb = new StringBuilder();
        writeValue(obj, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object obj, StringBuilder sb) {
        if (obj == null) {
            sb.append("null");
        } else if (obj instanceof String) {
            writeString((String) obj, sb);
        } else if (obj instanceof Map) {
            writeObject((Map<String, Object>) obj, sb);
        } else if (obj instanceof List) {
            writeArray((List<Object>) obj, sb);
        } else if (obj instanceof Boolean) {
            sb.append(obj.toString());
        } else if (obj instanceof Number) {
            sb.append(obj.toString());
        } else {
            writeString(obj.toString(), sb);
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(entry.getKey(), sb);
            sb.append(':');
            writeValue(entry.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeArray(List<Object> list, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(',');
            first = false;
            writeValue(item, sb);
        }
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------- PARSING (JSON text -> Java objects) ----------

    private Object parseValue() {
        skipWhitespace();
        char c = text.charAt(pos);
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't' || c == 'f') return parseBoolean();
        if (c == 'n') { pos += 4; return null; }
        return parseNumber();
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipWhitespace();
        if (text.charAt(pos) == '}') { pos++; return map; }

        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            pos++;
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if (text.charAt(pos) == ',') { pos++; continue; }
            if (text.charAt(pos) == '}') { pos++; break; }
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipWhitespace();
        if (text.charAt(pos) == ']') { pos++; return list; }

        while (true) {
            Object value = parseValue();
            list.add(value);
            skipWhitespace();
            if (text.charAt(pos) == ',') { pos++; continue; }
            if (text.charAt(pos) == ']') { pos++; break; }
        }
        return list;
    }

    private String parseString() {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (text.charAt(pos) != '"') {
            char c = text.charAt(pos);
            if (c == '\\') {
                pos++;
                char escaped = text.charAt(pos);
                switch (escaped) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'u':
                        String hex = text.substring(pos + 1, pos + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default: sb.append(escaped);
                }
            } else {
                sb.append(c);
            }
            pos++;
        }
        pos++;
        return sb.toString();
    }

    private Double parseNumber() {
        int start = pos;
        while (pos < text.length() && "-+.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        return Double.parseDouble(text.substring(start, pos));
    }

    private Boolean parseBoolean() {
        if (text.charAt(pos) == 't') { pos += 4; return true; }
        pos += 5; return false;
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }
}
