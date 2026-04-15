package dev.gfortes.ccpython.bridge;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

final class BridgeJson {
    private BridgeJson() {
    }

    static byte[] encode(Object value) {
        return toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String string) return '"' + escape(string) + '"';
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Enum<?> enumeration) return '"' + escape(enumeration.name()) + '"';

        if (value instanceof Map<?, ?> map) {
            var builder = new StringBuilder();
            builder.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) builder.append(',');
                first = false;
                builder.append('"').append(escape(String.valueOf(entry.getKey()))).append('"').append(':');
                builder.append(toJson(entry.getValue()));
            }
            builder.append('}');
            return builder.toString();
        }

        if (value instanceof Iterable<?> iterable) {
            var builder = new StringBuilder();
            builder.append('[');
            Iterator<?> iterator = iterable.iterator();
            boolean first = true;
            while (iterator.hasNext()) {
                if (!first) builder.append(',');
                first = false;
                builder.append(toJson(iterator.next()));
            }
            builder.append(']');
            return builder.toString();
        }

        if (value.getClass().isArray()) {
            var builder = new StringBuilder();
            builder.append('[');
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) builder.append(',');
                builder.append(toJson(Array.get(value, i)));
            }
            builder.append(']');
            return builder.toString();
        }

        return '"' + escape(String.valueOf(value)) + '"';
    }

    private static String escape(String value) {
        var builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}
