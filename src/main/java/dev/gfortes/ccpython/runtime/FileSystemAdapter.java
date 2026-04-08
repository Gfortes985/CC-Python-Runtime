package dev.gfortes.ccpython.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class FileSystemAdapter {
    private static final List<String> PACKAGE_DIR_NAMES = List.of("lib", "site-packages", "packages");

    private FileSystemAdapter() {
    }

    public static String normalizeWorkingDir(String cwd) {
        return sanitize(cwd == null || cwd.isBlank() ? "/" : cwd);
    }

    public static String normalizeProgramPath(String program, String cwd) {
        if (program == null || program.isBlank()) return null;
        var path = program.startsWith("/") ? program : combine(normalizeWorkingDir(cwd), program);
        return sanitize(path);
    }

    public static List<String> buildSearchPath(PythonLaunchSpec spec) {
        var searchPath = new ArrayList<String>();
        if (spec.program() != null) {
            addSearchRoot(searchPath, parent(spec.program()));
        }
        addSearchRoot(searchPath, normalizeWorkingDir(spec.cwd()));
        addSearchRoot(searchPath, "/");
        return searchPath.stream().distinct().toList();
    }

    private static void addSearchRoot(List<String> searchPath, String base) {
        var normalized = normalizeWorkingDir(base);
        searchPath.add(normalized);
        for (var packageDir : PACKAGE_DIR_NAMES) searchPath.add(combine(normalized, packageDir));
    }

    public static String parent(String path) {
        var normalized = sanitize(path);
        var index = normalized.lastIndexOf('/');
        if (index <= 0) return "/";
        return normalized.substring(0, index);
    }

    public static String combine(String left, String right) {
        if (right == null || right.isBlank()) return sanitize(left);
        if (right.startsWith("/")) return sanitize(right);
        if (left == null || left.isBlank() || "/".equals(left)) return sanitize("/" + right);
        return sanitize(left + "/" + right);
    }

    public static String sanitize(String raw) {
        var input = raw.replace('\\', '/');
        var absolute = input.startsWith("/") ? input : "/" + input;
        var parts = absolute.split("/");
        var stack = new ArrayDeque<String>();
        for (var part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!stack.isEmpty()) stack.removeLast();
                continue;
            }
            stack.addLast(part);
        }

        if (stack.isEmpty()) return "/";
        var builder = new StringBuilder();
        for (var part : stack) builder.append('/').append(part);
        return builder.toString();
    }
}
