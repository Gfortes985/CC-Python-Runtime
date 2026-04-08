package dev.gfortes.ccpython.runtime;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.terminal.Terminal;
import dev.gfortes.ccpython.charset.CCTerminalCharset;
import java.util.List;

final class NativeLineReader {
    private static final int KEY_ENTER = 257;
    private static final int KEY_TAB = 258;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_LEFT = 263;
    private static final int KEY_HOME = 268;
    private static final int KEY_END = 269;
    private static final int KEY_NUMPAD_ENTER = 335;

    private final PythonProcess process;
    private final Terminal terminal;

    private final StringBuilder line = new StringBuilder();
    private final int startX;
    private final int startY;
    private int width;
    private int cursor;
    private int scroll;

    private NativeLineReader(PythonProcess process, Terminal terminal) {
        this.process = process;
        this.terminal = terminal;
        this.startX = terminal.getCursorX();
        this.startY = terminal.getCursorY();
        this.width = terminal.getWidth();
    }

    static String read(PythonProcess process, Terminal terminal) throws LuaException {
        var reader = new NativeLineReader(process, terminal);
        return reader.run();
    }

    private String run() throws LuaException {
        terminal.setCursorBlink(true);
        redraw();

        try {
            while (true) {
                var event = process.awaitComputerEvent(null, false);
                if (event.isEmpty()) continue;

                String eventName = String.valueOf(event.getFirst());
                switch (eventName) {
                    case "char" -> handleChar(event);
                    case "paste" -> handlePaste(event);
                    case "key" -> {
                        if (handleKey(event)) return finish();
                    }
                    case "mouse_click", "mouse_drag" -> handleMouse(event);
                    case "term_resize" -> {
                        width = terminal.getWidth();
                        redraw();
                    }
                    default -> {
                    }
                }
            }
        } finally {
            terminal.setCursorBlink(false);
        }
    }

    private String finish() {
        newline();
        return line.toString();
    }

    private void handleChar(List<Object> event) {
        if (event.size() < 2) return;

        String text = decode(event.get(1));
        if (text.isEmpty()) return;

        line.insert(cursor, text);
        cursor += text.length();
        redraw();
    }

    private void handlePaste(List<Object> event) {
        if (event.size() < 2) return;

        String text = decode(event.get(1));
        if (text.isEmpty()) return;

        line.insert(cursor, text);
        cursor += text.length();
        redraw();
    }

    private boolean handleKey(List<Object> event) {
        Integer key = intValue(event, 1);
        if (key == null) return false;

        return switch (key) {
            case KEY_ENTER, KEY_NUMPAD_ENTER -> true;
            case KEY_LEFT -> {
                if (cursor > 0) {
                    cursor--;
                    redraw();
                }
                yield false;
            }
            case KEY_RIGHT -> {
                if (cursor < line.length()) {
                    cursor++;
                    redraw();
                }
                yield false;
            }
            case KEY_BACKSPACE -> {
                if (cursor > 0) {
                    line.deleteCharAt(cursor - 1);
                    cursor--;
                    if (scroll > 0) scroll--;
                    redraw();
                }
                yield false;
            }
            case KEY_DELETE -> {
                if (cursor < line.length()) {
                    line.deleteCharAt(cursor);
                    redraw();
                }
                yield false;
            }
            case KEY_HOME -> {
                if (cursor > 0) {
                    cursor = 0;
                    redraw();
                }
                yield false;
            }
            case KEY_END -> {
                if (cursor < line.length()) {
                    cursor = line.length();
                    redraw();
                }
                yield false;
            }
            case KEY_TAB -> false;
            default -> false;
        };
    }

    private void handleMouse(List<Object> event) {
        if (event.size() < 4) return;
        Integer x = intValue(event, 2);
        Integer y = intValue(event, 3);
        if (x == null || y == null) return;

        int row = startY + 1;
        int minX = startX + 1;
        if (y != row || x < minX || x > width) return;

        cursor = Math.min(Math.max(scroll + x - minX, 0), line.length());
        redraw();
    }

    private void redraw() {
        int cursorPos = cursor - scroll;
        if (startX + cursorPos >= width) {
            scroll = startX + cursor - (width - 1);
        } else if (cursorPos < 0) {
            scroll = cursor;
        }
        if (scroll < 0) scroll = 0;

        terminal.setCursorPos(startX, startY);
        String visible = visibleText();
        writeRaw(visible);
        clearToEndOfLine(visible.length());
        terminal.setCursorPos(startX + cursor - scroll, startY);
    }

    private String visibleText() {
        if (scroll >= line.length()) return "";

        int available = Math.max(width - startX, 0);
        if (available <= 0) return "";

        String slice = line.substring(scroll, Math.min(line.length(), scroll + available));
        return CCTerminalCharset.mapTerminalString(slice);
    }

    private void clearToEndOfLine(int visibleLength) {
        int remaining = Math.max(width - startX - visibleLength, 0);
        if (remaining > 0) writeRaw(" ".repeat(remaining));
    }

    private void newline() {
        int nextY = startY + 1;
        if (nextY < terminal.getHeight()) {
            terminal.setCursorPos(0, nextY);
        } else {
            terminal.setCursorPos(0, terminal.getHeight() - 1);
            terminal.scroll(1);
        }
    }

    private void writeRaw(String value) {
        terminal.write(value);
        terminal.setCursorPos(terminal.getCursorX() + value.length(), terminal.getCursorY());
    }

    private static String decode(Object value) {
        return CCTerminalCharset.decodeTerminalString(value == null ? "" : value.toString());
    }

    private static Integer intValue(List<Object> event, int index) {
        if (event.size() <= index) return null;
        Object value = event.get(index);
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
