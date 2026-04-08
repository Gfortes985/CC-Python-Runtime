package dev.gfortes.ccpython;

import com.mojang.logging.LogUtils;
import dan200.computercraft.api.ComputerCraftAPI;
import dev.gfortes.ccpython.api.PythonLuaApi;
import dev.gfortes.ccpython.config.CCPythonConfig;
import dev.gfortes.ccpython.network.PacketHandler;
import dev.gfortes.ccpython.runtime.SandboxManager;
import dev.gfortes.ccpython.runtime.PythonRuntimeManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(CCPythonMod.MOD_ID)
public final class CCPythonMod {
    public static final String MOD_ID = "ccpython";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CCPythonMod(IEventBus modBus, ModContainer modContainer) {
        ComputerCraftAPI.registerAPIFactory(PythonLuaApi::new);

        modBus.addListener(PacketHandler::register);
        modContainer.registerConfig(ModConfig.Type.COMMON, CCPythonConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerChangedDimension);
    }

    private void onServerStarted(ServerStartedEvent event) {
        SandboxManager.getInstance().warmUpAsync();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        PythonRuntimeManager.getInstance().shutdownServer(event.getServer());
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            PythonRuntimeManager.getInstance().syncPlayer(player);
        }
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            PythonRuntimeManager.getInstance().syncPlayer(player);
        }
    }

    private void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            PythonRuntimeManager.getInstance().syncPlayer(player);
        }
    }
}
