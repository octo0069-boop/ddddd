package com.example.speedforce.net;

import com.example.speedforce.Speedforce;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> owning client: authoritative snapshot of the player's own Speedforce state.
 * About 15-18 bytes. Sent when something meaningful changes (see ServerLogic.flush), never every tick.
 *
 * @param seq          monotonically increasing per player; the client drops anything not newer than what it has
 * @param serverTick   server game time (low 32 bits) when the snapshot was taken
 * @param level        SELECTED level 0-5 (what the player asked for)
 * @param tier         velocity tier 0-4 derived server-side from ACTUAL movement speed
 * @param flags        bit set, see FLAG_*
 * @param speedMilli   smoothed horizontal speed in blocks/tick x 1000 (0..65535)
 * @param staminaPerM  kinetic stamina in permille of max (0..1000)
 * @param strikeCd     remaining Lightning Strike cooldown, ticks
 * @param dashCd       remaining Flash Dash cooldown, ticks
 */
public record SyncPayload(int seq, int serverTick, int level, int tier, int flags,
                          int speedMilli, int staminaPerM, int strikeCd, int dashCd) implements CustomPacketPayload {
    public static final Type<SyncPayload> TYPE = new Type<>(Speedforce.id("sync"));

    /** Stamina hit zero; effective level is capped until it recovers. */
    public static final int FLAG_EXHAUSTED = 1;
    /** Server allows water running for this player right now (level + config). Client prediction keys off this. */
    public static final int FLAG_WATER_ENABLED = 1 << 1;
    /** Player is currently running on water (server-evaluated). */
    public static final int FLAG_WATER_ACTIVE = 1 << 2;
    /** Negative (red) Speedforce theme; clear = positive (gold). */
    public static final int FLAG_NEGATIVE_THEME = 1 << 3;
    /** A Flash Dash impulse is in progress. */
    public static final int FLAG_DASHING = 1 << 4;

    public static final StreamCodec<ByteBuf, SyncPayload> CODEC = new StreamCodec<>() {
        @Override
        public SyncPayload decode(ByteBuf b) {
            int seq = ByteBufCodecs.VAR_INT.decode(b);
            int tick = ByteBufCodecs.VAR_INT.decode(b);
            int level = b.readUnsignedByte();
            int tier = b.readUnsignedByte();
            int flags = b.readUnsignedByte();
            int speed = b.readUnsignedShort();
            int stam = b.readUnsignedShort();
            int sc = ByteBufCodecs.VAR_INT.decode(b);
            int dc = ByteBufCodecs.VAR_INT.decode(b);
            return new SyncPayload(seq, tick, level, tier, flags, speed, stam, sc, dc);
        }

        @Override
        public void encode(ByteBuf b, SyncPayload p) {
            ByteBufCodecs.VAR_INT.encode(b, p.seq);
            ByteBufCodecs.VAR_INT.encode(b, p.serverTick);
            b.writeByte(p.level).writeByte(p.tier).writeByte(p.flags);
            b.writeShort(p.speedMilli).writeShort(p.staminaPerM);
            ByteBufCodecs.VAR_INT.encode(b, p.strikeCd);
            ByteBufCodecs.VAR_INT.encode(b, p.dashCd);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
