package com.example.speedforce.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;

/** Speedforce level pips, live speedometer and ability cooldowns (bottom-left). */
public class HudLayer implements LayeredDraw.Layer {
    @Override
    public void render(GuiGraphics gfx, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.HUD.get() || mc.player == null || mc.options.hideGui) return;

        int x = 10, y = gfx.guiHeight() - 77;
        gfx.fill(x - 4, y - 4, x + 124, y + 67, 0x88000000);
        gfx.drawString(mc.font, "SPEEDFORCE", x, y, 0xFFD21A, true);

        for (int i = 0; i < 5; i++) {
            int c = i < ClientState.level ? (i >= 3 ? 0xFFFF3A2A : 0xFFFFD21A) : 0xFF333333;
            gfx.fill(x + i * 24, y + 12, x + i * 24 + 20, y + 18, c);
        }

        // smoothed, server-reconciled horizontal speed (blocks/tick -> m/s) plus the server's velocity tier
        gfx.drawString(mc.font, String.format("%.1f m/s  T%d", ClientState.speed(delta.getGameTimeDeltaPartialTick(false)) * 20.0, ClientState.tier), x, y + 22, 0xFFFFFF, true);

        stamina(gfx, mc, x, y + 34, delta.getGameTimeDeltaPartialTick(false));
        bar(gfx, mc, x, y + 45, "R", ClientEvents.STRIKE.getTranslatedKeyMessage().getString(), ClientState.strikeCd, ClientState.strikeMax);
        bar(gfx, mc, x, y + 56, "G", ClientEvents.DASH.getTranslatedKeyMessage().getString(), ClientState.dashCd, ClientState.dashMax);
    }

    /** Kinetic stamina (server-owned). Red while exhausted. */
    private static void stamina(GuiGraphics gfx, Minecraft mc, int x, int y, float pt) {
        gfx.drawString(mc.font, "Stamina", x, y, 0xCCCCCC, false);
        int bx = x + 66, bw = 54;
        gfx.fill(bx, y + 2, bx + bw, y + 7, 0xFF333333);
        gfx.fill(bx, y + 2, bx + (int) (bw * Mth.clamp(ClientState.stamina(pt), 0f, 1f)), y + 7, ClientState.exhausted() ? 0xFFCC2222 : 0xFF40C0FF);
    }

    private static void bar(GuiGraphics gfx, Minecraft mc, int x, int y, String name, String key, int cd, int max) {
        String label = (name.equals("R") ? "Strike " : "Dash ") + "[" + key + "]";
        gfx.drawString(mc.font, label, x, y, 0xCCCCCC, false);
        int bx = x + 66, bw = 54;
        gfx.fill(bx, y + 2, bx + bw, y + 7, 0xFF333333);
        float ready = 1f - (cd / (float) Math.max(1, max));
        gfx.fill(bx, y + 2, bx + (int) (bw * ready), y + 7, cd == 0 ? 0xFFFFE040 : 0xFFAA6600);
    }
}
