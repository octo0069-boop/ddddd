package com.example.speedforce.net;

import com.example.speedforce.Speedforce;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> clients TRACKING a player: the minimum other clients need to render that player's
 * aura / trails / afterimages later. 8-10 bytes. Never sent to the whole server, and never to the owner
 * (the owner has SyncPayload). Flags reuse SyncPayload.FLAG_*.
 *
 * @param entityId   network id of the player entity (resolved client-side at render time, so no lookup here)
 * @param seq        per-player sequence shared with SyncPayload; stale packets are dropped per entity
 * @param level      selected level 0-5 (0 = Speedforce off, client removes the entry)
 * @param tier       velocity tier 0-4
 * @param flags      SyncPayload.FLAG_*
 * @param speedMilli smoothed horizontal speed, blocks/tick x 1000
 */
public record VisualPayload(int entityId, int seq, int level, int tier, int flags, int speedMilli) implements CustomPacketPayload {
    public static final Type<VisualPayload> TYPE = new Type<>(Speedforce.id("visual"));

    public static final StreamCodec<ByteBuf, VisualPayload> CODEC = new StreamCodec<>() {
        @Override
        public VisualPayload decode(ByteBuf b) {
            int id = ByteBufCodecs.VAR_INT.decode(b);
            int seq = ByteBufCodecs.VAR_INT.decode(b);
            int level = b.readUnsignedByte();
            int tier = b.readUnsignedByte();
            int flags = b.readUnsignedByte();
            int speed = b.readUnsignedShort();
            return new VisualPayload(id, seq, level, tier, flags, speed);
        }

        @Override
        public void encode(ByteBuf b, VisualPayload p) {
            ByteBufCodecs.VAR_INT.encode(b, p.entityId);
            ByteBufCodecs.VAR_INT.encode(b, p.seq);
            b.writeByte(p.level).writeByte(p.tier).writeByte(p.flags).writeShort(p.speedMilli);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
