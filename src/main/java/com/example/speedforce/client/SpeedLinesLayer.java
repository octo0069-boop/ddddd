package com.example.speedforce.client;

import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;

/** Golden radial speed streaks, stronger the faster you actually move. */
public class SpeedLinesLayer implements LayeredDraw.Layer {
    @Override
    public void render(GuiGraphics gfx, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.SPEED_LINES.get() || ClientState.level <= 0 || mc.player == null || mc.options.hideGui) return;

        float t = Mth.clamp((float) (mc.player.getDeltaMovement().horizontalDistance() - 0.5) / 2.0f, 0f, 1f);
        t = Math.max(t, ClientState.level * 0.06f);
        int w = gfx.guiWidth(), h = gfx.guiHeight();
        float maxR = (float) Math.hypot(w, h) / 2f;
        gfx.fill(0, 0, w, h, ((int) (t * 34) << 24) | 0xFF8A1A);   // faint warm speed tint
        Random rng = new Random((mc.player.tickCount / 2) * 7919L);
        int lines = 30 + ClientState.level * 8;
        for (int i = 0; i < lines; i++) {
            float angle = rng.nextFloat() * 360f;
            float inner = maxR * (0.50f + rng.nextFloat() * 0.35f);
            float len = maxR * (0.06f + rng.nextFloat() * 0.2f * t);
            int rgb = rng.nextInt(4) == 0 ? 0xFF9A2A : 0xFFF0A8;
            int color = ((int) (t * 120) << 24) | rgb;
            gfx.pose().pushPose();
            gfx.pose().translate(w / 2f, h / 2f, 0);
            gfx.pose().mulPose(Axis.ZP.rotationDegrees(angle));
            gfx.fill((int) inner, 0, (int) (inner + len), 1, color);
            gfx.pose().popPose();
        }
    }
}
