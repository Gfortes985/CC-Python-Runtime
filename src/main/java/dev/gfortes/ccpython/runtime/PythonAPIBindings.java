package dev.gfortes.ccpython.runtime;

import dev.gfortes.ccpython.CCPythonMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;

public final class PythonAPIBindings {
    private static final PythonAPIBindings INSTANCE = new PythonAPIBindings();
    private static final String BOOTSTRAP_SOURCE = loadBootstrapSource();
    private static final Source BOOTSTRAP = buildBootstrapSource();

    private PythonAPIBindings() {
    }

    public static PythonAPIBindings getInstance() {
        return INSTANCE;
    }

    public void install(Context context, PythonComputerRuntime runtime) {
        context.getBindings("python").putMember("__ccpython_host", new RuntimeHostBridge(runtime));
        context.eval(BOOTSTRAP);
    }

    public void warmUp(Context context) {
        context.getBindings("python").putMember("__ccpython_host", new WarmupHostBridge());
        context.eval(BOOTSTRAP);
    }

    private static String loadBootstrapSource() {
        try (InputStream stream = PythonAPIBindings.class.getClassLoader().getResourceAsStream("python/ccpython_bootstrap.py")) {
            if (stream == null) throw new IllegalStateException("Missing python/ccpython_bootstrap.py resource.");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load python bootstrap.", exception);
        }
    }

    private static Source buildBootstrapSource() {
        try {
            return Source.newBuilder("python", BOOTSTRAP_SOURCE, "ccpython_bootstrap.py").build();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build Python bootstrap source.", exception);
        }
    }

    public static final class RuntimeHostBridge {
        private final PythonComputerRuntime runtime;

        private RuntimeHostBridge(PythonComputerRuntime runtime) {
            this.runtime = runtime;
        }

        @HostAccess.Export
        public Map<String, Object> call(String module, String method, List<Object> arguments) {
            var response = runtime.hostCall(module, method, arguments == null ? List.of() : arguments);
            return Map.of(
                "ok", response.ok(),
                "results", response.values(),
                "error", response.error() == null ? "" : response.error()
            );
        }
    }

    public static final class WarmupHostBridge {
        @HostAccess.Export
        public Map<String, Object> call(String module, String method, List<Object> arguments) {
            return Map.of(
                "ok", true,
                "results", List.of(),
                "error", ""
            );
        }
    }
}
