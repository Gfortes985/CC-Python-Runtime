package dev.gfortes.ccpython.charset;

import dan200.computercraft.core.util.StringUtil;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class CCTerminalCharset {
    private static final Charset CP866 = Charset.forName("CP866");

    private CCTerminalCharset() {
    }

    public static boolean isEnabled() {
        return true;
    }

    public static int unicodeToTerminal(int codePoint) {
        if (!isEnabled()) return -1;
        if (codePoint < 128) return -1;

        var text = new String(Character.toChars(codePoint));
        if (!CP866.newEncoder().canEncode(text)) return -1;

        byte[] encoded = text.getBytes(CP866);
        if (encoded.length != 1) return -1;
        return encoded[0] & 0xFF;
    }

    public static String mapTerminalString(String value) {
        if (!isEnabled() || value == null || value.isEmpty()) return value;

        var builder = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint >= 0 && codePoint <= 0xFF) {
                builder.append((char) codePoint);
                return;
            }

            int mapped = unicodeToTerminal(codePoint);
            if (mapped >= 0) {
                builder.append((char) mapped);
                return;
            }

            int fallback = StringUtil.unicodeToTerminal(codePoint);
            if (fallback >= 0) {
                builder.append((char) fallback);
            } else if (codePoint <= 0xFF) {
                builder.append((char) codePoint);
            } else {
                builder.append('?');
            }
        });
        return builder.toString();
    }

    public static String normaliseLabel(String label) {
        if (!isEnabled() || label == null || label.isEmpty()) return label;

        var mapped = mapTerminalString(label);
        int limit = Math.min(32, mapped.length());
        var builder = new StringBuilder(limit);
        for (int i = 0; i < limit; i++) {
            char ch = mapped.charAt(i);
            if ((ch >= 32 && ch <= 126) || (ch >= 128 && ch <= 255 && ch != 167)) {
                builder.append(ch);
            } else {
                builder.append('?');
            }
        }
        return builder.toString();
    }

    public static String decodeTerminalString(String value) {
        if (!isEnabled() || value == null || value.isEmpty()) return value;

        boolean byteString = value.chars().allMatch(ch -> ch >= 0 && ch <= 0xFF);
        if (!byteString) return value;

        byte[] bytes = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) bytes[i] = (byte) value.charAt(i);

        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }

        return new String(bytes, CP866);
    }
}
