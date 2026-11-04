package com.example.speedforce.net;

import com.example.speedforce.Speedforce;
import com.example.speedforce.server.ServerLogic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: a REQUEST. Carries no gameplay state, only which action the player pressed.
 * 0 level up, 1 level down, 2 off, 3 lightning strike, 4 flash dash, 5 toggle theme (gold/red).
 * Validation (range, rate limit, cooldown, stamina) happens in ServerLogic.onAction.
 */
public record ActionPayload(int action) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE = new Type<>(Speedforce.id("action"));
    public static final StreamCodec<ByteBuf, ActionPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(ActionPayload::new, ActionPayload::action);

    public static final int UP = 0, DOWN = 1, OFF = 2, STRIKE = 3, DASH = 4, THEME = 5;
    public static final int COUNT = 6;

    public static boolean isValid(int action) { return action >= 0 && action < COUNT; }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ActionPayload p, IPayloadContext ctx) {
        // Unknown ids are dropped silently (no disconnect: a newer client must not kick itself off an older server).
        if (!isValid(p.action())) return;
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) ServerLogic.onAction(sp, p.action());
        });
    }
}
