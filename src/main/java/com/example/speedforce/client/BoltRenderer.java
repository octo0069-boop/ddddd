package com.example.speedforce.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Custom lightning: jagged camera-facing ribbons in three layers (wide glow, hot mid, white core)
 * plus random side branches. Re-jittered every 2 ticks so it crackles. Drawn with the additive
 * lightning render type, so it glows against any background.
 */
public final class BoltRenderer {
    private BoltRenderer() {}

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ClientBolts.LIST.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        for (ClientBolts.Bolt b : ClientBolts.LIST) draw(vc, m, b, cam, b.age + pt);
        ps.popPose();
        buf.endBatch(RenderType.lightning());
    }

    private static void draw(VertexConsumer vc, Matrix4f m, ClientBolts.Bolt b, Vec3 cam, float age) {
        float fade = Mth.clamp(1f - age / b.maxAge, 0f, 1f);
        Vec3 a = new Vec3(b.x1, b.y1, b.z1);
        Vec3 z = new Vec3(b.x2, b.y2, b.z2);
        Vec3 d = z.subtract(a);
        double len = d.length();
        if (len < 0.05) return;
        Vec3 dn = d.scale(1.0 / len);
        Vec3 ref = Math.abs(dn.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dn.cross(ref).normalize();
        Vec3 v = dn.cross(u);

        Random r = new Random(b.seed * 31L + (long) (age / 2f));   // re-jitter every 2 ticks = crackle
        float scale = Mth.clamp((float) len / 8f, 0.5f, 1.4f);
        // reference look: white-hot core, pale-yellow halo, warm orange outer glow (style 1 = hotter orange)
        int[] outer = b.style == 1 ? new int[]{255, 90, 30} : new int[]{255, 150, 30};
        int[] halo = b.style == 1 ? new int[]{255, 170, 90} : new int[]{255, 228, 120};

        Vec3[] pts = jagged(a, d, len, u, v, r, Math.min(1.3, 0.10 + len * 0.035), Math.max(4, (int) (len / 0.55)));
        paint(vc, m, pts, cam, scale, outer, halo, fade, 1f);

        // forks: short thin branches off the main bolt, some fork again
        for (int i = 1; i < pts.length - 1; i++) {
            if (r.nextFloat() > 0.45f) continue;
            Vec3 dir = dn.scale(0.3 + r.nextDouble() * 0.7)
                    .add(u.scale((r.nextDouble() - 0.5) * 2)).add(v.scale((r.nextDouble() - 0.5) * 2)).normalize();
            fork(vc, m, pts[i], dir, 0.8 + r.nextDouble() * 2.6, 0, r, cam, scale, outer, halo, fade);
        }
    }

    private static void fork(VertexConsumer vc, Matrix4f m, Vec3 s, Vec3 dir, double length, int depth, Random r,
                             Vec3 cam, float scale, int[] outer, int[] halo, float fade) {
        Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(ref).normalize();
        Vec3 v = dir.cross(u);
        Vec3[] pts = jagged(s, dir.scale(length), length, u, v, r, 0.12 + length * 0.07, Math.max(3, (int) (length / 0.4)));
        float w = depth == 0 ? 0.55f : 0.35f;
        paint(vc, m, pts, cam, scale * w, outer, halo, fade, 0.8f);
        if (depth < 2 && pts.length > 3 && r.nextFloat() < 0.5f) {
            int i = 1 + r.nextInt(pts.length - 2);
            Vec3 nd = dir.add(u.scale((r.nextDouble() - 0.5) * 1.6)).add(v.scale((r.nextDouble() - 0.5) * 1.6)).normalize();
            fork(vc, m, pts[i], nd, length * 0.6, depth + 1, r, cam, scale, outer, halo, fade);
        }
    }

    /** Polyline a -> a+d with fine random jitter that is largest mid-way and has occasional sharp kinks. */
    private static Vec3[] jagged(Vec3 a, Vec3 d, double len, Vec3 u, Vec3 v, Random r, double amp, int n) {
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            Vec3 p = a.add(d.scale(t));
            if (i > 0 && i < n) {
                double s = amp * (0.35 + 0.65 * Math.sin(Math.PI * t)) * (r.nextInt(5) == 0 ? 2.0 : 1.0);
                p = p.add(u.scale((r.nextDouble() - 0.5) * 2 * s)).add(v.scale((r.nextDouble() - 0.5) * 2 * s));
            }
            pts[i] = p;
        }
        return pts;
    }

    private static void paint(VertexConsumer vc, Matrix4f m, Vec3[] pts, Vec3 cam, float scale,
                              int[] outer, int[] halo, float fade, float strength) {
        ribbon(vc, m, pts, cam, 0.20f * scale, outer, 0.16f * fade * strength);          // warm outer glow
        ribbon(vc, m, pts, cam, 0.085f * scale, halo, 0.45f * fade * strength);          // pale yellow halo
        ribbon(vc, m, pts, cam, 0.026f * scale, new int[]{255, 255, 255}, fade);         // white-hot core
    }

    /** Camera-facing quad strip through the points. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3[] pts, Vec3 cam, float width, int[] rgb, float alpha) {
        int al = (int) (Mth.clamp(alpha, 0f, 1f) * 255);
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 p0 = pts[i], p1 = pts[i + 1];
            Vec3 dir = p1.subtract(p0);
            Vec3 mid = p0.add(p1).scale(0.5);
            Vec3 side = dir.cross(cam.subtract(mid));
            if (side.lengthSqr() < 1e-8) continue;
            side = side.normalize().scale(width / 2.0);
            vert(vc, m, p0.subtract(side), rgb, al);
            vert(vc, m, p0.add(side), rgb, al);
            vert(vc, m, p1.add(side), rgb, al);
            vert(vc, m, p1.subtract(side), rgb, al);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vec3 p, int[] rgb, int a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(rgb[0], rgb[1], rgb[2], a);
    }
}
