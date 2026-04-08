package dev.gfortes.ccpython.network;

import dev.gfortes.ccpython.CCPythonMod;

public final class ClientTerminalSync {
    private ClientTerminalSync() {
    }

    public static void describe() {
        CCPythonMod.LOGGER.debug(
            "Python terminal I/O relies on CC: Tweaked native terminal packets; custom payloads only mirror runtime metadata."
        );
    }
}
