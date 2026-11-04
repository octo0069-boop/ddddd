package com.example.speedforce.client;

import com.example.speedforce.net.SyncPayload;
import com.example.speedforce.net.VisualPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Speedforce visual state of OTHER players, keyed by entity network id. Filled by VisualPayload (tracking-player
 * distribution only). Future aura / trail / afterimage renderers read {@link #get(int)} for the entity they draw.
 *
 * Bounded (MAX_ENTRIES), cleaned when entities leave the client level, when Speedforce turns off, and on reset.
 * No client-only imports. Main-thread only.
 */
public final class ClientRemoteVisuals {
    public static final int MAX_ENTRIES = 256;

    public static final class Remote {
        public int seq, level, tier, flags;
        public float targetSpeed;
        public float speed, prevSpeed;
        public float tierBlend, prevTierBlend;

        public float speed(float pt) { return prevSpeed + (speed - prevSpeed) * pt; }
        public float tierBlend(float pt) { return prevTierBlend + (tierBlend - prevTierBlend) * pt; }
        public boolean negativeTheme() { return (flags & SyncPayload.FLAG_NEGATIVE_THEME) != 0; }
        public boolean has(int flag) { return (flags & flag) != 0; }
    }

    private static final Int2ObjectOpenHashMap<Remote> MAP = new Int2ObjectOpenHashMap<>();

    private ClientRemoteVisuals() {}

    /** @return the state to render for this entity, or null if it is not using Speedforce. */
    public static Remote get(int entityId) { return MAP.get(entityId); }

    public static boolean isEmpty() { return MAP.isEmpty(); }

    public static void apply(VisualPayload p) {
        Remote r = MAP.get(p.entityId());
        if (r != null && (p.seq() - r.seq) <= 0) return;      // stale per entity
        if (p.level() <= 0) {                                   // Speedforce turned off: drop the entry
            MAP.remove(p.entityId());
            return;
        }
        if (r == null) {
            if (MAP.size() >= MAX_ENTRIES) return;              // hard cap, never grows unbounded
            r = new Remote();
            r.speed = r.prevSpeed = p.speedMilli() / 1000f;
            r.tierBlend = r.prevTierBlend = p.tier();
            MAP.put(p.entityId(), r);
        }
        r.seq = p.seq();
        r.level = p.level();
        r.tier = p.tier();
        r.flags = p.flags();
        r.targetSpeed = p.speedMilli() / 1000f;
    }

    /** Called once per client tick: ease every remote toward its latest authoritative snapshot. */
    public static void tick() {
        if (MAP.isEmpty()) return;
        for (Remote r : MAP.values()) {
            r.prevSpeed = r.speed; r.prevTierBlend = r.tierBlend;
            float ds = r.targetSpeed - r.speed;
            r.speed += Math.abs(ds) > SNAP ? ds : ds * 0.3f;
            float dt = r.tier - r.tierBlend;
            r.tierBlend += Math.abs(dt) < 0.02f ? dt : dt * (dt > 0 ? 0.18f : 0.08f);
        }
    }

    private static final float SNAP = 1.0f;

    public static void remove(int entityId) { MAP.remove(entityId); }

    public static void clear() { MAP.clear(); }
}
