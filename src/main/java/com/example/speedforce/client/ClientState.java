package com.example.speedforce.client;

import com.example.speedforce.net.SyncPayload;

/**
 * The local player's Speedforce state on the client. Plain data, NO client-only imports (safe to load anywhere).
 *
 * Two kinds of values live here:
 *  - AUTHORITATIVE: copied from the latest accepted SyncPayload (level, tier, flags, cooldowns, serverSpeed, stamina).
 *  - PRESENTATION: smoothed values the renderer should read (speed, tierBlend, staminaDisp). These are driven by
 *    local prediction (the real client velocity) and eased toward the server snapshot, so visuals react instantly
 *    but can never drift away from the server or snap abruptly.
 *
 * Main-thread only.
 */
public final class ClientState {
    // ---- kept from v1 so HudLayer / SpeedLinesLayer / FOV code keep working ----
    public static int level, strikeCd, dashCd, strikeMax = 40, dashMax = 30;

    // ---- authoritative (from server) ----
    /** Velocity tier 0-4 chosen by the server from real movement speed. Not the same thing as {@link #level}. */
    public static int tier;
    public static int flags;
    /** Last authoritative speed (blocks/tick) and stamina (0..1). */
    public static float serverSpeed, serverStamina = 1f;
    /** Server game time of the latest snapshot (low 32 bits) and local game ticks since it arrived. */
    public static int serverTick, ticksSinceSnapshot;

    // ---- presentation (read these for rendering) ----
    /** Smoothed horizontal speed, blocks/tick. Use {@link #speed(float)} for sub-tick interpolation. */
    public static float speed, prevSpeed;
    /** Continuous tier 0..4 that eases toward {@link #tier}; drives intensity so tier changes never pop. */
    public static float tierBlend, prevTierBlend;
    /** Smoothed stamina 0..1. */
    public static float staminaDisp = 1f, prevStaminaDisp = 1f;
    /** Client-predicted water running (hysteresis state for WaterRun.predict). */
    public static boolean waterPredicting;

    // ---- prediction internals ----
    private static float predicted;      // smoothed local velocity
    private static float correction;     // eased offset toward server value
    private static int lastSeq;
    private static boolean hasSeq;

    private static final float SNAP_SPEED = 1.0f;     // blocks/tick disagreement treated as "large discrepancy"
    private static final float SNAP_STAMINA = 0.5f;
    private static final int SNAPSHOT_FRESH_TICKS = 40;

    private ClientState() {}

    // ------------------------------------------------------------ queries
    public static boolean has(int flag) { return (flags & flag) != 0; }
    public static boolean exhausted() { return has(SyncPayload.FLAG_EXHAUSTED); }
    public static boolean negativeTheme() { return has(SyncPayload.FLAG_NEGATIVE_THEME); }
    /** Server says water running is allowed for us right now. */
    public static boolean waterAllowed() { return has(SyncPayload.FLAG_WATER_ENABLED); }

    public static float speed(float pt) { return prevSpeed + (speed - prevSpeed) * pt; }
    public static float tierBlend(float pt) { return prevTierBlend + (tierBlend - prevTierBlend) * pt; }
    public static float stamina(float pt) { return prevStaminaDisp + (staminaDisp - prevStaminaDisp) * pt; }

    // ------------------------------------------------------------ server -> client
    /** Newer-than test that survives int wrap-around. */
    private static boolean isNewer(int seq) { return !hasSeq || (seq - lastSeq) > 0; }

    public static void set(SyncPayload p) {
        if (!isNewer(p.seq())) return;           // stale or duplicate: reject
        hasSeq = true;
        lastSeq = p.seq();

        boolean levelChanged = p.level() != level;
        level = p.level();
        tier = p.tier();
        flags = p.flags();
        serverTick = p.serverTick();
        ticksSinceSnapshot = 0;

        // cooldown bars: remember the largest value seen as 100%
        if (p.strikeCd() > strikeCd) strikeMax = Math.max(1, p.strikeCd());
        if (p.dashCd() > dashCd) dashMax = Math.max(1, p.dashCd());
        strikeCd = p.strikeCd();
        dashCd = p.dashCd();

        serverSpeed = p.speedMilli() / 1000f;
        serverStamina = p.staminaPerM() / 1000f;

        // Large discrepancies (teleport, dash, level switch) snap instead of easing for many ticks.
        if (levelChanged || Math.abs(serverSpeed - speed) > SNAP_SPEED) {
            predicted = serverSpeed; correction = 0; speed = prevSpeed = serverSpeed;
        }
        if (Math.abs(serverStamina - staminaDisp) > SNAP_STAMINA) staminaDisp = prevStaminaDisp = serverStamina;
        if (level == 0) { tierBlend = prevTierBlend = 0; waterPredicting = false; }
    }

    // ------------------------------------------------------------ per client tick
    /** @param localHorizSpeed the local player's own horizontal velocity this tick (blocks/tick). */
    public static void tick(double localHorizSpeed) {
        if (strikeCd > 0) strikeCd--;
        if (dashCd > 0) dashCd--;
        if (ticksSinceSnapshot < 1_000_000) ticksSinceSnapshot++;

        prevSpeed = speed; prevTierBlend = tierBlend; prevStaminaDisp = staminaDisp;

        if (level <= 0) {
            speed *= 0.5f; if (speed < 0.001f) speed = 0;
            predicted = speed; correction = 0;
            tierBlend *= 0.7f; if (tierBlend < 0.01f) tierBlend = 0;
        } else {
            // PREDICTION: the real client velocity, lightly smoothed.
            predicted += ((float) localHorizSpeed - predicted) * 0.5f;
            // RECONCILIATION: ease an offset toward the server's value while snapshots are fresh, otherwise let it decay.
            if (ticksSinceSnapshot < SNAPSHOT_FRESH_TICKS) correction += ((serverSpeed - predicted) - correction) * 0.2f;
            else correction *= 0.9f;
            speed = Math.max(0f, predicted + correction);

            // Tier blend: eases to the server tier (up a bit faster than down, so effects ramp in quickly, fade slowly).
            float diff = tier - tierBlend;
            float rate = diff > 0 ? 0.18f : 0.08f;
            tierBlend += Math.abs(diff) < 0.02f ? diff : diff * rate;
        }
        staminaDisp += (serverStamina - staminaDisp) * 0.3f;
    }

    /** Login, logout, respawn, dimension change: forget everything and accept the next snapshot unconditionally. */
    public static void reset() {
        level = 0; strikeCd = 0; dashCd = 0; tier = 0; flags = 0;
        serverSpeed = 0; serverStamina = 1f; serverTick = 0; ticksSinceSnapshot = 0;
        speed = prevSpeed = 0; tierBlend = prevTierBlend = 0; staminaDisp = prevStaminaDisp = 1f;
        predicted = 0; correction = 0; waterPredicting = false;
        hasSeq = false; lastSeq = 0;
    }
}
