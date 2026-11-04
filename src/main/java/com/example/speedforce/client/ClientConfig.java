package com.example.speedforce.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HUD = B.comment("Show the Speedforce HUD").define("hud", true);
    public static final ModConfigSpec.BooleanValue SPEED_LINES = B.comment("Speed streaks on screen").define("speedLines", true);
    public static final ModConfigSpec.BooleanValue FOV_STRETCH = B.comment("Widen FOV with Speedforce level").define("fovStretch", true);

    public static final ModConfigSpec SPEC = B.build();
    private ClientConfig() {}
}
