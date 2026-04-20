package dev.gfortes.ccpython.bridge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DevBridgeCommand {
    private DevBridgeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ccpython")
                .then(
                    Commands.literal("access")
                        .then(
                            Commands.literal("info")
                                .then(Commands.argument("computerId", IntegerArgumentType.integer(0))
                                    .executes(context -> info(context.getSource(), IntegerArgumentType.getInteger(context, "computerId"))))
                        )
                        .then(
                            Commands.literal("claim")
                                .then(Commands.argument("computerId", IntegerArgumentType.integer(0))
                                    .executes(context -> claim(context.getSource(), IntegerArgumentType.getInteger(context, "computerId"))))
                        )
                        .then(
                            Commands.literal("grant")
                                .then(Commands.argument("computerId", IntegerArgumentType.integer(0))
                                    .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> grant(
                                            context.getSource(),
                                            IntegerArgumentType.getInteger(context, "computerId"),
                                            EntityArgument.getPlayer(context, "player")
                                        ))))
                        )
                        .then(
                            Commands.literal("revoke")
                                .then(Commands.argument("computerId", IntegerArgumentType.integer(0))
                                    .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> revoke(
                                            context.getSource(),
                                            IntegerArgumentType.getInteger(context, "computerId"),
                                            EntityArgument.getPlayer(context, "player")
                                        ))))
                        )
                        .then(
                            Commands.literal("set-owner")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("computerId", IntegerArgumentType.integer(0))
                                    .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> setOwner(
                                            context.getSource(),
                                            IntegerArgumentType.getInteger(context, "computerId"),
                                            EntityArgument.getPlayer(context, "player")
                                        ))))
                        )
                )
                .then(
                    Commands.literal("pair")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(context -> pair(context.getSource(), EntityArgument.getPlayer(context, "player"))))
                )
        );
    }

    private static int info(CommandSourceStack source, int computerId) {
        return execute(source, () -> {
            DevBridgeAccessStore.ComputerAcl acl = DevBridgeManager.getInstance().acl(source.getServer(), computerId);
            if (acl == null || acl.ownerUuid() == null) {
                source.sendSuccess(() -> Component.literal("Computer " + computerId + " is loaded but currently unowned."), false);
                return 1;
            }

            String whitelist = acl.whitelist().isEmpty()
                ? "none"
                : String.join(", ", acl.whitelist().values());
            source.sendSuccess(() -> Component.literal(
                "Computer " + computerId + " owner: " + acl.ownerName() + " (" + acl.ownerUuid() + "), whitelist: " + whitelist
            ), false);
            return 1;
        });
    }

    private static int claim(CommandSourceStack source, int computerId) {
        return execute(source, () -> {
            ServerPlayer player = source.getPlayerOrException();
            DevBridgeAccessStore.ComputerAcl acl = DevBridgeManager.getInstance().claimOwnership(
                source.getServer(),
                computerId,
                player.getUUID(),
                player.getGameProfile().getName()
            );
            source.sendSuccess(() -> Component.literal(
                "Computer " + computerId + " is now owned by " + acl.ownerName() + "."
            ), true);
            return 1;
        });
    }

    private static int grant(CommandSourceStack source, int computerId, ServerPlayer target) {
        return execute(source, () -> {
            ServerPlayer actor = source.getPlayer();
            DevBridgeAccessStore.ComputerAcl acl = DevBridgeManager.getInstance().grantAccess(
                source.getServer(),
                computerId,
                actor == null ? null : actor.getUUID(),
                actor == null ? source.getTextName() : actor.getGameProfile().getName(),
                source.hasPermission(2),
                target.getUUID(),
                target.getGameProfile().getName()
            );
            source.sendSuccess(() -> Component.literal(
                "Granted " + target.getGameProfile().getName() + " access to computer " + computerId + ". Whitelist size: " + acl.whitelist().size()
            ), true);
            return 1;
        });
    }

    private static int revoke(CommandSourceStack source, int computerId, ServerPlayer target) {
        return execute(source, () -> {
            ServerPlayer actor = source.getPlayer();
            DevBridgeAccessStore.ComputerAcl acl = DevBridgeManager.getInstance().revokeAccess(
                source.getServer(),
                computerId,
                actor == null ? null : actor.getUUID(),
                actor == null ? source.getTextName() : actor.getGameProfile().getName(),
                source.hasPermission(2),
                target.getUUID()
            );
            source.sendSuccess(() -> Component.literal(
                "Revoked " + target.getGameProfile().getName() + " access to computer " + computerId + ". Whitelist size: " + acl.whitelist().size()
            ), true);
            return 1;
        });
    }

    private static int setOwner(CommandSourceStack source, int computerId, ServerPlayer target) {
        return execute(source, () -> {
            DevBridgeAccessStore.ComputerAcl acl = DevBridgeManager.getInstance().setOwner(
                source.getServer(),
                computerId,
                target.getUUID(),
                target.getGameProfile().getName()
            );
            source.sendSuccess(() -> Component.literal(
                "Computer " + computerId + " owner set to " + acl.ownerName() + "."
            ), true);
            return 1;
        });
    }

    private static int pair(CommandSourceStack source, ServerPlayer player) {
        return execute(source, () -> {
            DevBridgeAuthStore.PairingCode pairing = DevBridgeManager.getInstance().startPlayerPairing(
                "VS Code",
                player.getUUID(),
                player.getGameProfile().getName()
            );
            source.sendSuccess(() -> Component.literal(
                "Bridge pair code for " + player.getGameProfile().getName() + ": " + pairing.code()
                    + " (expires " + pairing.toMap().get("expires_at_iso") + ")"
            ), false);
            return 1;
        });
    }

    private static int execute(CommandSourceStack source, CommandAction action) {
        try {
            return action.run();
        } catch (Exception exception) {
            source.sendFailure(Component.literal(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
            return 0;
        }
    }

    @FunctionalInterface
    private interface CommandAction {
        int run() throws Exception;
    }
}
