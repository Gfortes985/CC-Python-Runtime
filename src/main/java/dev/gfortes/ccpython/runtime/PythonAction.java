package dev.gfortes.ccpython.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PythonAction(
    Kind kind,
    String module,
    String method,
    List<Object> arguments,
    List<Object> results,
    String message,
    String traceback
) {
    public enum Kind {
        HOST_CALL,
        DONE,
        ERROR
    }

    public static PythonAction hostCall(String module, String method, List<Object> arguments) {
        return new PythonAction(Kind.HOST_CALL, module, method, arguments, List.of(), null, null);
    }

    public static PythonAction done(List<Object> results) {
        return new PythonAction(Kind.DONE, null, null, List.of(), results, null, null);
    }

    public static PythonAction error(String message, String traceback) {
        return new PythonAction(Kind.ERROR, null, null, List.of(), List.of(), message, traceback);
    }

    public Map<String, Object> toLuaTable() {
        var table = new LinkedHashMap<String, Object>();
        table.put("kind", kind.name().toLowerCase());
        if (module != null) table.put("module", module);
        if (method != null) table.put("method", method);
        if (!arguments.isEmpty()) table.put("args", arguments);
        if (!results.isEmpty()) {
            table.put("results", results);
            table.put("result_count", results.size());
        }
        if (message != null) table.put("message", message);
        if (traceback != null) table.put("traceback", traceback);
        return table;
    }
}
