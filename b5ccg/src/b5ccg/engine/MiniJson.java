package b5ccg.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B5-1975: a minimal, strict JSON reader for replay files.
 *
 * <p>Deliberately hand-written rather than borrowed: the build is stdlib-only
 * (AGENTS.md §2), and the replay format is ours, so the parser covers exactly
 * the grammar {@link ReplayRecorder} emits — objects, arrays, strings,
 * integers, booleans and null — and refuses everything else rather than
 * guessing.</p>
 *
 * <p>Values come back as {@link LinkedHashMap}, {@link ArrayList},
 * {@link String}, {@link Integer}, {@link Boolean}, or null, which is exactly
 * the set {@link ReplayRecorder} writes. Doubles are not produced: a replay
 * has no floating-point field, and accepting them here would let a corrupt
 * line through as a value the writer could never have written.</p>
 *
 * <p>Package-private: this exists to serve {@link ReplayRecorder#load}, not as
 * a general-purpose API.</p>
 */
final class MiniJson {

    private final String src;
    private int          pos;

    private MiniJson(String src) { this.src = src; }

    /** Parses one complete JSON object. Trailing content after the closing
     *  brace is an error — a JSON Lines line must be exactly one object. */
    static Map<String, Object> parseObject(String text) throws java.io.IOException {
        MiniJson p = new MiniJson(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.pos != p.src.length()) {
            throw new java.io.IOException("B5-1975: trailing content after JSON object at offset " + p.pos);
        }
        if (!(v instanceof Map)) {
            throw new java.io.IOException("B5-1975: expected a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        return m;
    }

    private Object value() throws java.io.IOException {
        if (pos >= src.length()) throw new java.io.IOException("B5-1975: unexpected end of JSON");
        char c = src.charAt(pos);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == 't') return literal("true",  Boolean.TRUE);
        if (c == 'f') return literal("false", Boolean.FALSE);
        if (c == 'n') return literal("null",  null);
        return number();
    }

    private Map<String, Object> object() throws java.io.IOException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        pos++;                                  // '{'
        ws();
        if (peek() == '}') { pos++; return m; }
        while (true) {
            ws();
            if (peek() != '"') throw new java.io.IOException("B5-1975: object key must be a string at offset " + pos);
            String k = string();
            ws();
            if (peek() != ':') throw new java.io.IOException("B5-1975: expected ':' after key '" + k + "'");
            pos++;
            ws();
            m.put(k, value());
            ws();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == '}') { pos++; return m; }
            throw new java.io.IOException("B5-1975: expected ',' or '}' at offset " + pos);
        }
    }

    private List<Object> array() throws java.io.IOException {
        List<Object> l = new ArrayList<Object>();
        pos++;                                  // '['
        ws();
        if (peek() == ']') { pos++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == ']') { pos++; return l; }
            throw new java.io.IOException("B5-1975: expected ',' or ']' at offset " + pos);
        }
    }

    private String string() throws java.io.IOException {
        pos++;                                  // opening '"'
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) throw new java.io.IOException("B5-1975: unterminated string");
            char ch = src.charAt(pos++);
            if (ch == '"') return sb.toString();
            if (ch != '\\') { sb.append(ch); continue; }
            if (pos >= src.length()) throw new java.io.IOException("B5-1975: unterminated escape");
            char e = src.charAt(pos++);
            switch (e) {
                case '"':  sb.append('"');  break;
                case '\\': sb.append('\\'); break;
                case '/':  sb.append('/');  break;
                case 'b':  sb.append('\b'); break;
                case 'f':  sb.append('\f'); break;
                case 'n':  sb.append('\n'); break;
                case 'r':  sb.append('\r'); break;
                case 't':  sb.append('\t'); break;
                case 'u':
                    if (pos + 4 > src.length()) {
                        throw new java.io.IOException("B5-1975: truncated \\u escape");
                    }
                    String hex = src.substring(pos, pos + 4);
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException nfe) {
                        throw new java.io.IOException("B5-1975: bad \\u escape '" + hex + "'");
                    }
                    pos += 4;
                    break;
                default:
                    throw new java.io.IOException("B5-1975: unknown escape '\\" + e + "'");
            }
        }
    }

    private Integer number() throws java.io.IOException {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < src.length() && isDigit(src.charAt(pos))) pos++;
        if (start == pos) throw new java.io.IOException("B5-1975: expected a value at offset " + start);
        String t = src.substring(start, pos);
        if (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '.' || c == 'e' || c == 'E') {
                throw new java.io.IOException("B5-1975: replay carries no floating-point values, found '" + t + c + "'");
            }
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException nfe) {
            throw new java.io.IOException("B5-1975: '" + t + "' is not an integer");
        }
    }

    private Object literal(String word, Object result) throws java.io.IOException {
        if (!src.startsWith(word, pos)) {
            throw new java.io.IOException("B5-1975: malformed literal at offset " + pos);
        }
        pos += word.length();
        return result;
    }

    private char peek() throws java.io.IOException {
        if (pos >= src.length()) throw new java.io.IOException("B5-1975: unexpected end of JSON");
        return src.charAt(pos);
    }

    private void ws() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++; else break;
        }
    }

    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }
}
