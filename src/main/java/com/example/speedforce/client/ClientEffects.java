package com.example.speedforce.client;

import com.example.speedforce.ModParticles;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bounded, client-only cosmetic aura and environment reactions. This class never edits blocks and never
 * searches unloaded chunks. Remote player effects are driven by the compact tracking visual snapshots.
 */
public final class ClientEffects {
    private static final Random RANDOM = new Random(0x5EED5EEDL);
    private static final int MAX_REMOTE_PLAYERS_PER_TICK = 8;
    private static int lastTick = Integer.MIN_VALUE;

    private ClientEffects() {}

    public static void tick(Minecraft minecraft) {
        Player local = minecraft.player;
        if (minecraft.level == null || local == null || local.tickCount == lastTick) return;
        lastTick = local.tickCount;
        if (!ClientConfig.ENVIRONMENT_PARTICLES.get() || ClientConfig.PARTICLE_DENSITY.get() == 0) return;

        float speed = ClientState.speed(1.0f);
        if (ClientState.level > 0) {
            aura(local, speed, ClientState.tier, ClientState.negativeTheme());
            environment(local, speed);
        }

        int visited = 0;
        for (Player other : minecraft.level.players()) {
            if (other == local || visited >= MAX_REMOTE_PLAYERS_PER_TICK) continue;
            ClientRemoteVisuals.Remote remote = ClientRemoteVisuals.get(other.getId());
            if (remote == null || remote.level <= 0) continue;
            visited++;
            aura(other, remote.speed(1.0f), remote.tier, remote.negativeTheme());
        }
    }

    private static void aura(Player player, float speed, int tier, boolean negative) {
        if (tier <= 0 || !chance(Math.min(100, 18 + tier * 13))) return;
        ParticleOptions particle = negative ? ModParticles.RED_SPARK.get() : ModParticles.GOLD_SPARK.get();
        double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 0.7;
        double y = player.getY() + 0.25 + RANDOM.nextDouble() * Math.min(1.5, 0.35 + speed * 0.25);
        double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.7;
        player.level().addParticle(particle, x, y, z,
                (RANDOM.nextDouble() - 0.5) * 0.04, RANDOM.nextDouble() * 0.05,
                (RANDOM.nextDouble() - 0.5) * 0.04);
    }

    private static void environment(Player player, float speed) {
        if (speed < 0.45f || !chance(Math.min(100, (int) (speed * 22)))) return;
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.08, player.getZ());
        BlockState state = player.level().getBlockState(below);
        double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 0.7;
        double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.7;
        if (player.level().getFluidState(below).is(FluidTags.WATER)) {
            player.level().addParticle(ParticleTypes.SPLASH, x, player.getY() + 0.03, z,
                    (RANDOM.nextDouble() - 0.5) * 0.12, 0.04, (RANDOM.nextDouble() - 0.5) * 0.12);
        } else if (state.is(BlockTags.SAND) || state.is(BlockTags.DIRT)) {
            player.level().addParticle(ParticleTypes.CLOUD, x, player.getY() + 0.02, z,
                    (RANDOM.nextDouble() - 0.5) * 0.10, 0.01, (RANDOM.nextDouble() - 0.5) * 0.10);
        } else if (state.is(BlockTags.SNOW)) {
            player.level().addParticle(ParticleTypes.SNOWFLAKE, x, player.getY() + 0.03, z,
                    (RANDOM.nextDouble() - 0.5) * 0.08, 0.02, (RANDOM.nextDouble() - 0.5) * 0.08);
        } else if (state.is(BlockTags.LEAVES)) {
            player.level().addParticle(ParticleTypes.COMPOSTER, x, player.getY() + 0.5, z,
                    (RANDOM.nextDouble() - 0.5) * 0.08, 0.03, (RANDOM.nextDouble() - 0.5) * 0.08);
        }
    }

    private static boolean chance(int percent) {
        int density = ClientConfig.PARTICLE_DENSITY.get();
        return RANDOM.nextInt(100) < percent * density / 100;
    }

    public static void reset() {
        lastTick = Integer.MIN_VALUE;
    }
}
