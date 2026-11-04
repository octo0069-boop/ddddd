package com.example.speedforce;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Gameplay balance (config/speedforce-common.toml). COMMON config is NOT synced to clients, so client code
 * must never read these values - anything the client needs is carried in SyncPayload (flags, tier, stamina).
 */
public final class Config {
    /** How Flash Dash moves the player. */
    public enum DashMode {
        /** Server applies a short burst of velocity impulses; vanilla movement replication does the rest. */
        IMPULSE,
        /** Legacy behaviour: instant server teleport to the end of the path. */
        BLINK
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ---- existing options (unchanged names/defaults) ----
    public static final ModConfigSpec.IntValue MAX_LEVEL = B.comment("Highest Speedforce level (1-5)").defineInRange("maxLevel", 5, 1, 5);
    public static final ModConfigSpec.DoubleValue SPEED_SCALE = B.comment("Multiplier on every level's speed bonus").defineInRange("speedScale", 1.0, 0.1, 3.0);
    public static final ModConfigSpec.BooleanValue CONTACT_DAMAGE = B.comment("Running into mobs at speed hurts them").define("contactDamage", true);
    public static final ModConfigSpec.BooleanValue WATER_RUN = B.comment("Run across water at level 3+").define("waterRunning", true);
    public static final ModConfigSpec.DoubleValue HUNGER_DRAIN = B.comment("Hunger cost multiplier (0 = free)").defineInRange("hungerDrain", 1.0, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue STRIKE_DAMAGE = B.comment("Lightning Strike damage (half-hearts)").defineInRange("strikeDamage", 12.0, 1.0, 100.0);
    public static final ModConfigSpec.IntValue STRIKE_COOLDOWN = B.comment("Lightning Strike cooldown (ticks)").defineInRange("strikeCooldown", 40, 0, 6000);
    public static final ModConfigSpec.IntValue DASH_COOLDOWN = B.comment("Flash Dash cooldown (ticks)").defineInRange("dashCooldown", 30, 0, 6000);

    // ---- velocity tiers (blocks per tick; 1 b/t = 20 m/s) ----
    public static final ModConfigSpec.DoubleValue TIER1_SPEED = B.comment("Horizontal speed (blocks/tick) that enters tier 1 'Acceleration'").defineInRange("tier1Speed", 0.35, 0.05, 20.0);
    public static final ModConfigSpec.DoubleValue TIER2_SPEED = B.comment("Speed that enters tier 2 'Blur'").defineInRange("tier2Speed", 0.80, 0.05, 20.0);
    public static final ModConfigSpec.DoubleValue TIER3_SPEED = B.comment("Speed that enters tier 3 'Lightning Trail'").defineInRange("tier3Speed", 1.40, 0.05, 20.0);
    public static final ModConfigSpec.DoubleValue TIER4_SPEED = B.comment("Speed that enters tier 4 'Time Dilation'").defineInRange("tier4Speed", 2.40, 0.05, 20.0);
    public static final ModConfigSpec.DoubleValue TIER_HYSTERESIS = B.comment("A tier is only left once speed drops this fraction below its entry threshold").defineInRange("tierHysteresis", 0.10, 0.0, 0.5);

    // ---- kinetic stamina (independent of hunger) ----
    public static final ModConfigSpec.BooleanValue STAMINA_ENABLED = B.comment("Enable kinetic stamina").define("staminaEnabled", true);
    public static final ModConfigSpec.DoubleValue STAMINA_MAX = B.comment("Maximum kinetic stamina").defineInRange("staminaMax", 100.0, 1.0, 10000.0);
    public static final ModConfigSpec.DoubleValue STAMINA_DRAIN_SCALE = B.comment("Multiplier on stamina drain per tick (per-tier base: 0.02/0.06/0.12/0.25)").defineInRange("staminaDrainScale", 1.0, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue STAMINA_REGEN_WALK = B.comment("Stamina regained per tick while moving below tier 1 (or while exhausted)").defineInRange("staminaRegenWalk", 0.08, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue STAMINA_REGEN_IDLE = B.comment("Stamina regained per tick while standing still").defineInRange("staminaRegenIdle", 0.20, 0.0, 100.0);
    public static final ModConfigSpec.IntValue EXHAUSTED_MAX_LEVEL = B.comment("While exhausted the effective Speedforce level is capped to this (0 = no speed bonus)").defineInRange("exhaustedMaxLevel", 1, 0, 5);
    public static final ModConfigSpec.DoubleValue EXHAUSTED_RECOVER = B.comment("Fraction of max stamina needed to stop being exhausted").defineInRange("exhaustedRecover", 0.25, 0.01, 1.0);
    public static final ModConfigSpec.DoubleValue STRIKE_STAMINA = B.comment("Stamina cost of Lightning Strike").defineInRange("strikeStamina", 10.0, 0.0, 10000.0);
    public static final ModConfigSpec.DoubleValue DASH_STAMINA = B.comment("Stamina cost of Flash Dash").defineInRange("dashStamina", 15.0, 0.0, 10000.0);

    // ---- dash / networking ----
    public static final ModConfigSpec.EnumValue<DashMode> DASH_MODE = B.comment("IMPULSE = server velocity burst, BLINK = legacy teleport").defineEnum("dashMode", DashMode.IMPULSE);
    public static final ModConfigSpec.IntValue TRAIL_STEP_CAP = B.comment("Max particle-trail samples per player per tick (0 = no trail). Lower = fewer packets").defineInRange("trailStepCap", 16, 0, 40);

    public static final ModConfigSpec SPEC = B.build();
    private Config() {}
}
