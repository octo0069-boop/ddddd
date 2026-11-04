package com.example.speedforce.net;

import com.example.speedforce.client.ClientPayloads;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration. Protocol version "2": SyncPayload changed shape, ActionPayload gained THEME and
 * VisualPayload was added, so v1 and v2 peers must not talk. NeoForge rejects the connection on mismatch,
 * which is the desired behaviour (mod is required on both sides).
 *
 * Handlers run on the main (game) thread by default in NeoForge 21.1; every handler still goes through
 * enqueueWork so that stays true if a handler thread is ever changed.
 */
public final class Network {
    public static final String PROTOCOL = "2";

    private Network() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(PROTOCOL);
        r.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, ActionPayload::handle);
        r.playToClient(SyncPayload.TYPE, SyncPayload.CODEC, ClientPayloads::onSync);
        r.playToClient(VisualPayload.TYPE, VisualPayload.CODEC, ClientPayloads::onVisual);
        r.playToClient(BoltPayload.TYPE, BoltPayload.CODEC, ClientPayloads::onBolt);
    }
}
