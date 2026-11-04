package com.example.speedforce.client;

import com.example.speedforce.net.BoltPayload;
import com.example.speedforce.net.SyncPayload;
import com.example.speedforce.net.VisualPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handlers for server->client packets. Intentionally free of net.minecraft.client imports (dedicated-server safe):
 * Network.register references these method handles on both sides, so this class and everything it touches
 * (ClientState, ClientRemoteVisuals, ClientBolts) must stay free of client-only classes.
 * All state mutation goes through enqueueWork so it runs on the client game thread.
 */
public final class ClientPayloads {
    private ClientPayloads() {}

    public static void onSync(SyncPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientState.set(p));
    }

    public static void onVisual(VisualPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientRemoteVisuals.apply(p));
    }

    public static void onBolt(BoltPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientBolts.add(p.x1(), p.y1(), p.z1(), p.x2(), p.y2(), p.z2(), p.seed(), p.style(), p.life()));
    }
}
