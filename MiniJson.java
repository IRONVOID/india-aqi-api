import java.util.*;

/*
 * MiniJson — a small, dependency-free JSON parser.
 *
 * Why we're writing our own instead of using a library:
 * Java has no JSON support built in (unlike Python), and pulling in a
 * real library (like org.json or Gson) means dealing with build tools
 * (Maven/Gradle) or manually downloading .jar files. To keep things
 * simple while we're still learning the basics, this file converts
 * JSON text into plain Java objects:
 *
 *   JSON object  -> Map<String, Object>
 *   JSON array   -> List<Object>
 *   JSON string  -> String
 *   JSON number  -> Double
 *   JSON true/false -> Boolean
 *   JSON null    -> null
 *
 * Usage:
 *   Object data = MiniJson.parse(jsonText);
 *   Map<String, Object> obj = (Map<String, Object>) data;
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
        Object result = parser.parseValue();
        return result;
    }

    private Object parseValue() {
        skipWhitespace();
        char c = text.charAt(pos);
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't' || c == 'f') return parseBoolean();
        if (c == 'n') { pos += 4; return null; } // "null"
        return parseNumber();
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // skip '{'
        skipWhitespace();
        if (text.charAt(pos) == '}') { pos++; return map; }

        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            pos++; // skip ':'
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
        pos++; // skip '['
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
        pos++; // skip opening quote
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
        pos++; // skip closing quote
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
        if (text.charAt(pos) == 't') { pos += 4; return true; }  // "true"
        pos += 5; return false; // "false"
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }
}
