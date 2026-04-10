package dev.gfortes.ccpython.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dan200.computercraft.client.integration.ShaderMod;
import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity;
import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.monitor.MonitorGraphicsKey;
import dev.gfortes.ccpython.monitor.MonitorGraphicsUtil;
import dev.gfortes.ccpython.monitor.MonitorPalette;
import dev.gfortes.ccpython.network.payload.MonitorGraphicsClearPayload;
import dev.gfortes.ccpython.network.payload.MonitorGraphicsFramePayload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

public final class ClientMonitorGraphicsState {
    private static final float TERMINAL_EDGE = 0.034375f;
    private static final Map<MonitorGraphicsKey, Entry> ENTRIES = new ConcurrentHashMap<>();

    private ClientMonitorGraphicsState() {
    }

    public static void apply(MonitorGraphicsFramePayload payload) {
        var key = payload.key();
        ENTRIES.compute(key, (ignored, existing) -> {
            if (existing != null) existing.close();
            return new Entry(payload);
        });
    }

    public static void apply(MonitorGraphicsClearPayload payload) {
        var removed = ENTRIES.remove(payload.key());
        if (removed != null) removed.close();
    }

    public static void clearAll() {
        for (var entry : ENTRIES.values()) entry.close();
        ENTRIES.clear();
    }

    public static void renderOverlay(MonitorBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (blockEntity == null || blockEntity.getLevel() == null) return;
        if (ShaderMod.get().isRenderingShadowPass()) return;

        var clientMonitor = blockEntity.getOriginClientMonitor();
        if (clientMonitor == null) return;
        var origin = clientMonitor.getOrigin();
        if (origin == null) return;

        var key = MonitorGraphicsUtil.key(origin);
        var entry = ENTRIES.get(key);
        if (entry == null) return;

        poseStack.pushPose();
        var currentPos = blockEntity.getBlockPos();
        var originPos = origin.getBlockPos();
        poseStack.translate(
            originPos.getX() - currentPos.getX() + 0.5D,
            originPos.getY() - currentPos.getY() + 0.5D,
            originPos.getZ() - currentPos.getZ() + 0.5D
        );
        poseStack.mulPose(com.mojang.math.Axis.YN.rotationDegrees(origin.getDirection().toYRot()));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(dan200.computercraft.shared.util.DirectionUtil.toPitchAngle(origin.getFront())));
        poseStack.translate(-0.34375D, origin.getHeight() - 0.5D - 0.15625D, 0.5D);

        float left = -TERMINAL_EDGE;
        float top = TERMINAL_EDGE;
        float right = (float) (origin.getWidth() - 0.3125D + TERMINAL_EDGE * 2.0D);
        float bottom = (float) -(origin.getHeight() - 0.3125D + TERMINAL_EDGE * 2.0D);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(entry.textureLocation()));
        consumer.addVertex(matrix, left, bottom, 0.001f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
        consumer.addVertex(matrix, right, bottom, 0.001f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
        consumer.addVertex(matrix, right, top, 0.001f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
        consumer.addVertex(matrix, left, top, 0.001f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
        poseStack.popPose();
    }

    private static final class Entry implements AutoCloseable {
        private final ResourceLocation textureLocation;
        private final DynamicTexture texture;

        private Entry(MonitorGraphicsFramePayload payload) {
            NativeImage image = new NativeImage(payload.pixelWidth(), payload.pixelHeight(), false);
            byte[] pixels = payload.pixels();
            for (int y = 0; y < payload.pixelHeight(); y++) {
                for (int x = 0; x < payload.pixelWidth(); x++) {
                    int index = y * payload.pixelWidth() + x;
                    image.setPixelRGBA(x, y, MonitorPalette.argb(Byte.toUnsignedInt(pixels[index])));
                }
            }

            texture = new DynamicTexture(image);
            texture.setFilter(false, false);
            textureLocation = ResourceLocation.fromNamespaceAndPath(
                CCPythonMod.MOD_ID,
                "monitor/" + sanitize(payload.dimension()) + "/" + payload.origin().getX() + "_" + payload.origin().getY() + "_" + payload.origin().getZ()
            );
            Minecraft.getInstance().getTextureManager().register(textureLocation, texture);
            texture.upload();
        }

        private ResourceLocation textureLocation() {
            return textureLocation;
        }

        @Override
        public void close() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return;
            minecraft.getTextureManager().release(textureLocation);
            texture.close();
        }

        private static String sanitize(String value) {
            return value.replace(':', '_').replace('/', '_');
        }
    }
}
