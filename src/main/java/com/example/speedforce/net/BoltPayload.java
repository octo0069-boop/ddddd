package com.example.speedforce.net;

import com.example.speedforce.Speedforce;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> clients: draw one custom lightning bolt from (x1,y1,z1) to (x2,y2,z2). style 0 = gold, 1 = red. */
public record BoltPayload(double x1, double y1, double z1, double x2, double y2, double z2,
                          int seed, int style, int life) implements CustomPacketPayload {
    public static final Type<BoltPayload> TYPE = new Type<>(Speedforce.id("bolt"));
    public static final StreamCodec<ByteBuf, BoltPayload> CODEC = new StreamCodec<>() {
        @Override
        public BoltPayload decode(ByteBuf b) {
            return new BoltPayload(b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readInt(), b.readByte(), b.readByte());
        }

        @Override
        public void encode(ByteBuf b, BoltPayload p) {
            b.writeDouble(p.x1).writeDouble(p.y1).writeDouble(p.z1)
                    .writeDouble(p.x2).writeDouble(p.y2).writeDouble(p.z2)
                    .writeInt(p.seed).writeByte(p.style).writeByte(p.life);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
