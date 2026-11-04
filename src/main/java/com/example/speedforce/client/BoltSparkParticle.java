package com.example.speedforce.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/**
 * Speedforce particle. Two behaviours:
 *  - spark: a lightning fragment that swaps between 4 zigzag frames every 2 ticks (so it crackles),
 *    twists randomly, shrinks and fades;
 *  - glow: a soft orb that swells and fades.
 * Both are fullbright so they stay vivid in caves and at night.
 */
public class BoltSparkParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final boolean glow;

    protected BoltSparkParticle(ClientLevel level, double x, double y, double z,
                                double xd, double yd, double zd, SpriteSet sprites, boolean glow) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        this.glow = glow;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.friction = 0.82f;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.lifetime = glow ? 8 + random.nextInt(5) : 5 + random.nextInt(5);
        this.quadSize = glow ? 0.35f + random.nextFloat() * 0.35f : 0.28f + random.nextFloat() * 0.30f;
        this.roll = this.oRoll = glow ? 0f : random.nextFloat() * Mth.TWO_PI;
        this.setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        if (!glow && age % 2 == 0) {
            setSprite(sprites.get(random));            // new zigzag shape = crackle
            roll += (random.nextFloat() - 0.5f) * 1.6f; // and a random twist
        }
        alpha = 1f - age / (float) lifetime;
        move(xd, yd, zd);
        xd *= friction;
        yd *= friction;
        zd *= friction;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = (age + partialTick) / lifetime;
        return glow ? quadSize * (1f + t * 0.8f) : quadSize * Mth.clamp(1f - t * 0.5f, 0f, 1f);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0; // full sky + block light
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
