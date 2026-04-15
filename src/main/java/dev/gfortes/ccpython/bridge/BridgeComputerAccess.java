package dev.gfortes.ccpython.bridge;

import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.shared.computer.core.ServerComputer;
import java.lang.reflect.Field;

final class BridgeComputerAccess {
    private static final Field INNER_COMPUTER_FIELD = findInnerComputerField();

    private final ServerComputer serverComputer;
    private final Computer computer;
    private final IAPIEnvironment environment;

    private BridgeComputerAccess(ServerComputer serverComputer, Computer computer, IAPIEnvironment environment) {
        this.serverComputer = serverComputer;
        this.computer = computer;
        this.environment = environment;
    }

    static BridgeComputerAccess resolve(ServerComputer serverComputer) throws ReflectiveOperationException {
        var computer = (Computer) INNER_COMPUTER_FIELD.get(serverComputer);
        if (computer == null) {
            throw new IllegalStateException("Failed to resolve the internal Computer instance for CC computer " + serverComputer.getID() + ".");
        }

        return new BridgeComputerAccess(serverComputer, computer, computer.getAPIEnvironment());
    }

    ServerComputer serverComputer() {
        return serverComputer;
    }

    Computer computer() {
        return computer;
    }

    IAPIEnvironment environment() {
        return environment;
    }

    FileSystem fileSystem() {
        return environment.getFileSystem();
    }

    private static Field findInnerComputerField() {
        try {
            Field field = ServerComputer.class.getDeclaredField("computer");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access ServerComputer internals for the dev bridge.", exception);
        }
    }
}
