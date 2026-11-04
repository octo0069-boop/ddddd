package com.example.speedforce.client;

import java.util.ArrayList;
import java.util.List;

/** Active lightning bolts waiting to be drawn. Main-thread only. */
public final class ClientBolts {
    public static final class Bolt {
        public final double x1, y1, z1, x2, y2, z2;
        public final int seed, style, maxAge;
        public int age;

        Bolt(double x1, double y1, double z1, double x2, double y2, double z2, int seed, int style, int maxAge) {
            this.x1 = x1; this.y1 = y1; this.z1 = z1; this.x2 = x2; this.y2 = y2; this.z2 = z2;
            this.seed = seed; this.style = style; this.maxAge = Math.max(2, maxAge);
        }
    }

    public static final List<Bolt> LIST = new ArrayList<>();

    private ClientBolts() {}

    public static void add(double x1, double y1, double z1, double x2, double y2, double z2, int seed, int style, int life) {
        if (LIST.size() < 300) LIST.add(new Bolt(x1, y1, z1, x2, y2, z2, seed, style, life));
    }

    public static void tick() { LIST.removeIf(b -> ++b.age >= b.maxAge); }

    public static void clear() { LIST.clear(); }
}
