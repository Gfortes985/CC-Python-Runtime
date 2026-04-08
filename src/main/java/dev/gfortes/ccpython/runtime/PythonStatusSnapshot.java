package dev.gfortes.ccpython.runtime;

public record PythonStatusSnapshot(
    int computerId,
    String processId,
    PythonProcessState state,
    String program,
    boolean interactive,
    long startedAt,
    String detail
) {
}
