package dev.gfortes.ccpython.mixin;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.LuaTable;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import dev.gfortes.ccpython.audio.SpeakerHiFiAudioManager;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpeakerPeripheral.class)
public abstract class SpeakerPeripheralMixin {
    @Shadow @Final private AttachedComputerSet computers;

    @Shadow protected abstract ServerLevel getLevel();

    @Shadow protected abstract SpeakerPosition getPosition();

    @Shadow public abstract UUID getSource();

    @LuaFunction({"playAudio16", "playHifiAudio"})
    public final boolean ccpython$playAudio16(ILuaContext context, LuaTable<?, ?> audio, Optional<Double> volume) throws LuaException {
        short[] samples = SpeakerHiFiAudioManager.decodeSamples(audio);
        return SpeakerHiFiAudioManager.play(getLevel(), getPosition(), getSource(), samples, volume.orElse(1.0));
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void ccpython$updateHiFi(CallbackInfo ci) {
        var result = SpeakerHiFiAudioManager.update(getSource());
        if (result.notifyRoom()) computers.queueEvent(SpeakerHiFiAudioManager.BUFFER_EVENT);
        if (result.notifyDrain()) computers.queueEvent(SpeakerHiFiAudioManager.DRAIN_EVENT);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void ccpython$stopHiFi(CallbackInfo ci) {
        SpeakerHiFiAudioManager.stop(getLevel(), getSource());
    }
}
