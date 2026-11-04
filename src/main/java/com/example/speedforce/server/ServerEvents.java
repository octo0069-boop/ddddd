package com.example.speedforce.server;

import com.example.speedforce.Speedforce;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game-bus (default bus) events, run on the logical server. Handlers ignore client-side players. */
@EventBusSubscriber(modid = Speedforce.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp) ServerLogic.tick(sp);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) ServerLogic.reset(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) ServerLogic.reset(sp);
    }

    /** Client wiped its prediction when the dimension changed; resend the full state. */
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) ServerLogic.onDimensionChange(sp);
    }

    /** A player came into range of another player: hand them that player's current visual state. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer target) {
            ServerLogic.onStartTracking(viewer, target);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) ServerLogic.forget(sp);
    }

    /** Static state must not survive into the next integrated-server world. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        ServerLogic.clearAll();
    }
}
