package com.example.speedforce.server;

import com.example.speedforce.Config;
import com.example.speedforce.ModParticles;
import com.example.speedforce.Speedforce;
import com.example.speedforce.net.ActionPayload;
import com.example.speedforce.net.BoltPayload;
import com.example.speedforce.net.SyncPayload;
import com.example.speedforce.net.VisualPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * All authoritative Speedforce state and abilities. Server-thread only (called from game-bus events and from
 * ActionPayload.handle via enqueueWork). Never touches client classes.
 *
 * Per player we keep four DIFFERENT things (they used to be one variable):
 *   level  - what the player selected (0-5)
 *   tier   - visual/movement tier 0-4 derived from ACTUAL sampled speed, with hysteresis + dwell time
 *   speed  - smoothed horizontal speed, sampled from real position deltas
 *   stamina- kinetic stamina, independent of hunger, server-owned
 */
public final class ServerLogic {
    private static final ResourceLocation SPEED_ID = Speedforce.id("speed");
    private static final ResourceLocation STEP_ID = Speedforce.id("step");
    private static final ResourceLocation FALL_ID = Speedforce.id("fall");
    /** Extra movement speed as a multiplier per level (x Config.SPEED_SCALE). Unchanged from v1. */
    private static final double[] BONUS = {0, 1.0, 2.5, 5.0, 9.0, 15.0};
    /** Stamina drained per tick at each tier (x Config.STAMINA_DRAIN_SCALE). Tier 0 never drains. */
    private static final float[] DRAIN = {0f, 0.02f, 0.06f, 0.12f, 0.25f};

    /** A position change bigger than this in one tick (blocks) is a teleport, not speed. */
    private static final double TELEPORT_SQ = 10.0 * 10.0;
    private static final int TIER_UP_DWELL = 2, TIER_DOWN_DWELL = 8;   // ticks a new tier must be wanted before switching
    private static final int DASH_TICKS = 5;

    // ---- packet budgets ----
    private static final int SYNC_MIN_INTERVAL = 5;       // ticks between value-only (non-structural) snapshots
    private static final int HEARTBEAT = 100;             // ticks between keep-alive snapshots while active
    private static final int SPEED_DELTA_SELF = 150;      // milli-blocks/tick change worth a self snapshot
    private static final int SPEED_DELTA_VISUAL = 200;    // ... and a tracker snapshot
    private static final int STAMINA_DELTA = 10;          // permille (1%)
    private static final int VISUAL_MIN_INTERVAL = 4;
    // ---- client request rate limit (token bucket, refilled lazily when a packet arrives) ----
    private static final float ACTION_BURST = 8f, ACTION_REFILL = 0.25f;   // 1 action per 4 ticks sustained, bursts of 8

    private static final class Data {
        // gameplay
        int level;
        boolean negative;                       // theme: false = gold/positive, true = red/negative
        long strikeReady, dashReady;            // game-time deadlines (unchanged semantics)
        // movement sampling (primitives: no Vec3 per tick)
        boolean prevValid;
        double px, py, pz;
        int skipSamples;                        // ticks to ignore after an intentional teleport
        float smoothSpeed;                      // EMA of horizontal speed, blocks/tick
        // tier
        int tier, tierCand, tierDwell;
        // stamina
        float stamina;
        boolean exhausted;
        // water + dash
        boolean waterActive;
        int dashTicks;
        double dashVx, dashVy, dashVz;
        // attribute cache: never re-add modifiers unless something changed
        int aEff = -1, aFall = -1;
        double aScale = Double.NaN;
        // networking
        int seq;
        int sLevel = -1, sTier = -1, sFlags = -1, sSpeed = -1, sStam = -1;
        int sTick = -100000;
        int vLevel = -1, vTier = -1, vFlags = -1, vSpeed = -1;
        int vTick = -100000;
        float tokens = ACTION_BURST;
        int tokenTick;

        Data() { stamina = Config.STAMINA_MAX.get().floatValue(); }
    }

    private static final Map<UUID, Data> DATA = new HashMap<>();

    private ServerLogic() {}

    private static Data data(ServerPlayer p) { return DATA.computeIfAbsent(p.getUUID(), k -> new Data()); }

    public static int level(ServerPlayer p) {
        Data d = DATA.get(p.getUUID());
        return d == null ? 0 : d.level;
    }

    // ------------------------------------------------------------ lifecycle
    /** Login / respawn: back to a clean inactive state and push a full snapshot. */
    public static void reset(ServerPlayer p) {
        Data d = data(p);
        d.level = 0; d.strikeReady = 0; d.dashReady = 0;
        d.prevValid = false; d.skipSamples = 0; d.smoothSpeed = 0;
        d.tier = 0; d.tierCand = 0; d.tierDwell = 0;
        d.stamina = Config.STAMINA_MAX.get().floatValue(); d.exhausted = false;
        d.waterActive = false; d.dashTicks = 0;
        d.aEff = -1; d.aFall = -1; d.aScale = Double.NaN;     // transient modifiers did not survive the new entity
        applyAttributes(p, d);
        flush(p, d, true);
    }

    /** Dimension change: same entity, same state, but the client wiped its prediction, so resend everything. */
    public static void onDimensionChange(ServerPlayer p) {
        Data d = DATA.get(p.getUUID());
        if (d == null) return;
        d.prevValid = false;           // first sample in the new dimension is a jump, not speed
        d.skipSamples = 2;
        d.waterActive = false;
        d.dashTicks = 0;
        flush(p, d, true);
    }

    /** A client started tracking this player: give it the current visual state (otherwise it waits for a change). */
    public static void onStartTracking(ServerPlayer viewer, ServerPlayer target) {
        Data d = DATA.get(target.getUUID());
        if (d == null || d.level == 0) return;
        PacketDistributor.sendToPlayer(viewer, new VisualPayload(target.getId(), ++d.seq, d.level, d.tier,
                flags(d), speedMilli(d)));
    }

    public static void forget(ServerPlayer p) { DATA.remove(p.getUUID()); }

    /** Server stopped (also integrated server leaving a world): nothing may leak into the next one. */
    public static void clearAll() { DATA.clear(); }

    // ------------------------------------------------------------ input (validated)
    public static void onAction(ServerPlayer p, int action) {
        if (!ActionPayload.isValid(action)) return;
        if (!p.isAlive() || p.isSpectator()) return;
        Data d = data(p);
        if (!allowAction(p, d)) return;                         // rate limit
        switch (action) {
            case ActionPayload.UP -> setLevel(p, d.level + 1);
            case ActionPayload.DOWN -> setLevel(p, d.level - 1);
            case ActionPayload.OFF -> setLevel(p, 0);
            case ActionPayload.STRIKE -> strike(p);
            case ActionPayload.DASH -> dash(p);
            case ActionPayload.THEME -> { d.negative = !d.negative; flush(p, d, true); }
            default -> { }
        }
    }

    private static boolean allowAction(ServerPlayer p, Data d) {
        int now = p.server.getTickCount();
        if (now < d.tokenTick) d.tokenTick = now;
        d.tokens = Math.min(ACTION_BURST, d.tokens + (now - d.tokenTick) * ACTION_REFILL);
        d.tokenTick = now;
        if (d.tokens < 1f) return false;
        d.tokens -= 1f;
        return true;
    }

    public static void setLevel(ServerPlayer p, int wanted) {
        Data d = data(p);
        int nl = Mth.clamp(wanted, 0, Config.MAX_LEVEL.get());
        if (nl > 0 && p.getFoodData().getFoodLevel() <= 3) {
            p.displayClientMessage(Component.translatable("message.speedforce.hungry"), true);
            return;
        }
        int old = d.level;
        d.level = nl;
        if (nl == 0) deactivate(d);
        applyAttributes(p, d);
        flush(p, d, true);
        if (nl == old) return;

        p.displayClientMessage(Component.translatable("message.speedforce.level", nl), true);
        ServerLevel lv = p.serverLevel();
        if (nl > old) {
            lv.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6f, 1.2f + nl * 0.12f);
            for (int i = 0; i < 3 + nl; i++) aura(lv, p, 1.4, 0);
            if (nl == Config.MAX_LEVEL.get() && nl >= 5) {
                lv.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 1.6f);
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI / 5;
                    Vec3 c = p.position().add(0, 0.9, 0);
                    sendBolt(lv, c, c.add(Math.cos(a) * 5, (lv.random.nextDouble() - 0.5) * 2, Math.sin(a) * 5), i % 3 == 0 ? 1 : 0, 8);
                }
            }
        }
    }

    /** Clears everything that only exists while Speedforce is on. Stamina and exhaustion are deliberately kept. */
    private static void deactivate(Data d) {
        d.tier = 0; d.tierCand = 0; d.tierDwell = 0;
        d.smoothSpeed = 0; d.waterActive = false; d.dashTicks = 0; d.prevValid = false;
    }

    // ------------------------------------------------------------ derived state
    /** Selected level, capped while exhausted. This is what actually drives speed, damage and hunger. */
    private static int eff(Data d) {
        return d.exhausted ? Math.min(d.level, Config.EXHAUSTED_MAX_LEVEL.get()) : d.level;
    }

    private static boolean waterEnabled(Data d) { return Config.WATER_RUN.get() && eff(d) >= 3; }

    private static int flags(Data d) {
        int f = 0;
        if (d.exhausted) f |= SyncPayload.FLAG_EXHAUSTED;
        if (d.negative) f |= SyncPayload.FLAG_NEGATIVE_THEME;
        if (d.level > 0) {
            if (waterEnabled(d)) f |= SyncPayload.FLAG_WATER_ENABLED;
            if (d.waterActive) f |= SyncPayload.FLAG_WATER_ACTIVE;
            if (d.dashTicks > 0) f |= SyncPayload.FLAG_DASHING;
        }
        return f;
    }

    private static int speedMilli(Data d) { return (int) Mth.clamp(d.smoothSpeed * 1000f, 0f, 65535f); }

    private static int staminaPerM(Data d) {
        float max = Config.STAMINA_MAX.get().floatValue();
        return (int) Mth.clamp(d.stamina / max * 1000f, 0f, 1000f);
    }

    /** Tier entry thresholds, forced strictly increasing so a bad config cannot invert the tiers. */
    private static double enter(int tier) {
        double t1 = Config.TIER1_SPEED.get();
        if (tier == 1) return t1;
        double t2 = Math.max(Config.TIER2_SPEED.get(), t1 + 0.01);
        if (tier == 2) return t2;
        double t3 = Math.max(Config.TIER3_SPEED.get(), t2 + 0.01);
        if (tier == 3) return t3;
        return Math.max(Config.TIER4_SPEED.get(), t3 + 0.01);
    }

    /** One tier step at a time, with a hysteresis band and a dwell counter so a threshold can't flicker. */
    private static void updateTier(Data d) {
        int cur = d.tier, cand = cur;
        float s = d.smoothSpeed;
        if (cur < 4 && s >= enter(cur + 1)) cand = cur + 1;
        else if (cur > 0 && s < enter(cur) * (1.0 - Config.TIER_HYSTERESIS.get())) cand = cur - 1;

        if (cand == cur) { d.tierDwell = 0; d.tierCand = cur; return; }
        if (cand != d.tierCand) { d.tierCand = cand; d.tierDwell = 0; }
        if (++d.tierDwell >= (cand > cur ? TIER_UP_DWELL : TIER_DOWN_DWELL)) {
            d.tier = cand; d.tierDwell = 0;
        }
    }

    // ------------------------------------------------------------ attributes (applied only when they change)
    private static void applyAttributes(ServerPlayer p, Data d) {
        int eff = eff(d);
        double scale = Config.SPEED_SCALE.get();
        if (eff != d.aEff || scale != d.aScale) {
            mod(p, Attributes.MOVEMENT_SPEED, SPEED_ID, eff > 0 ? BONUS[eff] * scale : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            mod(p, Attributes.STEP_HEIGHT, STEP_ID, eff >= 2 ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);
            d.aEff = eff; d.aScale = scale;
        }
        if (d.level != d.aFall) {   // fall protection follows the SELECTED level, so exhaustion never drops you out of the sky
            mod(p, Attributes.SAFE_FALL_DISTANCE, FALL_ID, d.level > 0 ? 6.0 * d.level : 0, AttributeModifier.Operation.ADD_VALUE);
            d.aFall = d.level;
        }
    }

    private static void mod(ServerPlayer p, Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    // ------------------------------------------------------------ synchronisation
    /**
     * Sends what changed. Owner gets a SyncPayload, players TRACKING the owner get a VisualPayload.
     * Structural changes (level / tier / flags) go out immediately; value drift is rate-limited with a delta
     * threshold; a slow heartbeat keeps cooldowns honest. Nothing is sent when nothing changed.
     */
    private static void flush(ServerPlayer p, Data d, boolean force) {
        int now = p.server.getTickCount();
        int flags = flags(d);
        int speed = speedMilli(d);
        int stam = staminaPerM(d);

        // ---- owner ----
        boolean due = force || d.level != d.sLevel || d.tier != d.sTier || flags != d.sFlags;
        if (!due) {
            int age = now - d.sTick;
            due = (age >= SYNC_MIN_INTERVAL && (Math.abs(speed - d.sSpeed) >= SPEED_DELTA_SELF || Math.abs(stam - d.sStam) >= STAMINA_DELTA))
                    || (d.level > 0 && age >= HEARTBEAT);
        }
        if (due) {
            long gt = p.serverLevel().getGameTime();
            PacketDistributor.sendToPlayer(p, new SyncPayload(++d.seq, (int) gt, d.level, d.tier, flags, speed, stam,
                    (int) Math.max(0, d.strikeReady - gt), (int) Math.max(0, d.dashReady - gt)));
            d.sLevel = d.level; d.sTier = d.tier; d.sFlags = flags; d.sSpeed = speed; d.sStam = stam; d.sTick = now;
        }

        // ---- everyone tracking this player (not the owner) ----
        boolean vdue = d.level != d.vLevel || d.tier != d.vTier || flags != d.vFlags;
        if (!vdue && d.level > 0) {
            int age = now - d.vTick;
            vdue = (age >= VISUAL_MIN_INTERVAL && Math.abs(speed - d.vSpeed) >= SPEED_DELTA_VISUAL) || age >= HEARTBEAT;
        }
        if (vdue) {
            PacketDistributor.sendToPlayersTrackingEntity(p, new VisualPayload(p.getId(), ++d.seq, d.level, d.tier, flags, speed));
            d.vLevel = d.level; d.vTier = d.tier; d.vFlags = flags; d.vSpeed = speed; d.vTick = now;
        }
    }

    // ------------------------------------------------------------ per-tick
    public static void tick(ServerPlayer p) {
        Data d = DATA.get(p.getUUID());
        if (d == null) return;

        // ---------- inactive: only stamina recovery (cheap) ----------
        if (d.level == 0) {
            boolean changed = false;
            if (Config.STAMINA_ENABLED.get()) {
                float max = Config.STAMINA_MAX.get().floatValue();
                if (d.stamina < max || d.exhausted) {
                    d.stamina = Math.min(max, d.stamina + Config.STAMINA_REGEN_IDLE.get().floatValue());
                    if (d.exhausted && d.stamina >= max * Config.EXHAUSTED_RECOVER.get()) d.exhausted = false;
                    changed = true;
                }
            } else if (d.exhausted) { d.exhausted = false; changed = true; }
            if (changed) { applyAttributes(p, d); flush(p, d, false); }
            return;
        }

        ServerLevel lv = p.serverLevel();
        if (d.level > Config.MAX_LEVEL.get()) { setLevel(p, Config.MAX_LEVEL.get()); return; }   // config lowered at runtime
        if (p.getFoodData().getFoodLevel() <= 3) {
            p.displayClientMessage(Component.translatable("message.speedforce.hungry"), true);
            setLevel(p, 0);
            return;
        }
        if (p.isSpectator()) { setLevel(p, 0); return; }

        // ---------- 1. dash impulse: re-applied for a few ticks, vanilla replication carries it to the client ----------
        if (d.dashTicks > 0) {
            d.dashTicks--;
            p.setDeltaMovement(d.dashVx, d.dashVy, d.dashVz);
            p.hurtMarked = true;          // makes the server send the motion packet to the player at end of tick
            p.fallDistance = 0;
        }

        // ---------- 2. sample REAL movement from position deltas ----------
        // (A ServerPlayer's deltaMovement is not reliable: movement is client-reported. Position deltas are.)
        double x = p.getX(), y = p.getY(), z = p.getZ();
        boolean valid = false;
        double dx = 0, dy = 0, dz = 0, hs = 0;
        boolean riding = p.isPassenger();
        if (d.prevValid && d.skipSamples == 0 && !riding) {
            dx = x - d.px; dy = y - d.py; dz = z - d.pz;
            if (dx * dx + dy * dy + dz * dz <= TELEPORT_SQ) {   // bigger jumps are teleports: ignore, don't call it speed
                valid = true;
                hs = Math.sqrt(dx * dx + dz * dz);
            }
        }
        if (d.skipSamples > 0) d.skipSamples--;
        double ox = d.px, oy = d.py, oz = d.pz;
        d.px = x; d.py = y; d.pz = z; d.prevValid = !riding;
        if (valid) d.smoothSpeed += (float) (hs - d.smoothSpeed) * 0.35f;
        else if (riding) d.smoothSpeed *= 0.8f;                 // else hold: momentum is preserved across a skipped sample

        // ---------- 3. tier ----------
        updateTier(d);

        // ---------- 4. kinetic stamina ----------
        boolean wasExhausted = d.exhausted;
        if (Config.STAMINA_ENABLED.get()) {
            float max = Config.STAMINA_MAX.get().floatValue();
            if (d.exhausted) {
                d.stamina += Config.STAMINA_REGEN_WALK.get().floatValue();       // no drain while recovering
            } else if (d.tier >= 1) {
                d.stamina -= DRAIN[d.tier] * Config.STAMINA_DRAIN_SCALE.get().floatValue();
            } else if (d.smoothSpeed < 0.03f) {
                d.stamina += Config.STAMINA_REGEN_IDLE.get().floatValue();
            } else {
                d.stamina += Config.STAMINA_REGEN_WALK.get().floatValue();
            }
            d.stamina = Mth.clamp(d.stamina, 0f, max);
            if (!d.exhausted && d.stamina <= 0f) d.exhausted = true;
            else if (d.exhausted && d.stamina >= max * Config.EXHAUSTED_RECOVER.get()) d.exhausted = false;
        } else {
            d.stamina = Config.STAMINA_MAX.get().floatValue();
            d.exhausted = false;
        }
        if (d.exhausted != wasExhausted) {
            p.displayClientMessage(Component.translatable(d.exhausted ? "message.speedforce.exhausted" : "message.speedforce.recovered"), true);
            applyAttributes(p, d);
        }
        int level = eff(d);

        // ---------- 5. water running (server only EVALUATES; the client predicts; nobody teleports anyone) ----------
        d.waterActive = WaterRun.serverActive(p, waterEnabled(d), d.smoothSpeed, d.waterActive);
        if (d.waterActive) {
            p.fallDistance = 0;
            p.connection.resetFlyingTicks();   // vanilla would kick for "flying" after ~4s hovering over a non-solid surface
        }

        // ---------- 6. effects (unchanged behaviour; trail capped to limit packets) ----------
        int cap = Config.TRAIL_STEP_CAP.get();
        if (valid && cap > 0) {
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 0.3) {
                int steps = (int) Math.min(cap, dist * 2) + 1;
                for (int i = 0; i <= steps; i++) {
                    double t = i / (double) steps;
                    double qx = ox + dx * t, qy = oy + dy * t, qz = oz + dz * t;
                    lv.sendParticles(lv.random.nextInt(4) == 0 ? ModParticles.RED_SPARK.get() : ModParticles.GOLD_SPARK.get(),
                            qx, qy + 0.9, qz, 1, 0.25, 0.55, 0.25, 0.02);
                    if (lv.random.nextInt(2) == 0) lv.sendParticles(ModParticles.GLOW.get(), qx, qy + 0.9, qz, 1, 0.15, 0.5, 0.15, 0);
                }
            }
        }

        if (p.tickCount % 2 == 0 && level >= 2) {
            int arcs = level >= 4 ? 3 : 2;
            for (int i = 0; i < arcs; i++) aura(lv, p, 2.2, lv.random.nextInt(5) == 0 ? 1 : 0);
        }
        if (level >= 4 && valid && hs > 0.8 && p.tickCount % 3 == 0) {
            double hl = Math.sqrt(dx * dx + dz * dz);
            Vec3 back = new Vec3(-dx / hl, 0, -dz / hl);
            Vec3 s = new Vec3(x, y + 0.3 + lv.random.nextDouble() * 1.4, z);
            Vec3 e = s.add(back.scale(5 + lv.random.nextDouble() * 6)).add(lv.random.nextDouble() - 0.5, lv.random.nextDouble() - 0.5, lv.random.nextDouble() - 0.5);
            sendBolt(lv, s, e, lv.random.nextInt(3) == 0 ? 1 : 0, 5);
        }

        // ---------- 7. hunger (kept; independent of stamina) ----------
        if (hs > 0.2 && level > 0) p.causeFoodExhaustion((float) (0.004 * level * Config.HUNGER_DRAIN.get()));

        // ---------- 8. contact damage (kept) ----------
        if (Config.CONTACT_DAMAGE.get() && level >= 2 && hs > 0.6) {
            AABB box = p.getBoundingBox().inflate(0.8, 0.2, 0.8);
            float dmg = (float) Math.min(20.0, hs * 6.0);
            for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, box, t -> canHit(p, t))) {
                if (e.invulnerableTime > 10) continue;
                if (e.hurt(p.damageSources().playerAttack(p), dmg)) {
                    e.knockback(Math.min(2.5, hs * 0.8), Mth.sin(p.getYRot() * ((float) Math.PI / 180f)), -Mth.cos(p.getYRot() * ((float) Math.PI / 180f)));
                    sendBolt(lv, p.position().add(0, 1, 0), e.position().add(0, e.getBbHeight() / 2, 0), 0, 5);
                }
            }
        }

        flush(p, d, false);
    }

    // ------------------------------------------------------------ abilities
    private static boolean requireActive(ServerPlayer p) {
        if (level(p) > 0) return true;
        p.displayClientMessage(Component.translatable("message.speedforce.inactive"), true);
        return false;
    }

    /** Spends stamina for an ability. The client never tells us a cost or a balance. */
    private static boolean spend(ServerPlayer p, Data d, double cost) {
        if (!Config.STAMINA_ENABLED.get() || cost <= 0) return true;
        if (d.exhausted || d.stamina < cost) {
            p.displayClientMessage(Component.translatable("message.speedforce.low_stamina"), true);
            return false;
        }
        d.stamina -= (float) cost;
        return true;
    }

    /** Lightning Strike: sky bolt at what you look at + chain lightning to nearby enemies. */
    private static void strike(ServerPlayer p) {
        if (!requireActive(p)) return;
        Data d = data(p);
        ServerLevel lv = p.serverLevel();
        long now = lv.getGameTime();
        if (now < d.strikeReady) return;
        if (!spend(p, d, Config.STRIKE_STAMINA.get())) return;
        d.strikeReady = now + Config.STRIKE_COOLDOWN.get();

        Vec3 target = rayTarget(p, 60);
        Vec3 sky = target.add((lv.random.nextDouble() - 0.5) * 6, 42, (lv.random.nextDouble() - 0.5) * 6);
        sendBolt(lv, sky, target, 0, 10);
        sendBolt(lv, sky.add(2, 0, 1), target, 1, 8);
        lv.playSound(null, target.x, target.y, target.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 1.5f);
        lv.playSound(null, target.x, target.y, target.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0f, 1.3f);

        float dmg = Config.STRIKE_DAMAGE.get().floatValue();
        List<LivingEntity> hit = lv.getEntitiesOfClass(LivingEntity.class, new AABB(target, target).inflate(3.5), e -> canHit(p, e));
        for (LivingEntity e : hit) {
            e.hurt(lv.damageSources().indirectMagic(p, p), dmg);
            e.igniteForSeconds(3);
        }
        // chain lightning: up to 4 more targets within 10 blocks
        List<LivingEntity> near = new ArrayList<>(lv.getEntitiesOfClass(LivingEntity.class, new AABB(target, target).inflate(10), e -> canHit(p, e) && !hit.contains(e)));
        near.sort((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)));
        Vec3 from = target.add(0, 1, 0);
        for (int i = 0; i < Math.min(4, near.size()); i++) {
            LivingEntity e = near.get(i);
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0);
            sendBolt(lv, from, to, 0, 7);
            e.hurt(lv.damageSources().indirectMagic(p, p), dmg * 0.5f);
            from = to;
        }
        flush(p, d, true);
    }

    /**
     * Flash Dash. The server validates, plans the path (block clip, entity shock) and then either
     *  IMPULSE: sets velocity for DASH_TICKS ticks and lets vanilla movement replication move the player, or
     *  BLINK:   legacy server teleport.
     */
    private static void dash(ServerPlayer p) {
        if (!requireActive(p)) return;
        if (p.isPassenger() || p.isFallFlying() || p.isSleeping()) return;   // invalid states for a dash
        Data d = data(p);
        ServerLevel lv = p.serverLevel();
        long now = lv.getGameTime();
        if (now < d.dashReady) return;
        if (!spend(p, d, Config.DASH_STAMINA.get())) return;
        d.dashReady = now + Config.DASH_COOLDOWN.get();

        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, look.y * 0.3, look.z).normalize();
        double range = 10 + 3 * eff(d);
        Vec3 start = p.position().add(0, 0.9, 0);
        Vec3 end = start.add(dir.scale(range));
        BlockHitResult hr = lv.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hr.getType() != HitResult.Type.MISS) end = hr.getLocation().subtract(dir.scale(0.8));

        // shock everything along the path
        AABB box = new AABB(start, end).inflate(1.5);
        for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, box, t -> canHit(p, t))) {
            if (distToSegment(e.position().add(0, e.getBbHeight() / 2, 0), start, end) <= 1.6) {
                e.hurt(lv.damageSources().indirectMagic(p, p), 6.0f);
                sendBolt(lv, start.lerp(end, 0.5), e.position().add(0, e.getBbHeight() / 2, 0), 0, 6);
            }
        }
        sendBolt(lv, start, end, 0, 9);
        sendBolt(lv, start.add(0, 0.3, 0), end.add(0, 0.3, 0), 1, 7);
        lv.playSound(null, start.x, start.y, start.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 2.0f);

        if (Config.DASH_MODE.get() == Config.DashMode.BLINK) {
            p.connection.teleport(end.x, end.y - 0.9, end.z, p.getYRot(), p.getXRot());
            p.setDeltaMovement(dir.scale(0.8));
            p.hurtMarked = true;
            p.fallDistance = 0;
            d.skipSamples = 2;           // the jump is not "speed"
            d.prevValid = false;
        } else {
            double v = start.distanceTo(end) / DASH_TICKS;     // blocks per tick, stays far below vanilla's 10 b/t move check
            d.dashVx = dir.x * v; d.dashVy = dir.y * v; d.dashVz = dir.z * v;
            d.dashTicks = DASH_TICKS - 1;                      // this immediate application is the first of DASH_TICKS
            p.setDeltaMovement(d.dashVx, d.dashVy, d.dashVz);
            p.hurtMarked = true;
            p.fallDistance = 0;
        }
        flush(p, d, true);
    }

    // ------------------------------------------------------------ helpers
    private static Vec3 rayTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult br = p.serverLevel().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 limit = br.getType() == HitResult.Type.MISS ? end : br.getLocation();
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult er = ProjectileUtil.getEntityHitResult(p, eye, limit, box,
                e -> !e.isSpectator() && e.isPickable() && e != p, limit.distanceToSqr(eye));
        return er != null ? er.getLocation() : limit;
    }

    private static boolean canHit(ServerPlayer p, Entity e) {
        if (e == p || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof TamableAnimal t && t.isOwnedBy(p)) return false;
        if (e instanceof Player other && !p.canHarmPlayer(other)) return false;
        return e instanceof LivingEntity;
    }

    private static double distToSegment(Vec3 pt, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1e-6) return pt.distanceTo(a);
        double t = Mth.clamp(pt.subtract(a).dot(ab) / len2, 0, 1);
        return pt.distanceTo(a.add(ab.scale(t)));
    }

    /** A short random crackle on the player's body. */
    private static void aura(ServerLevel lv, ServerPlayer p, double size, int style) {
        Vec3 c = p.position();
        Vec3 a = c.add((lv.random.nextDouble() - 0.5) * 0.8, lv.random.nextDouble() * 1.8, (lv.random.nextDouble() - 0.5) * 0.8);
        Vec3 dir = new Vec3(lv.random.nextDouble() - 0.5, lv.random.nextDouble() - 0.5, lv.random.nextDouble() - 0.5).normalize();
        sendBolt(lv, a, a.add(dir.scale(0.8 + lv.random.nextDouble() * size)), style, 5);
    }

    private static void sendBolt(ServerLevel lv, Vec3 a, Vec3 b, int style, int life) {
        BoltPayload payload = new BoltPayload(a.x, a.y, a.z, b.x, b.y, b.z, lv.random.nextInt(), style, life);
        PacketDistributor.sendToPlayersNear(lv, null, b.x, b.y, b.z, 128, payload);
    }
}
