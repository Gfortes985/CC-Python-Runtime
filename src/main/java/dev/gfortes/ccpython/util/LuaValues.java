package dev.gfortes.ccpython.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.graalvm.polyglot.Value;

public final class LuaValues {
    private LuaValues() {
    }

    public static List<Object> toList(Object[] values) {
        var result = new ArrayList<Object>(values.length);
        for (var value : values) result.add(normalize(value));
        return result;
    }

    public static List<Object> toList(Value value) {
        var size = value.hasArrayElements() ? Math.toIntExact(value.getArraySize()) : 0;
        var result = new ArrayList<Object>(size);
        for (var i = 0; i < size; i++) result.add(toJava(value.getArrayElement(i)));
        return result;
    }

    public static Object toJava(Value value) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isString()) return value.asString();
        if (value.fitsInInt()) return value.asInt();
        if (value.fitsInLong()) return value.asLong();
        if (value.fitsInDouble()) return value.asDouble();

        if (value.hasArrayElements()) {
            var list = new ArrayList<Object>(Math.toIntExact(value.getArraySize()));
            for (var i = 0; i < value.getArraySize(); i++) list.add(toJava(value.getArrayElement(i)));
            return list;
        }

        if (value.hasMembers()) {
            var map = new LinkedHashMap<String, Object>();
            for (var key : value.getMemberKeys()) map.put(key, toJava(value.getMember(key)));
            return map;
        }

        if (value.isHostObject()) return value.asHostObject();

        return Objects.toString(value);
    }

    public static Object normalize(Object value) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) return normalizeMap(map);
        if (value instanceof List<?> list) {
            var normalized = new ArrayList<Object>(list.size());
            for (var entry : list) normalized.add(normalize(entry));
            return normalized;
        }
        if (value instanceof Object[] array) return toList(array);
        return value;
    }

    private static Object normalizeMap(Map<?, ?> map) {
        if (map.isEmpty()) return Map.of();

        var indexed = new ArrayList<Map.Entry<Integer, Object>>(map.size());
        for (var entry : map.entrySet()) {
            Integer index = arrayIndex(entry.getKey());
            if (index == null) return normalizeObjectMap(map);
            indexed.add(Map.entry(index, normalize(entry.getValue())));
        }

        indexed.sort(Comparator.comparingInt(Map.Entry::getKey));
        for (var i = 0; i < indexed.size(); i++) {
            if (indexed.get(i).getKey() != i + 1) return normalizeObjectMap(map);
        }

        var normalized = new ArrayList<Object>(indexed.size());
        for (var entry : indexed) normalized.add(entry.getValue());
        return normalized;
    }

    private static LinkedHashMap<Object, Object> normalizeObjectMap(Map<?, ?> map) {
        var normalized = new LinkedHashMap<Object, Object>(map.size());
        for (var entry : map.entrySet()) normalized.put(entry.getKey(), normalize(entry.getValue()));
        return normalized;
    }

    private static Integer arrayIndex(Object key) {
        if (key instanceof Number number) {
            double value = number.doubleValue();
            if (value >= 1 && Math.rint(value) == value) return (int) value;
        }

        if (key instanceof String string) {
            try {
                int value = Integer.parseInt(string);
                if (value >= 1) return value;
            } catch (NumberFormatException ignored) {
            }
        }

        return null;
    }

    public static long approximateSize(Object value) {
        if (value == null) return 0L;
        if (value instanceof String string) return string.getBytes(StandardCharsets.UTF_8).length;
        if (value instanceof Number || value instanceof Boolean) return 16L;
        if (value instanceof ByteBuffer buffer) return buffer.remaining();
        if (value instanceof List<?> list) {
            long size = 16L;
            for (var entry : list) size += approximateSize(entry);
            return size;
        }
        if (value instanceof Map<?, ?> map) {
            long size = 32L;
            for (var entry : map.entrySet()) {
                size += approximateSize(entry.getKey());
                size += approximateSize(entry.getValue());
            }
            return size;
        }
        if (value instanceof Object[] array) {
            long size = 16L;
            for (var entry : array) size += approximateSize(entry);
            return size;
        }
        return Objects.toString(value).getBytes(StandardCharsets.UTF_8).length;
    }
}
