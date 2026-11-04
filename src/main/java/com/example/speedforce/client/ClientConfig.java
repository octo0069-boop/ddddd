package com.example.speedforce.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only presentation preferences. These settings never affect server gameplay. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HUD = B.comment("Show the Speedforce HUD").define("hud", true);
    public static final ModConfigSpec.BooleanValue SPEED_LINES = B.comment("Show velocity-driven radial speed streaks").define("speedLines", true);
    public static final ModConfigSpec.BooleanValue FOV_STRETCH = B.comment("Widen FOV with Speedforce level").define("fovStretch", true);
    public static final ModConfigSpec.BooleanValue VIGNETTE = B.comment("Show a subtle speed vignette at high velocity").define("speedVignette", true);
    public static final ModConfigSpec.BooleanValue TUNNEL_VISION = B.comment("Narrow the visible screen edges at extreme velocity").define("tunnelVision", true);
    public static final ModConfigSpec.BooleanValue ENVIRONMENT_PARTICLES = B.comment("Show local cosmetic speed effects for ground, water, snow and leaves").define("environmentParticles", true);
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION = B.comment("Reduce camera and screen effects for accessibility").define("reducedMotion", false);
    public static final ModConfigSpec.IntValue PARTICLE_DENSITY = B.comment("Client cosmetic particle density (0-100 percent)").defineInRange("particleDensity", 65, 0, 100);

    public static final ModConfigSpec SPEC = B.build();
    private ClientConfig() {}
}
