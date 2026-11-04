package com.example.speedforce;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Our own particle types (registered on both sides; textures/behaviour are client-only). */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Speedforce.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GOLD_SPARK =
            PARTICLES.register("gold_spark", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RED_SPARK =
            PARTICLES.register("red_spark", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GLOW =
            PARTICLES.register("glow", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
