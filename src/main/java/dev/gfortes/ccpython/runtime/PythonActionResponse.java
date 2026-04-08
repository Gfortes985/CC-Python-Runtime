package dev.gfortes.ccpython.runtime;

import java.util.List;

public record PythonActionResponse(boolean ok, List<Object> values, String error) {
    public static PythonActionResponse success(List<Object> values) {
        return new PythonActionResponse(true, values, null);
    }

    public static PythonActionResponse failure(String error) {
        return new PythonActionResponse(false, List.of(), error);
    }
}
