package dev.gfortes.ccpython.bridge;

import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.shared.computer.core.ServerComputer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

final class BridgeComputerAccess {
    private static final Field INNER_COMPUTER_FIELD = findInnerComputerField();
    private static final Constructor<?> COMPUTER_SYSTEM_CONSTRUCTOR = findComputerSystemConstructor();
    private static final Method COMPUTER_SYSTEM_ACTIVATE = findComputerSystemActivateMethod();

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

    IComputerSystem computerSystem() {
        try {
            Object system = COMPUTER_SYSTEM_CONSTRUCTOR.newInstance(serverComputer, environment, Map.of());
            COMPUTER_SYSTEM_ACTIVATE.invoke(system);
            return (IComputerSystem) system;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to construct a bridge ComputerSystem for computer " + serverComputer.getID() + ".", exception);
        }
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

    private static Constructor<?> findComputerSystemConstructor() {
        try {
            Class<?> type = Class.forName("dan200.computercraft.shared.computer.core.ComputerSystem");
            Constructor<?> constructor = type.getDeclaredConstructor(ServerComputer.class, IAPIEnvironment.class, Map.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access ComputerSystem internals for the dev bridge.", exception);
        }
    }

    private static Method findComputerSystemActivateMethod() {
        try {
            Method method = COMPUTER_SYSTEM_CONSTRUCTOR.getDeclaringClass().getDeclaredMethod("activate");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access ComputerSystem.activate() for the dev bridge.", exception);
        }
    }
}
