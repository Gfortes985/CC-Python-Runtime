package dev.gfortes.ccpython.monitor;

import dan200.computercraft.core.util.Colour;

public final class MonitorPalette {
    public static final int PIXELS_PER_BLOCK = 128;
    private static final int[] ARGB_BY_BLIT = {
        Colour.WHITE.getARGB(),
        Colour.ORANGE.getARGB(),
        Colour.MAGENTA.getARGB(),
        Colour.LIGHT_BLUE.getARGB(),
        Colour.YELLOW.getARGB(),
        Colour.LIME.getARGB(),
        Colour.PINK.getARGB(),
        Colour.GREY.getARGB(),
        Colour.LIGHT_GREY.getARGB(),
        Colour.CYAN.getARGB(),
        Colour.PURPLE.getARGB(),
        Colour.BLUE.getARGB(),
        Colour.BROWN.getARGB(),
        Colour.GREEN.getARGB(),
        Colour.RED.getARGB(),
        Colour.BLACK.getARGB()
    };

    private MonitorPalette() {
    }

    public static int size() {
        return ARGB_BY_BLIT.length;
    }

    public static int argb(int index) {
        int normalized = Math.max(0, Math.min(ARGB_BY_BLIT.length - 1, index));
        return ARGB_BY_BLIT[normalized];
    }

    public static byte nearestIndex(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha < 8) return (byte) 15;

        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;

        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int index = 0; index < ARGB_BY_BLIT.length; index++) {
            int palette = ARGB_BY_BLIT[index];
            int pr = (palette >>> 16) & 0xFF;
            int pg = (palette >>> 8) & 0xFF;
            int pb = palette & 0xFF;
            long distance = square(red - pr) + square(green - pg) + square(blue - pb);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }

        return (byte) bestIndex;
    }

    public static int coerceArgb(long value) {
        if (value > 0 && (value & (value - 1L)) == 0L && value <= 0x8000L) {
            int bit = Long.numberOfTrailingZeros(value);
            return argb(bit);
        }

        if ((value & 0xFF00_0000L) == 0L) return (int) (0xFF00_0000L | (value & 0x00FF_FFFFL));
        return (int) value;
    }

    private static long square(int value) {
        return (long) value * value;
    }
}
