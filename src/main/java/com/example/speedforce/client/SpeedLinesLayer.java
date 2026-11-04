package com.example.speedforce.client;

import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;

/** Velocity-sensitive radial streaks and restrained screen-edge effects (not a post-processing motion-blur shader). */
public class SpeedLinesLayer implements LayeredDraw.Layer {
    private static final Random RANDOM = new Random();
    private static int seededTick = Integer.MIN_VALUE;
    private static float intensity;
    private static float previousIntensity;
    private static int currentTick = Integer.MIN_VALUE;

    @Override
    public void render(GuiGraphics gfx, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int tick = mc.player.tickCount;
        if (tick != currentTick) {
            previousIntensity = intensity;
            float speed = ClientState.speed(1.0f);
            float target = Mth.clamp((speed - 0.35f) / 2.8f, 0.0f, 1.0f);
            target = Math.max(target, ClientState.tierBlend(1.0f) * 0.14f);
            if (ClientConfig.REDUCED_MOTION.get()) target *= 0.25f;
            intensity += (target - intensity) * (target > intensity ? 0.38f : 0.12f);
            currentTick = tick;
        }
        if (ClientState.level <= 0 || intensity < 0.015f) return;

        float pt = delta.getGameTimeDeltaPartialTick(false);
        float amount = Mth.lerp(pt, previousIntensity, intensity);
        int width = gfx.guiWidth(), height = gfx.guiHeight();
        float radius = (float) Math.hypot(width, height) * 0.5f;
        boolean red = ClientState.negativeTheme();
        int lineCount = Math.min(56, 12 + ClientState.tier * 9);

        if (seededTick != tick) {
            RANDOM.setSeed((long) tick * 7919L + 0x51F0L);
            seededTick = tick;
        }
        if (ClientConfig.SPEED_LINES.get()) {
            for (int i = 0; i < lineCount; i++) {
                float angle = RANDOM.nextFloat() * 360f;
                float inner = radius * (0.50f + RANDOM.nextFloat() * 0.33f);
                float length = radius * (0.035f + RANDOM.nextFloat() * 0.20f * amount);
                int rgb = red ? (RANDOM.nextInt(3) == 0 ? 0xFF5A35 : 0xFFFFB0) : (RANDOM.nextInt(3) == 0 ? 0xFFC040 : 0xFFFFE0);
                int alpha = Mth.clamp((int) (amount * (ClientConfig.REDUCED_MOTION.get() ? 48 : 100)), 0, 100);
                gfx.pose().pushPose();
                gfx.pose().translate(width * 0.5f, height * 0.5f, 0);
                gfx.pose().mulPose(Axis.ZP.rotationDegrees(angle));
                gfx.fill((int) inner, 0, (int) (inner + length), 1, (alpha << 24) | rgb);
                gfx.pose().popPose();
            }
        }

        if (ClientConfig.VIGNETTE.get()) drawEdgeShade(gfx, width, height, amount, false);
        if (ClientConfig.TUNNEL_VISION.get() && !ClientConfig.REDUCED_MOTION.get() && ClientState.tier >= 4) {
            drawEdgeShade(gfx, width, height, amount * 0.7f, true);
        }
    }

    private static void drawEdgeShade(GuiGraphics gfx, int width, int height, float intensity, boolean tunnel) {
        int alpha = Mth.clamp((int) (intensity * (tunnel ? 72 : 30)), 0, 72);
        if (alpha == 0) return;
        int color = alpha << 24;
        int edgeX = Math.max(1, (int) (width * (tunnel ? 0.12f : 0.035f) * intensity));
        int edgeY = Math.max(1, (int) (height * (tunnel ? 0.10f : 0.03f) * intensity));
        gfx.fill(0, 0, edgeX, height, color);
        gfx.fill(width - edgeX, 0, width, height, color);
        gfx.fill(0, 0, width, edgeY, color);
        gfx.fill(0, height - edgeY, width, height, color);
    }
}
