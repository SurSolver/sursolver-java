package com.sursolver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict RFC 8259 JSON codec, so the package needs no dependency.
 *
 * <p>Objects parse to unmodifiable {@code LinkedHashMap}s (duplicate keys are an error), arrays to
 * unmodifiable lists, numbers to {@code Long} when integral and in range, otherwise {@code Double}.
 */
final class Json {
    private static final int MAX_DEPTH = 512;

    private Json() {}

    /** Thrown when text is not JSON. */
    static final class ParseException extends Exception {
        private static final long serialVersionUID = 1L;

        ParseException(String message, int position) {
            super(message + " at offset " + position);
        }
    }

    static Object parse(String text) throws ParseException {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.value(0);
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw new ParseException("trailing data", parser.pos);
        }
        return value;
    }

    /** Serialises maps with string keys, collections, strings, numbers, booleans and null. */
    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("value nests deeper than " + MAX_DEPTH + " levels");
        }
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(text, out);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                throw new IllegalArgumentException("JSON has no NaN or Infinity");
            }
            out.append(number == Math.rint(number) && Math.abs(number) < 1e15 ? Long.toString((long) number) : Double.toString(number));
        } else if (value instanceof BigDecimal decimal) {
            out.append(decimal.toString());
        } else if (value instanceof Number) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                if (!first) out.append(',');
                first = false;
                writeString(key, out);
                out.append(':');
                write(entry.getValue(), out, depth + 1);
            }
            out.append('}');
        } else if (value instanceof Collection<?> list) {
            out.append('[');
            boolean first = true;
            for (Object entry : list) {
                if (!first) out.append(',');
                first = false;
                write(entry, out, depth + 1);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("cannot write " + value.getClass().getName() + " as JSON");
        }
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
                pos++;
            }
        }

        Object value(int depth) throws ParseException {
            if (depth > MAX_DEPTH) throw new ParseException("nesting too deep", pos);
            if (pos >= text.length()) throw new ParseException("unexpected end", pos);
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object(depth);
                case '[' -> array(depth);
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) yield number();
                    throw new ParseException("unexpected character", pos);
                }
            };
        }

        private Object literal(String word, Object value) throws ParseException {
            if (!text.startsWith(word, pos)) throw new ParseException("unexpected literal", pos);
            pos += word.length();
            return value;
        }

        private Map<String, Object> object(int depth) throws ParseException {
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < text.length() && text.charAt(pos) == '}') {
                pos++;
                return Collections.unmodifiableMap(map);
            }
            while (true) {
                skipWhitespace();
                if (pos >= text.length() || text.charAt(pos) != '"') throw new ParseException("expected a key", pos);
                int keyAt = pos;
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                if (map.containsKey(key)) throw new ParseException("duplicate key", keyAt);
                map.put(key, value(depth + 1));
                skipWhitespace();
                if (pos < text.length() && text.charAt(pos) == ',') {
                    pos++;
                    continue;
                }
                expect('}');
                return Collections.unmodifiableMap(map);
            }
        }

        private List<Object> array(int depth) throws ParseException {
            pos++;
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (pos < text.length() && text.charAt(pos) == ']') {
                pos++;
                return Collections.unmodifiableList(list);
            }
            while (true) {
                skipWhitespace();
                list.add(value(depth + 1));
                skipWhitespace();
                if (pos < text.length() && text.charAt(pos) == ',') {
                    pos++;
                    continue;
                }
                expect(']');
                return Collections.unmodifiableList(list);
            }
        }

        private void expect(char c) throws ParseException {
            if (pos >= text.length() || text.charAt(pos) != c) throw new ParseException("expected '" + c + "'", pos);
            pos++;
        }

        private String string() throws ParseException {
            pos++;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= text.length()) throw new ParseException("unterminated string", pos);
                char c = text.charAt(pos++);
                if (c == '"') return out.toString();
                if (c < 0x20) throw new ParseException("control character in string", pos - 1);
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= text.length()) throw new ParseException("unterminated escape", pos);
                char e = text.charAt(pos++);
                switch (e) {
                    case '"', '\\', '/' -> out.append(e);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) throw new ParseException("short unicode escape", pos);
                        try {
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException error) {
                            throw new ParseException("bad unicode escape", pos);
                        }
                        pos += 4;
                    }
                    default -> throw new ParseException("bad escape", pos - 1);
                }
            }
        }

        private Object number() throws ParseException {
            int start = pos;
            if (text.charAt(pos) == '-') pos++;
            if (pos < text.length() && text.charAt(pos) == '0') {
                pos++;
            } else if (!digits()) {
                throw new ParseException("bad number", start);
            }
            boolean integral = true;
            if (pos < text.length() && text.charAt(pos) == '.') {
                integral = false;
                pos++;
                if (!digits()) throw new ParseException("bad fraction", pos);
            }
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                integral = false;
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) pos++;
                if (!digits()) throw new ParseException("bad exponent", pos);
            }
            String literal = text.substring(start, pos);
            if (integral) {
                try {
                    return Long.parseLong(literal);
                } catch (NumberFormatException outOfRange) {
                    // falls through to a double
                }
            }
            try {
                return new BigDecimal(literal).doubleValue();
            } catch (NumberFormatException error) {
                throw new ParseException("number out of range", start);
            }
        }

        private boolean digits() {
            int start = pos;
            while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos++;
            return pos > start;
        }
    }
}
