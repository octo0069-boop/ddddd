package com.example.speedforce.server;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Water running, split into two roles that never fight each other:
 *
 *  SERVER ({@link #serverActive}): read-only. Decides whether the player is legitimately running on water
 *  (eligible + fast enough + at the surface). It NEVER calls setPos / setDeltaMovement / setOnGround, so it cannot
 *  produce position corrections or rubber-banding. The result is synced as FLAG_WATER_ACTIVE and used to
 *  cancel fall damage and the vanilla "flying" kick timer.
 *
 *  CLIENT ({@link #predict}): only runs when the server said FLAG_WATER_ENABLED. Instead of teleporting to the
 *  surface it nudges vertical VELOCITY so the player glides to the surface and holds it, keeping horizontal
 *  momentum untouched. Call it from PlayerTickEvent.Post on the local player: that fires at the end of
 *  Player.tick(), BEFORE LocalPlayer.sendPosition(), so the onGround flag we set is the one the server receives.
 *
 * Both sides use the same surface test, so prediction and server evaluation agree. This class has no
 * client-only imports (Player/Level are common classes).
 */
public final class WaterRun {
    /** Horizontal speed (blocks/tick) needed to start / keep running. Keep-speed is lower: hysteresis. */
    public static final double ENTER_SPEED = 0.35, STAY_SPEED = 0.25;
    private static final double REACH_BELOW = 0.35, REACH_ABOVE = 0.15;
    /** Vanilla per-tick gravity and vertical drag for players; used to solve the exact hover velocity. */
    private static final double GRAVITY = 0.08, VDRAG = 0.98;

    private WaterRun() {}

    /** Things that always disable water running. */
    private static boolean blocked(Player p) {
        return p.isShiftKeyDown() || p.isSpectator() || p.isPassenger() || p.isFallFlying()
                || p.getAbilities().flying || p.isSleeping();
    }

    /**
     * Surface height of the water column under (x,z) if feet at {@code y} are within the run band, else NaN.
     * A "surface" is a water cell with no water above it.
     */
    public static double surfaceTop(Level level, double x, double y, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, Mth.floor(y - 0.1), bz);
        // check the cell just under the feet and the one below it (covers falling onto the water at speed)
        for (int i = 0; i < 2; i++) {
            FluidState fs = level.getFluidState(pos);
            if (fs.is(FluidTags.WATER)) {
                pos.move(0, 1, 0);
                boolean waterAbove = level.getFluidState(pos).is(FluidTags.WATER);
                pos.move(0, -1, 0);
                if (!waterAbove) {
                    double top = pos.getY() + fs.getHeight(level, pos);
                    double dy = y - top;
                    return (dy <= REACH_ABOVE && dy >= -REACH_BELOW) ? top : Double.NaN;
                }
            }
            pos.move(0, -1, 0);
        }
        return Double.NaN;
    }

    /**
     * SERVER: is this player running on water right now? Pure query, mutates nothing.
     * @param hs horizontal speed sampled from real position deltas (blocks/tick)
     * @param wasActive previous result, for hysteresis
     */
    public static boolean serverActive(Player p, boolean eligible, double hs, boolean wasActive) {
        if (!eligible || blocked(p)) return false;
        if (hs < (wasActive ? STAY_SPEED : ENTER_SPEED)) return false;
        return !Double.isNaN(surfaceTop(p.level(), p.getX(), p.getY(), p.getZ()));
    }

    /**
     * CLIENT prediction for the local player. Adjusts velocity only; never teleports.
     * @param allowed server-granted permission (ClientState.waterAllowed())
     * @param wasActive previous result, for hysteresis
     * @return whether the player is water running after this call
     */
    public static boolean predict(Player p, boolean allowed, boolean wasActive) {
        if (!allowed || blocked(p)) return false;
        Vec3 mv = p.getDeltaMovement();
        // Jumping off (strong upward velocity) or slowing down ends the run.
        if (mv.horizontalDistance() < (wasActive ? STAY_SPEED : ENTER_SPEED) || mv.y > 0.25) return false;
        double top = surfaceTop(p.level(), p.getX(), p.getY(), p.getZ());
        if (Double.isNaN(top)) return false;

        // Next tick vanilla does y' = (vy - gravity) * drag. Solve for the vy that lands exactly on the surface
        // (+1mm so the feet box is not inside the fluid, which would trigger water drag on horizontal speed).
        double want = Mth.clamp((top + 0.001) - p.getY(), -0.25, 0.30);
        double vy = want / VDRAG + GRAVITY;
        p.setDeltaMovement(mv.x, vy, mv.z);
        p.setOnGround(true);   // sent to the server in this tick's position packet -> no "floating" accumulation
        p.fallDistance = 0;
        return true;
    }
}
