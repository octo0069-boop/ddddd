package com.example.speedforce.client;

import com.example.speedforce.ModParticles;
import com.example.speedforce.Speedforce;
import com.example.speedforce.net.ActionPayload;
import com.example.speedforce.server.WaterRun;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    private static final String CAT = "key.categories.speedforce";
    public static final KeyMapping UP = key("up", GLFW.GLFW_KEY_Z);
    public static final KeyMapping DOWN = key("down", GLFW.GLFW_KEY_X);
    public static final KeyMapping STRIKE = key("strike", GLFW.GLFW_KEY_R);
    public static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_G);
    public static final KeyMapping THEME = key("theme", GLFW.GLFW_KEY_V);

    private ClientEvents() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.speedforce." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CAT);
    }

    // ---- MOD bus ----
    public static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(UP); e.register(DOWN); e.register(STRIKE); e.register(DASH); e.register(THEME);
    }

    public static void registerLayers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(Speedforce.id("speed_lines"), new SpeedLinesLayer());
        e.registerAboveAll(Speedforce.id("hud"), new HudLayer());
    }

    public static void registerParticles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.GOLD_SPARK.get(), sprites -> (type, lv, x, y, z, xd, yd, zd) ->
                new BoltSparkParticle(lv, x, y, z, xd, yd, zd, sprites, false));
        e.registerSpriteSet(ModParticles.RED_SPARK.get(), sprites -> (type, lv, x, y, z, xd, yd, zd) ->
                new BoltSparkParticle(lv, x, y, z, xd, yd, zd, sprites, false));
        e.registerSpriteSet(ModParticles.GLOW.get(), sprites -> (type, lv, x, y, z, xd, yd, zd) ->
                new BoltSparkParticle(lv, x, y, z, xd, yd, zd, sprites, true));
    }

    // ---- GAME bus ----
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // Requests only: the server decides whether anything happens (and applies cooldown, stamina, rate limit).
        while (UP.consumeClick()) PacketDistributor.sendToServer(new ActionPayload(ActionPayload.UP));
        while (DOWN.consumeClick()) PacketDistributor.sendToServer(new ActionPayload(ActionPayload.DOWN));
        while (STRIKE.consumeClick()) PacketDistributor.sendToServer(new ActionPayload(ActionPayload.STRIKE));
        while (DASH.consumeClick()) PacketDistributor.sendToServer(new ActionPayload(ActionPayload.DASH));
        while (THEME.consumeClick()) PacketDistributor.sendToServer(new ActionPayload(ActionPayload.THEME));
        if (mc.isPaused()) return;
        // Local prediction input: the real client velocity. ClientState eases it toward the server snapshot.
        ClientState.tick(mc.player.getDeltaMovement().horizontalDistance());
        ClientRemoteVisuals.tick();
        ClientBolts.tick();
    }

    /**
     * Water-running prediction for the LOCAL player. Hooked on PlayerTickEvent.Post, not ClientTickEvent.Post:
     * LocalPlayer.tick() runs Player.tick() (which fires this) and THEN sendPosition(), so the onGround flag set
     * here is the one the server receives. Permission comes from the server (FLAG_WATER_ENABLED), never from
     * the client's own config file.
     */
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player || !e.getEntity().level().isClientSide()) return;
        ClientState.waterPredicting = WaterRun.predict(mc.player, ClientState.level > 0 && ClientState.waterAllowed(), ClientState.waterPredicting);
    }

    public static void onFov(ViewportEvent.ComputeFov e) {
        if (ClientState.level <= 0 || !ClientConfig.FOV_STRETCH.get()) return;
        // Vanilla scales FOV by the movement-speed attribute, which explodes at high levels. Replace it.
        double base = Minecraft.getInstance().options.fov().get();
        e.setFOV(base + ClientState.level * 6.0);
    }

    public static void onRender(RenderLevelStageEvent e) { BoltRenderer.render(e); }

    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) { resetAll(); }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { resetAll(); }

    /** The local player object was replaced (respawn / dimension change): prediction state is meaningless now. */
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone e) { resetAll(); }

    /** An entity left the client level: forget its remote visual state (keeps the map bounded and correct). */
    public static void onEntityLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide()) ClientRemoteVisuals.remove(e.getEntity().getId());
    }

    private static void resetAll() {
        ClientState.reset();
        ClientRemoteVisuals.clear();
        ClientBolts.clear();
    }
}
