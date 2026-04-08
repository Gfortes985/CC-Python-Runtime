package dev.gfortes.ccpython.runtime;

public enum PythonProcessState {
    STARTING,
    RUNNING,
    WAITING_HOST,
    WAITING_EVENT,
    COMPLETED,
    FAILED,
    KILLED
}
