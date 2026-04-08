package dev.gfortes.ccpython.runtime;

import java.util.List;

public record PythonLaunchSpec(String program, String cwd, List<Object> args, boolean interactive) {
}
